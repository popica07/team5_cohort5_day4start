// TICKET-ADV120 — useMemo for portfolio-value calc.
// TICKET-ADV116 — useTradeStream live feed.
// TICKET-ADV131 — trades-per-month chart with a year picker.
import React from 'react';
import { withAuth } from '@components/withAuth.jsx';
import MonthlyTradesChart from '@components/MonthlyTradesChart.jsx';
import { useTradeStream } from '@hooks/useTradeStream.js';
import { useMonthlyTradeStats } from '@hooks/useMonthlyTradeStats.js';
import { useMemo, useState } from 'react'

function StatCard({ label, value }) {
  return (
    <article className="stat-card">
      <h3>{label}</h3>
      <p>{value}</p>
    </article>
  );
}

function Dashboard() {
  const { trades, isConnected } = useTradeStream();

const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + (t.quantity * t.price || 0), 0),
    [trades]
  );

  const matched = trades.filter((t) => t.status === 'MATCHED').length;
  const breaks  = trades.filter((t) => ['UNMATCHED','DISPUTED'].includes(t.status)).length;

  const [year, setYear] = useState(() => new Date().getFullYear());
  const { data: stats, isLoading, error } = useMonthlyTradeStats(year);

  // The server decides which years it can offer; until the first response
  // lands, the selected year is the only option we can honestly show.
  const years = stats?.availableYears?.length ? stats.availableYears : [year];

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        <StatCard label="Portfolio value (USD)" value={portfolioValue.toLocaleString()} />
        <StatCard label="Trades streamed" value={trades.length} />
        <StatCard label="Matched" value={matched} />
        <StatCard label="Open breaks" value={breaks} />
      </div>
      <div role="status" aria-live="polite">
        SSE: {isConnected ? 'connected' : 'disconnected'}
      </div>

      {/* Filters sit in one row above the content they scope. */}
      <div className="chart-controls">
        <label htmlFor="chart-year">Year</label>
        <select
          id="chart-year"
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
        >
          {years.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
      </div>

      {error && (
        <p className="form-error" role="alert">
          Could not load monthly trade volume
          {stats ? ' — showing the last figures that loaded.' : '.'}
        </p>
      )}

      {stats && (
        <div className={isLoading ? 'chart--loading' : undefined}>
          <MonthlyTradesChart year={stats.year} months={stats.months} total={stats.total} />
        </div>
      )}
    </section>
  );
}

export default withAuth(Dashboard);
