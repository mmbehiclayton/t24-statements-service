package com.sensys.statement.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sensys.statement.t24.FieldMap;
import com.sensys.statement.t24.T24Reader;
import com.sensys.statement.t24.T24Record;

/**
 * Customer-level statements: CUSTOMER.ACCOUNT (id = customer) lists the customer's accounts;
 * each account's statement is built as usual and the results are totalled per currency.
 */
@Service
public class CustomerStatementService {

    private final StatementService statements;
    private final T24Reader reader;
    private final FieldMap fieldMap;
    private final Map<String, List<Map<String, Object>>> sampleCache = new ConcurrentHashMap<>();

    public CustomerStatementService(StatementService statements, T24Reader reader, FieldMap fieldMap) {
        this.statements = statements;
        this.reader = reader;
        this.fieldMap = fieldMap;
    }

    public CustomerStatement build(String company, String customerId, LocalDate from, LocalDate to) {
        long t0 = System.nanoTime();
        List<String> accounts = accountsOf(company, customerId);
        String name = customerName(company, customerId);

        List<Statement> built = new ArrayList<>();
        List<CustomerStatement.Skipped> skipped = new ArrayList<>();
        for (String account : accounts) {
            try {
                built.add(statements.build(company, account, from, to, false));
            } catch (StatementService.NotFoundException e) {
                skipped.add(new CustomerStatement.Skipped(account, "Not found in company " + company + " (closed or another company)"));
            }
        }
        built.sort((a, b) -> a.account().currency().equals(b.account().currency())
                ? a.account().id().compareTo(b.account().id())
                : a.account().currency().compareTo(b.account().currency()));

        Map<String, List<Statement>> byCcy = built.stream()
                .collect(Collectors.groupingBy(s -> s.account().currency(), TreeMap::new, Collectors.toList()));
        List<CustomerStatement.CurrencyTotal> totals = byCcy.entrySet().stream().map(e -> new CustomerStatement.CurrencyTotal(
                e.getKey(), e.getValue().size(),
                sum(e.getValue(), Statement::opening), sum(e.getValue(), Statement::totalDebits),
                sum(e.getValue(), Statement::totalCredits), sum(e.getValue(), Statement::closing))).toList();

        return new CustomerStatement(company, customerId, name, from, to, built, totals, skipped,
                (System.nanoTime() - t0) / 1_000_000);
    }

    private List<String> accountsOf(String company, String customerId) {
        T24Record rec = reader.read(reader.companyTable(company, "CUSTOMER.ACCOUNT"), customerId)
                .orElseThrow(() -> new StatementService.NotFoundException(
                        "Customer " + customerId + " has no accounts in company " + company));
        List<String> ids = rec.values();
        if (ids.isEmpty()) throw new StatementService.NotFoundException("Customer " + customerId + " has no accounts");
        return ids;
    }

    private String customerName(String company, String customerId) {
        try {
            var f = fieldMap.of("CUSTOMER");
            return reader.read(reader.companyTable(company, "CUSTOMER"), customerId)
                    .map(r -> {
                        String n = f.find("NAME.1").map(r::joined).orElse("");
                        return n.isBlank() ? f.find("SHORT.NAME").map(r::joined).orElse("") : n;
                    }).orElse("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static BigDecimal sum(List<Statement> list, java.util.function.Function<Statement, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Customers with two or more accounts that have statement history, most accounts first.
     * Demo helper: scans CUSTOMER.ACCOUNT, which is small in the test bank; cached per company.
     */
    public List<Map<String, Object>> sampleCustomers(String company, int limit) {
        List<Map<String, Object>> all = sampleCache.computeIfAbsent(company, c -> {
            Set<String> withHistory = statements.sampleAccounts(c, 1000).stream()
                    .map(m -> String.valueOf(m.get("ACCOUNT"))).collect(Collectors.toSet());
            Map<String, Integer> counts = new HashMap<>();
            reader.jdbc().query("select t.RECID, t.XMLRECORD from " + reader.companyTable(c, "CUSTOMER.ACCOUNT") + " t", rs -> {
                List<String> ids = T24Record.parse(rs.getString(1), rs.getString(2)).values();
                long n = ids.stream().filter(withHistory::contains).count();
                if (n >= 2) counts.put(rs.getString(1), (int) n);
            });
            List<Map<String, Object>> rows = new ArrayList<>();
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .forEach(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("CUSTOMER", e.getKey());
                        m.put("ACCOUNTS", e.getValue());
                        rows.add(m);
                    });
            return rows;
        });
        return all.subList(0, Math.min(all.size(), Math.max(1, limit)));
    }
}
