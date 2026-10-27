# History

## 27 Oct 2026

**Solution design**
- Wrote the solution design for customer statement generation on T24 R21 ([solution-design.md](solution-design.md)). The Claude Doc is the master copy.
- Reworked the sizing to one environment only (UAT, also used for development and the Phase 1 benchmark): one application on 1 VM with 8 vCPU / 16 GB. Production and DR are sized after the benchmark.
- Kept the doc to solution design only. Team, price and commercial terms are handled separately.
- Checked the doc against the bank's request: individual and corporate customers, faster and more reliable than standard T24 statements, large volumes, existing infrastructure. Added a benchmark pass mark.

**MVP**
- Built a standalone, read-only Spring Boot service (Java 21, OpenPDF) that reads T24 tables on Oracle directly and produces statements as JSON, PDF and CSV.
- T24 details handled: `STMT.PRINTED` and `ACCT.ENT.TODAY` store their records as a CLOB holding a raw dynamic array (field, value and sub-value marks); the other tables are XMLTYPE.
- Added the single-account statement with a balance reconciliation against the live T24 balance.
- Added the batch run: a parallel worker pool writing PDF and CSV files, with timings (per minute, average, 95th percentile, slowest).
- Added customer-level statements: all of a customer's accounts (from `CUSTOMER.ACCOUNT`) in one statement, with totals per currency. The PDF has a summary page, then one page per account.
- Deployed to the dev server on port 8097, separate from every other service.
- Wrote the demo guide ([README](../README.md)) with test accounts, test customers, click paths and reference results.

**Results on the test system**
| Test | Result |
|---|---|
| One account | 30–300 ms to read, 20–100 ms for the PDF |
| One customer, 5 accounts in 3 currencies | about 0.35 s |
| Customers with 100 / 143 accounts | about 0.65 s each, PDF included |
| Batch of 200 statements (PDF + CSV) | about 1 s with 4 workers, 0 failures |

**Doc updates from the MVP**
- Added "Already tested" with the results and four screenshots ([images/](images/)).
- Added "Why not build it inside T24 Browser" and an "inside T24" row in the comparison of approaches.
- The Phase 1 benchmark in UAT, against the bank's slowest real statements, is still the gate before the full build.
