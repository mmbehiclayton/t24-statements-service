package com.sensys.statement.render;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.sensys.statement.statement.CustomerStatement;
import com.sensys.statement.statement.Statement;

@Component
public class CsvRenderer {

    public byte[] render(Statement s) {
        StringBuilder b = new StringBuilder();
        account(b, s);
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Summary rows for the customer, then each account's block. */
    public byte[] render(CustomerStatement cs) {
        StringBuilder b = new StringBuilder();
        b.append("Customer,").append(cell(cs.customer())).append('\n');
        b.append("Customer name,").append(cell(cs.name())).append('\n');
        b.append("Period,").append(cs.from()).append(',').append(cs.to()).append('\n');
        b.append('\n');
        b.append("Account,Currency,Opening balance,Total debits,Total credits,Closing balance\n");
        for (Statement s : cs.statements()) {
            b.append(cell(s.account().id())).append(',').append(cell(s.account().currency())).append(',')
             .append(num(s.opening())).append(',').append(num(s.totalDebits())).append(',')
             .append(num(s.totalCredits())).append(',').append(num(s.closing())).append('\n');
        }
        b.append('\n');
        b.append("Currency total,Accounts,Opening balance,Total debits,Total credits,Closing balance\n");
        for (CustomerStatement.CurrencyTotal c : cs.totals()) {
            b.append(cell(c.currency())).append(',').append(c.accounts()).append(',')
             .append(num(c.opening())).append(',').append(num(c.totalDebits())).append(',')
             .append(num(c.totalCredits())).append(',').append(num(c.closing())).append('\n');
        }
        for (Statement s : cs.statements()) {
            b.append("\n\n");
            account(b, s);
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void account(StringBuilder b, Statement s) {
        b.append("Account,").append(cell(s.account().id())).append('\n');
        b.append("Currency,").append(cell(s.account().currency())).append('\n');
        b.append("Period,").append(s.from()).append(',').append(s.to()).append('\n');
        b.append("Opening balance,").append(num(s.opening())).append('\n');
        b.append('\n');
        b.append("Booking date,Value date,Description,Reference,Debit,Credit,Balance,Entry id\n");
        for (Statement.Line l : s.lines()) {
            b.append(d(l.bookingDate())).append(',')
             .append(d(l.valueDate())).append(',')
             .append(cell(l.description())).append(',')
             .append(cell(l.reference())).append(',')
             .append(num(l.debit())).append(',')
             .append(num(l.credit())).append(',')
             .append(num(l.balance())).append(',')
             .append(cell(l.entryId())).append('\n');
        }
        b.append('\n');
        b.append("Total debits,").append(num(s.totalDebits())).append('\n');
        b.append("Total credits,").append(num(s.totalCredits())).append('\n');
        b.append("Closing balance,").append(num(s.closing())).append('\n');
    }

    private static String d(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String num(BigDecimal n) {
        return n == null ? "" : n.toPlainString();
    }

    private static String cell(String v) {
        if (v == null) return "";
        // Neutralise spreadsheet formulas and quote when needed.
        String s = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
