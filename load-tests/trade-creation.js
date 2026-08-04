// =============================================================================
// TICKET-ADV158 — k6 load test: 200 concurrent trade creations for 60s
//
// Prerequisites:
//   brew install k6          (macOS)
//   apt-get install k6       (Ubuntu/Debian)
//
// Run against a live stack:
//   k6 run load-tests/trade-creation.js
//
// Tune BASE_URL and credentials via env vars:
//   k6 run -e BASE_URL=http://localhost:8081/api load-tests/trade-creation.js
// =============================================================================

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// ── Options ──────────────────────────────────────────────────────────────────
export const options = {
    scenarios: {
        trade_creation: {
            executor: "constant-vus",
            vus: 200,
            duration: "60s",
        },
    },
    thresholds: {
        // Less than 1% of requests must fail
        http_req_failed: ["rate<0.01"],
        // 95th-percentile latency must be under 800ms
        http_req_duration: ["p(95)<800"],
        // All auth calls succeed
        auth_errors: ["count==0"],
    },
};

// ── Custom metrics ────────────────────────────────────────────────────────────
const tradeCreated  = new Counter("trades_created_total");
const authErrors    = new Counter("auth_errors");
const tradeLatency  = new Trend("trade_create_duration_ms");
const errorRate     = new Rate("trade_create_error_rate");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8081/api";

// ── Setup: authenticate once and share the token across VUs ──────────────────
export function setup() {
    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ username: "admin", password: "admin" }),
        { headers: { "Content-Type": "application/json" } }
    );

    const ok = check(res, {
        "login 200": (r) => r.status === 200,
        "token present": (r) => JSON.parse(r.body).token !== undefined,
    });

    if (!ok) {
        authErrors.add(1);
        console.error(`Auth failed: ${res.status} ${res.body}`);
        return { token: null };
    }

    return { token: JSON.parse(res.body).token };
}

// ── Default function: executed by every VU for the 60s duration ──────────────
export default function (data) {
    if (!data.token) {
        console.error("No token — skipping iteration");
        sleep(1);
        return;
    }

    const headers = {
        "Content-Type": "application/json",
        Authorization: `Bearer ${data.token}`,
    };

    // Unique trade reference per iteration to avoid duplicate-key errors
    const tradeRef = `LOAD-${__VU}-${__ITER}`;

    const payload = JSON.stringify({
        tradeRef,
        instrument: "AAPL",
        direction: "BUY",
        quantity: 100,
        price: 150.00,
        tradeDate: "2025-01-01",
        settlementDate: "2025-01-03",
        counterparty: "CPTY-A",
        currency: "USD",
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/trades`, payload, { headers });
    tradeLatency.add(Date.now() - start);

    const success = check(res, {
        "trade created 201": (r) => r.status === 201,
        "id returned":       (r) => {
            try { return JSON.parse(r.body).id !== undefined; } catch { return false; }
        },
    });

    if (success) {
        tradeCreated.add(1);
        errorRate.add(0);
    } else {
        errorRate.add(1);
        console.error(`VU ${__VU} iter ${__ITER}: ${res.status} ${res.body}`);
    }

    // Tiny think-time so we don't hammer connections too hard
    sleep(0.1);
}

// ── Teardown: log a summary ───────────────────────────────────────────────────
export function teardown(data) {
    console.log("Load test complete.");
    console.log(`Token was ${data.token ? "valid" : "missing"}.`);
}