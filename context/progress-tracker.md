# Progress tracker

## Status (28 Oct 2026)

- **MVP:** built and deployed on the dev server (port 8097). It covers one account, a customer's accounts with totals per currency, and batch runs with PDF and CSV output. Reference results are in the README.
- **Solution design:** finalised as a solution proposal, Claude Doc revision 236 (direct read, no database copy; one SCN cut-off per statement). It has been exported to `docs/solution-design.md`.
- **GitHub:** `main` is pushed up to `60c2df6` (in sync with origin).

## Open items

- [ ] The "___ seconds" benchmark pass mark. Owned by the user or the CEO, not filled by Claude.
- [ ] Open points in the doc, for the bank: whether policy allows a read-only user directly on the T24 database (or requires an existing copy), the archive set-up and cut-off date, the container platform or VMs, and a read-only user with SELECT and FLASHBACK on the listed tables per company.
- [ ] MVP gaps against the design (see [architecture.md](architecture.md)): the second balance anchor (`ACCT.STMT.PRINT`), joint and closed accounts, reading a customer's accounts in parallel, Excel output, every read of a statement AS OF one SCN, the printed "as at" cut-off, and today's entries marked provisional.
- [ ] Test-data limits: internal accounts show raw transaction codes, and a couple of their entries are missing.

## Session notes

### 28 Oct 2026
- Doc rev 123–142: "Why not use STMT.ENT.BOOK", the step-by-step build, the fine details the service gets right, the T24 tables it reads (with a tested-build column), a reader guide per audience, "What the bank is asked to approve now", and consistency fixes. Pushed (`e93d19b`, `26090e5`).
- Doc rev 148: the deployment and architecture diagrams now show **one** Statement Service (they had shown the Statement API and the Workers as two). Production "2 identical copies" became "sized after the benchmark; a second copy only for availability". The 10-step list became a flow diagram: the steps, the T24 table each one reads, the stored-copy shortcut, and the balance check leading to "Held for operations". Committed `ed0215a`.
- Doc rev 227: direct read instead of a copy. The service reads the T24 database directly through one read-only user, with an Oracle Resource Manager cap and one read-only transaction per statement; bulk runs start after COB. Active Data Guard became optional (only if policy or the benchmark needs it); GoldenGate and the intraday IRIS/OFS call were dropped. The four diagrams were redrawn to match. Re-exported to the user's Downloads .docx.
- Doc rev 236: transactions still processing at extraction time. Every read of a statement (all accounts, all worker threads) is a flashback query AS OF one SCN, and that cut-off time is printed on the statement. Uncommitted work is never shown, so a posting that finishes 20 s later appears on the next statement. Today's entries are marked provisional and never stored; closed periods are built after COB and are final. Optionally, official statements end at the last COB date. The read-only user now needs SELECT + FLASHBACK. A new risk row was added. Downloads .docx re-exported.
- Added `CLAUDE.md`, `context/` and the `.claude/skills` (deploy, doc sync) so that future sessions start with the project context.

### 27 Oct 2026
- MVP built and deployed; solution design written and sized for one environment. See [docs/HISTORY.md](../docs/HISTORY.md).
