package com.sensys.statement.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sensys.statement.t24.FieldMap;
import com.sensys.statement.t24.FieldMap.Fields;
import com.sensys.statement.t24.T24Reader;
import com.sensys.statement.t24.T24Record;

/**
 * Builds a statement for one account and date range from T24, read-only:
 * <ol>
 *   <li>ACCOUNT gives the header and the live balance (ONLINE.ACTUAL.BAL).</li>
 *   <li>STMT.PRINTED (id ACCOUNT-YYYYMMDD) lists the entry ids of each statement period;
 *       ACCT.ENT.TODAY lists entries not yet printed.</li>
 *   <li>STMT.ENTRY rows are read by id (then the $ARC table for archived ones).</li>
 *   <li>Closing = live balance minus entries booked after the period; opening = closing
 *       minus the period's entries. Lines are in booking-date order.</li>
 * </ol>
 * Field numbers come from STANDARD.SELECTION by name.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);
    static final DateTimeFormatter T24_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final T24Reader reader;
    private final FieldMap fieldMap;
    private final Map<String, String> txnNarrative = new ConcurrentHashMap<>();
    private final Map<String, Boolean> arcExists = new ConcurrentHashMap<>();

    public StatementService(T24Reader reader, FieldMap fieldMap) {
        this.reader = reader;
        this.fieldMap = fieldMap;
    }

    public Statement build(String company, String accountId, LocalDate from, LocalDate to, boolean verify) {
        if (from.isAfter(to)) throw new IllegalArgumentException("'from' is after 'to'");
        long t0 = System.nanoTime();

        Fields acf = fieldMap.of("ACCOUNT");
        Fields sef = fieldMap.of("STMT.ENTRY");
        int printedEntryNo = fieldMap.of("STMT.PRINTED").find("STMT.ENTRY.NO").orElse(1);

        T24Record acct = reader.read(reader.companyTable(company, "ACCOUNT"), accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found in company " + company));

        String acctCcy = acct.first(acf.no("CURRENCY"));
        BigDecimal live = amount(acct.first(acf.no("ONLINE.ACTUAL.BAL")));

        // Statement periods: all of them when verifying, else only those that can hold entries >= from.
        String low = accountId + "-" + (verify ? "00000000" : from.format(T24_DATE));
        List<T24Record> periods = reader.readRange(reader.companyTable(company, "STMT.PRINTED"), low, accountId + "-99999999")
                .stream().filter(r -> r.id().matches(java.util.regex.Pattern.quote(accountId) + "-\\d{8}")).toList();

        Set<String> ids = new LinkedHashSet<>();
        for (T24Record p : periods) {
            p.all(printedEntryNo).stream().filter(s -> !s.isBlank()).forEach(ids::add);
        }
        int unprinted = 0;
        try {
            String todayTable = reader.companyTable(company, "ACCT.ENT.TODAY");
            T24Record today = reader.read(todayTable, accountId).orElse(null);
            if (today != null) {
                int f = fieldMap.of("ACCT.ENT.TODAY").find("STMT.ENTRY.NO").orElse(1);
                for (String id : today.all(f)) {
                    if (!id.isBlank() && ids.add(id)) unprinted++;
                }
            }
        } catch (RuntimeException e) {
            log.debug("ACCT.ENT.TODAY not read for {}: {}", accountId, e.getMessage());
        }

        String entryTable = reader.companyTable(company, "STMT.ENTRY");
        Map<String, T24Record> entries = new HashMap<>(reader.readMany(entryTable, ids));
        int archived = 0;
        Set<String> missing = new HashSet<>(ids);
        missing.removeAll(entries.keySet());
        if (!missing.isEmpty() && arcTableExists(entryTable)) {
            Map<String, T24Record> arc = reader.readMany(entryTable + "$ARC", missing);
            archived = arc.size();
            entries.putAll(arc);
            missing.removeAll(arc.keySet());
        }
        long t1 = System.nanoTime();

        int fBooking = sef.no("BOOKING.DATE"), fValue = sef.no("VALUE.DATE");
        int fLcy = sef.no("AMOUNT.LCY"), fFcy = sef.no("AMOUNT.FCY");
        int fTxn = sef.no("TRANSACTION.CODE"), fNarr = sef.no("NARRATIVE");
        int fTheir = sef.no("THEIR.REFERENCE"), fTrans = sef.no("TRANS.REFERENCE");
        int fRev = sef.no("REVERSAL.MARKER");

        List<Line> inPeriod = new ArrayList<>();
        BigDecimal after = BigDecimal.ZERO, all = BigDecimal.ZERO;
        int afterCount = 0;
        for (T24Record e : entries.values()) {
            LocalDate booking = date(e.first(fBooking));
            String fcy = e.first(fFcy);
            BigDecimal amt = amount(fcy.isBlank() ? e.first(fLcy) : fcy);
            all = all.add(amt);
            if (booking == null) continue;
            if (booking.isAfter(to)) {
                after = after.add(amt);
                afterCount++;
            } else if (!booking.isBefore(from)) {
                inPeriod.add(new Line(e.id(), booking, date(e.first(fValue)), amt,
                        description(company, e.first(fTxn), e.joined(fNarr)),
                        firstNonBlank(e.first(fTheir), e.first(fTrans)),
                        "R".equalsIgnoreCase(e.first(fRev))));
            }
        }
        inPeriod.sort(Comparator.comparing(Line::booking)
                .thenComparing(l -> l.value() == null ? l.booking() : l.value())
                .thenComparing(Line::id));

        BigDecimal closing = live.subtract(after);
        BigDecimal periodSum = inPeriod.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal opening = closing.subtract(periodSum);

        List<Statement.Line> lines = new ArrayList<>(inPeriod.size());
        BigDecimal running = opening, debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        for (Line l : inPeriod) {
            running = running.add(l.amount());
            boolean dr = l.amount().signum() < 0;
            if (dr) debits = debits.add(l.amount().negate());
            else credits = credits.add(l.amount());
            lines.add(new Statement.Line(l.id(), l.booking(), l.value(), l.description(), l.reference(),
                    dr ? l.amount().negate() : null, dr ? null : l.amount(), running, l.reversal()));
        }

        Boolean historyMatches = null;
        BigDecimal historyDiff = null;
        if (verify) {
            historyDiff = live.subtract(all);
            historyMatches = historyDiff.signum() == 0;
        }
        long t2 = System.nanoTime();

        Statement.Account header = new Statement.Account(accountId,
                acct.joined(acf.no("ACCOUNT.TITLE.1")),
                acct.joined(acf.no("SHORT.TITLE")),
                acctCcy,
                acct.first(acf.no("CATEGORY")),
                acct.first(acf.no("CUSTOMER")),
                acf.find("CO.CODE").map(acct::first).orElse(""));
        return new Statement(company, header, from, to, opening, closing, debits, credits, lines,
                new Statement.Reconciliation(live, afterCount, periods.size(), unprinted, missing.size(), archived,
                        historyMatches, historyDiff),
                new Statement.Timing((t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, 0));
    }

    /** Accounts with the most statement periods, for picking demo accounts. */
    public List<Map<String, Object>> sampleAccounts(String company, int limit) {
        String table = reader.companyTable(company, "STMT.PRINTED");
        return reader.jdbc().queryForList(
                "select substr(RECID, 1, length(RECID) - 9) as account, count(*) as periods, "
                        + "min(substr(RECID, -8)) as first_period, max(substr(RECID, -8)) as last_period "
                        + "from " + table + " where regexp_like(RECID, '-[0-9]{8}$') "
                        + "group by substr(RECID, 1, length(RECID) - 9) order by 2 desc, 1 fetch first ? rows only",
                Math.max(1, Math.min(limit, 1000)));
    }

    private boolean arcTableExists(String table) {
        return arcExists.computeIfAbsent(table, t -> reader.exists(t + "$ARC"));
    }

    private String description(String company, String txnCode, String narrative) {
        String txn = "";
        if (!txnCode.isBlank()) {
            txn = txnNarrative.computeIfAbsent(company + "|" + txnCode, k -> {
                try {
                    int f = fieldMap.of("TRANSACTION").no("NARRATIVE");
                    return reader.read(reader.companyTable(company, "TRANSACTION"), txnCode)
                            .map(r -> r.first(f)).filter(s -> !s.isBlank()).orElse(txnCode);
                } catch (RuntimeException e) {
                    return txnCode;
                }
            });
        }
        if (narrative.isBlank()) return txn;
        return txn.isBlank() ? narrative : txn + " - " + narrative;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b == null ? "" : b;
    }

    static BigDecimal amount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    static LocalDate date(String s) {
        if (s == null || s.length() < 8) return null;
        try {
            return LocalDate.parse(s.substring(0, 8), T24_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private record Line(String id, LocalDate booking, LocalDate value, BigDecimal amount,
                        String description, String reference, boolean reversal) {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
