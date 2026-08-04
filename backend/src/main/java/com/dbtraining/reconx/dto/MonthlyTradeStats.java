package com.dbtraining.reconx.dto;

import java.util.List;

/**
 * ============================================================================
 * TICKET-ADV131 — Monthly trade volume, the payload behind the dashboard's
 * "trades per month" line chart.
 *
 * WHAT:    Twelve points for one calendar year plus the years the picker may
 *          offer.
 * HOW:     {@code months} is always length 12, in calendar order, with 0 for
 *          months that saw no trades — the chart draws a continuous line and
 *          never has to reason about gaps.
 * WHY:     Aggregating in SQL keeps the browser from paging through every
 *          trade in a year just to count them.
 * ============================================================================
 */
public record MonthlyTradeStats(
        int year,
        long total,
        List<Integer> availableYears,
        List<MonthPoint> months
) {

    /** One point on the line: month 1-12, its short name, and the trade count. */
    public record MonthPoint(int month, String label, long count) {}
}

