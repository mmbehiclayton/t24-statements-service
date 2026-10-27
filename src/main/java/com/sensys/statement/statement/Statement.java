package com.sensys.statement.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Statement(
        String company,
        Account account,
        LocalDate from,
        LocalDate to,
        BigDecimal opening,
        BigDecimal closing,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        List<Line> lines,
        Reconciliation reconciliation,
        Timing timing) {

    public record Account(
            String id,
            String title,
            String shortTitle,
            String currency,
            String category,
            String customer,
            String coCode) {
    }

    public record Line(
            String entryId,
            LocalDate bookingDate,
            LocalDate valueDate,
            String description,
            String reference,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance,
            boolean reversal) {
    }

    /**
     * How the figures tie back to T24. The closing balance is anchored to the live
     * ONLINE.ACTUAL.BAL minus entries booked after the period. When verify is on, all
     * entries on the account are also summed and compared with that balance.
     */
    public record Reconciliation(
            BigDecimal liveBalance,
            int entriesAfterPeriod,
            int periodRecordsRead,
            int unprintedEntries,
            int missingEntries,
            int archivedEntries,
            Boolean historyMatches,
            BigDecimal historyDifference) {
    }

    public record Timing(long readMs, long computeMs, long renderMs) {
        public Timing withRender(long ms) {
            return new Timing(readMs, computeMs, ms);
        }

        public long totalMs() {
            return readMs + computeMs + renderMs;
        }
    }

    public Statement withTiming(Timing t) {
        return new Statement(company, account, from, to, opening, closing, totalDebits, totalCredits,
                lines, reconciliation, t);
    }
}
