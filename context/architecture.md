# Architecture (MVP)

## Code map

| Path | Role |
|---|---|
| `StatementMvpApplication`, `StatementProperties` | Spring Boot entry point and `statement.*` settings |
| `t24/T24Reader` | All database access. `read` (one RECID), `readMany` (ids in chunks of 500), `readRange` (RECID range), `exists`, and `companyTable(company, "STMT.ENTRY")` → `FBNK_STMT_ENTRY` |
| `t24/T24Record` | Parses a record: XMLTYPE `XMLRECORD` (`<row><c1>…`), or a CLOB holding a raw dynamic array (field, value and sub-value marks), used by `STMT.PRINTED` and `ACCT.ENT.TODAY`. `first(n)`, `all(n)`, `joined(n)` |
| `t24/FieldMap` | Field numbers by name from `F_STANDARD_SELECTION`, cached. Never hard-code a field number |
| `statement/StatementService` | One account, one date range → `Statement` |
| `statement/CustomerStatementService` | All of a customer's accounts → `CustomerStatement`, with totals per currency |
| `statement/BatchService` | Worker pool, writes a PDF and a CSV per account to `output/batch-<timestamp>`, with timings |
| `render/PdfRenderer`, `render/CsvRenderer` | OpenPDF and CSV output |
| `web/StatementController` | REST API (listed in the README) |
| `resources/static/index.html` | The demo page |

## How one account statement is built

1. `ACCOUNT` by id → header and the live balance `ONLINE.ACTUAL.BAL`.
2. `STMT.PRINTED` RECID range `<acct>-<from>` to `<acct>-99999999` → entry ids per statement period. The open period is a forward-dated record.
3. `ACCT.ENT.TODAY` by account → ids not yet on any period. Ids are deduplicated (a `LinkedHashSet`), so nothing is counted twice.
4. `STMT.ENTRY` by id; ids not found there → `STMT.ENTRY$ARC` (only if that table exists). Ids found in neither are counted as `missing`.
5. Amount = `AMOUNT.FCY`, else `AMOUNT.LCY`. The booking date decides the period; the value date is only shown. `REVERSAL.MARKER` = `R` → marked as a reversal.
6. Closing = live balance − entries booked after `to`. Opening = closing − the period's entries.
7. Lines are sorted by booking date, then value date, then entry id, with a running balance.
8. The description is the `TRANSACTION` narrative for the code (cached), plus the entry's own narrative.

`verify=true` (the page's full-history check) reads every period from `<acct>-00000000` and compares the sum of all entries with the live balance.

## Target design vs MVP

The solution design adds things the MVP does not have yet. Keep the doc's "In the tested build" column honest when any of these is built:

- a second balance anchor, forward from `ACCT.STMT.PRINT`, that holds the statement if it disagrees with the first;
- joint accounts and accounts closed during the period;
- a stored copy for closed periods and an audit trail;
- an Oracle AQ job queue, a scheduler driven by `ACCOUNT.STATEMENT` frequencies, and Spring Batch partitioned by customer ID range;
- Excel output, bank templates with an optional password, and email delivery;
- one cut-off per statement: take the current SCN, run every read (all accounts, all threads) `AS OF SCN`, print the cut-off time as "as at", and mark today's entries provisional. The MVP reads each table in its own query, at slightly different moments, and prints no cut-off. A posting that commits between its balance read and its entry read makes that account's balances off by that amount;
- bulk runs gated on the `DATES` record showing COB has finished.

The design reads the T24 database directly through one read-only user, as the MVP does; a read-only copy (ADG standby, reporting replica) is optional and only a connection change. GoldenGate and the intraday IRIS/OFS call were dropped (rev 227).

## Runtime

- Java 21 on the dev server, `-Xms256m -Xmx768m`, Hikari pool of 6 read-only connections (`T24_POOL_SIZE`).
- Config comes from the environment only (`deploy/statement-mvp.env.example`). `T24_DEFAULT_COMPANY=BNK` is the lead company mnemonic.
- `/actuator/health` is the only actuator endpoint exposed.
