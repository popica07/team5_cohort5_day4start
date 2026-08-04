// TICKET-ADV131 — monthly trade volume for the dashboard chart.
import { useEffect, useState } from 'react';
import { api } from '@services/apiService.js';

/**
 * Fetches the 12-month trade counts for `year`.
 *
 * Returns `{ data, isLoading, error }`. On a year change the previous `data`
 * is deliberately kept until the new response lands, so the chart holds its
 * frame (dimmed by the caller) instead of collapsing to a skeleton and
 * bouncing the page layout.
 */
export function useMonthlyTradeStats(year) {
  const [state, setState] = useState({ data: null, isLoading: true, error: null });

  useEffect(() => {
    let cancelled = false;

    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    api.monthlyTradeStats(year)
      .then((res) => {
        if (!cancelled) setState({ data: res, isLoading: false, error: null });
      })
      .catch((err) => {
        // Keep the last good render alongside the error — a failed refetch
        // shouldn't wipe a chart the user is already reading.
        if (!cancelled) setState((prev) => ({ data: prev.data, isLoading: false, error: err }));
      });

    return () => { cancelled = true; };
  }, [year]);

  return state;
}
