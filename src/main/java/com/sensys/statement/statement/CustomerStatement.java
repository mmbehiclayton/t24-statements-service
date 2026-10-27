package com.sensys.statement.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** All of one customer's account statements for a period, with totals per currency. */
public record CustomerStatement(String company, String customer, String name, LocalDate from, LocalDate to,
                                List<Statement> statements, List<CurrencyTotal> totals,
                                List<Skipped> skipped, long readMs) {

    public record CurrencyTotal(String currency, int accounts, BigDecimal opening, BigDecimal totalDebits,
                                BigDecimal totalCredits, BigDecimal closing) {
    }

    /** An account listed for the customer that could not be included (closed, other company...). */
    public record Skipped(String account, String reason) {
    }

    public int lineCount() {
        return statements.stream().mapToInt(s -> s.lines().size()).sum();
    }
}
