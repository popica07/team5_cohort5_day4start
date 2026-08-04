// TICKET-ADV131 — trades-per-month line chart.
//
// Hand-rolled SVG rather than a charting library: one single-series line is
// well under the weight of a runtime dependency, and it keeps the marks on the
// app's own CSS tokens so the dark-mode toggle restyles it for free.
import React, { useId, useMemo, useRef, useState } from 'react';

// A fixed viewBox scaled by CSS (width:100%, height:auto). Every coordinate
// below is in viewBox units, so layout maths never has to observe the DOM.
const VIEW_W = 800;
const VIEW_H = 320;
const PAD = { top: 28, right: 24, bottom: 44, left: 56 };
const PLOT_W = VIEW_W - PAD.left - PAD.right;
const PLOT_H = VIEW_H - PAD.top - PAD.bottom;
const TICK_COUNT = 4;
const MONTH_COUNT = 12;

/**
 * Round the y-axis up to a clean top value so the ticks read 0/5/10/15 rather
 * than 0/3.25/6.5. An all-zero year still gets a real axis (0-4) instead of a
 * degenerate one where every value divides by zero.
 */
function niceScale(maxValue) {
  if (!Number.isFinite(maxValue) || maxValue <= 0) {
    return { top: TICK_COUNT, ticks: [0, 1, 2, 3, 4] };
  }

  const rawStep = maxValue / TICK_COUNT;
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const normalised = rawStep / magnitude;
  const niceStep = (normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10) * magnitude;

  const top = Math.ceil(maxValue / niceStep) * niceStep;
  const ticks = [];
  for (let v = 0; v <= top + niceStep / 2; v += niceStep) {
    ticks.push(Math.round(v));
  }
  return { top, ticks };
}

const xAt = (index) => PAD.left + (index * PLOT_W) / (MONTH_COUNT - 1);
const yAt = (value, top) => PAD.top + PLOT_H - (value / top) * PLOT_H;

/**
 * @param {{ month: number, label: string, count: number }[]} months — always 12,
 *        in calendar order; the API pads empty months to zero.
 */
export default function MonthlyTradesChart({ year, months, total }) {
  const svgRef = useRef(null);
  const titleId = useId();
  const [active, setActive] = useState(null);   // index of the hovered/focused month

  const { top, ticks, points, peakIndex } = useMemo(() => {
    const counts = months.map((m) => m.count);
    const scale = niceScale(Math.max(...counts));
    return {
      ...scale,
      points: months.map((m, i) => ({ ...m, x: xAt(i), y: yAt(m.count, scale.top) })),
      // The one point worth a direct label. First peak wins on a tie, so the
      // label doesn't hop between equal months on refetch.
      peakIndex: counts.indexOf(Math.max(...counts)),
    };
  }, [months]);

  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  const areaPath = `${linePath} L ${xAt(MONTH_COUNT - 1)} ${yAt(0, top)} L ${xAt(0)} ${yAt(0, top)} Z`;

  // Nearest-month lookup: the reader aims at a month, never at a 2px line.
  function indexFromPointer(event) {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return null;

    const viewX = ((event.clientX - rect.left) / rect.width) * VIEW_W;
    const ratio = (viewX - PAD.left) / PLOT_W;
    return Math.min(MONTH_COUNT - 1, Math.max(0, Math.round(ratio * (MONTH_COUNT - 1))));
  }

  function handleKeyDown(event) {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (step === 0) {
      if (event.key === 'Escape') setActive(null);
      return;
    }
    event.preventDefault();   // don't scroll the page while reading the series
    setActive((prev) => {
      const next = prev === null ? 0 : prev + step;
      return Math.min(MONTH_COUNT - 1, Math.max(0, next));
    });
  }

  const activePoint = active === null ? null : points[active];
  const peak = points[peakIndex];
  const showPeakLabel = total > 0 && (active === null || active === peakIndex);

  return (
    <figure className="chart">
      <figcaption className="chart__caption" id={titleId}>
        Trades per month · {year}
        <span className="chart__caption-total">{total.toLocaleString()} total</span>
      </figcaption>

      <div className="chart__plot">
        <svg
          ref={svgRef}
          className="chart__svg"
          viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
          role="img"
          aria-labelledby={titleId}
          tabIndex={0}
          onPointerMove={(e) => setActive(indexFromPointer(e))}
          onPointerLeave={() => setActive(null)}
          onFocus={() => setActive((prev) => (prev === null ? peakIndex : prev))}
          onBlur={() => setActive(null)}
          onKeyDown={handleKeyDown}
        >
          {/* Gridlines + y ticks: hairline, one step off the surface, recessive. */}
          {ticks.map((t) => (
            <g key={t}>
              <line
                className="chart__gridline"
                x1={PAD.left}
                x2={PAD.left + PLOT_W}
                y1={yAt(t, top)}
                y2={yAt(t, top)}
              />
              <text className="chart__tick" x={PAD.left - 10} y={yAt(t, top) + 4} textAnchor="end">
                {t.toLocaleString()}
              </text>
            </g>
          ))}

          {/* Month labels along x. */}
          {points.map((p) => (
            <text
              key={p.month}
              className="chart__tick"
              x={p.x}
              y={PAD.top + PLOT_H + 24}
              textAnchor="middle"
            >
              {p.label}
            </text>
          ))}

          <path className="chart__area" d={areaPath} />
          <path className="chart__line" d={linePath} />

          {/* The crosshair finds the X; it sits under the marks so it never
              draws over the dot the reader is aiming at. */}
          {activePoint && (
            <line
              className="chart__crosshair"
              x1={activePoint.x}
              x2={activePoint.x}
              y1={PAD.top}
              y2={PAD.top + PLOT_H}
            />
          )}

          {points.map((p, i) => (
            <circle
              key={p.month}
              className={i === active ? 'chart__dot chart__dot--active' : 'chart__dot'}
              cx={p.x}
              cy={p.y}
              r={i === active ? 5.5 : 4}
            />
          ))}

          {showPeakLabel && (
            <text
              className="chart__peak-label"
              x={peak.x}
              y={peak.y - 14}
              // Keep the label inside the plot when the peak sits at either end.
              textAnchor={peakIndex === 0 ? 'start' : peakIndex === MONTH_COUNT - 1 ? 'end' : 'middle'}
            >
              {peak.count.toLocaleString()}
            </text>
          )}
        </svg>

        {activePoint && (
          <div
            className="chart__tooltip"
            style={{
              left: `${(activePoint.x / VIEW_W) * 100}%`,
              top: `${(activePoint.y / VIEW_H) * 100}%`,
              // Nudge the box off the plot edges instead of letting it clip.
              transform: `translate(${active <= 1 ? '-12%' : active >= MONTH_COUNT - 2 ? '-88%' : '-50%'}, -125%)`,
            }}
          >
            <strong className="chart__tooltip-value">{activePoint.count.toLocaleString()}</strong>
            <span className="chart__tooltip-label">
              <span className="chart__tooltip-key" aria-hidden="true" />
              {activePoint.label} {year}
            </span>
          </div>
        )}
      </div>

      {/* Same detail keyboard users get on focus that pointer users get on hover. */}
      <div className="visually-hidden" role="status" aria-live="polite">
        {activePoint ? `${activePoint.label} ${year}: ${activePoint.count} trades` : ''}
      </div>

      {/* The tooltip enhances; it never gates. Every value is also here. */}
      <details className="chart__table-toggle">
        <summary>Show data table</summary>
        <table className="chart__table">
          <caption className="visually-hidden">Trades per month in {year}</caption>
          <thead>
            <tr><th scope="col">Month</th><th scope="col">Trades</th></tr>
          </thead>
          <tbody>
            {points.map((p) => (
              <tr key={p.month}>
                <th scope="row">{p.label}</th>
                <td>{p.count.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
