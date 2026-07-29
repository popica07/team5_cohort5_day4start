// TICKET-ADV104 — EventSource subscription to /api/v1/trades/stream
// TICKET-ADV105 — prepend-and-animate with XSS-safe rendering + 50-entry DOM cap
(function () {
  'use strict';

  const FEED_EL   = document.getElementById('trade-feed');
  const STATUS_EL = document.getElementById('sse-status');
  if (!FEED_EL) return; // guard: script may load on pages without the feed

  const STREAM_URL = '/api/v1/trades/stream';
  let sse = null;

  // ── Helpers ────────────────────────────────────────────────────────────────
  function updateBadge(text) {
    if (STATUS_EL) STATUS_EL.textContent = text;
  }

  /** Always escape server-provided strings before inserting into innerHTML. */
  function escapeHtml(s) {
    return String(s)
      .replace(/&/g,  '&amp;')
      .replace(/</g,  '&lt;')
      .replace(/>/g,  '&gt;')
      .replace(/"/g,  '&quot;')
      .replace(/'/g,  '&#39;');
  }

  const fmtQty   = new Intl.NumberFormat('en-US');
  const fmtPrice = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  });

  // ── TICKET-ADV105 — prepend one trade card ─────────────────────────────────
  function prependTradeRow(trade) {
    const statusMap = { MATCHED: 'matched', BREAK: 'break', UNMATCHED: 'break' };
    const mod = statusMap[trade.status] || 'pending';

    const el = document.createElement('article');
    // trade-card--new triggers the combined slide-in + fade-in entrance;
    // strip it after 500 ms once the CSS animation finishes.
    el.className = 'trade-card trade-card--' + mod + ' trade-card--new';
    el.innerHTML =
      '<strong>' + escapeHtml(trade.tradeRef) + '</strong> ' +
      '<span>' + escapeHtml(trade.symbol) + '</span> ' +
      '<span>qty=' + fmtQty.format(trade.qty != null ? trade.qty : (trade.quantity || 0)) + '</span> ' +
      '<span>price=' + fmtPrice.format(trade.price || 0) + '</span> ' +
      '<span>[' + escapeHtml(trade.status) + ']</span>';

    FEED_EL.prepend(el);

    // Remove the --new modifier once the 0.4 s CSS animation completes.
    setTimeout(function () { el.classList.remove('trade-card--new'); }, 500);

    // Cap the feed at 50 entries so the DOM stays bounded after a long session.
    while (FEED_EL.children.length > 50) {
      FEED_EL.lastElementChild.remove();
    }
  }

  // ── TICKET-ADV104 — EventSource connection ─────────────────────────────────
  function connect() {
    sse = new EventSource(STREAM_URL);
console.log("attempting");
    sse.onopen = function () {
      updateBadge('Live');
    };

    sse.onmessage = function (e) {
      try {
        prependTradeRow(JSON.parse(e.data));
      } catch (_) {
        // malformed JSON from server — ignore silently
      }
    };

    // IMPORTANT: do NOT call connect() again inside onerror.
    // EventSource auto-reconnects with exponential backoff. Calling connect()
    // here would flood the dev server with cascading connection attempts.
    sse.onerror = function () {
      updateBadge('Reconnecting…');
    };
  }

  // Clean up when the user navigates away.
  window.addEventListener('beforeunload', function () {
    if (sse) sse.close();
  });

  // ── Demo fallback ──────────────────────────────────────────────────────────
  // When no backend is running, EventSource fails silently (badge stays
  // "Connecting…"). These three hardcoded trades fire via setTimeout so the
  // page still demonstrates the animated feed without a live backend.
  // Remove (or comment out) this block once your backend SSE endpoint is live.
  var DEMO = [
    { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE',  qty: 1000,    price: 125.50, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0001',  symbol: 'EUR/USD', qty: 1000000, price: 1.0852, status: 'PENDING' },
    { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL',    qty: 500,     price: 178.20, status: 'BREAK'   },
  ];
  DEMO.forEach(function (trade, i) {
    setTimeout(function () { prependTradeRow(trade); }, 500 * (i + 1));
  });

  connect();
})();