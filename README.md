# Statement Service MVP: demo guide

A standalone, read-only service that produces customer statements straight from T24 (Oracle). It runs on ssedev, port 8097, separate from SSE, and never writes to T24 or touches the migration pipeline.

The data on ssedev is the T24 **R25 model bank**: test data only, with no real customers. Statement history starts on 17 Mar 2025.

## Opening the page

**Easiest: the tunnel script (no password to type)**

1. In PowerShell, run `python C:\Users\USER\.sse-deploy\statement-tunnel.py`.
2. Wait for `tunnel up: http://localhost:8097/`, and leave the window open.
3. Open http://localhost:8097/
4. To stop it, press Ctrl+C or close the window.

If port 8097 is already taken on your PC, add another port: `python C:\Users\USER\.sse-deploy\statement-tunnel.py 8098`, then open http://localhost:8098/

The script uses `ssedev.py` and `ssedev-creds.env` in the same folder (`C:\Users\USER\.sse-deploy`). The password is read from that file and never printed. It is not in this repo.

**Alternative: plain SSH**

1. Run `ssh -N -L 8097:localhost:8097 ssedev@dev-ssh.fikia.sensys.co.ke`. The first time, add `-o StrictHostKeyChecking=accept-new`. The server's key fingerprint is `SHA256:eVBYM0HUzv7fujcvL1gZt6LjjoMghBk+ia3ubMotSVo`.
2. Paste the ssedev password (the `APP_PW` value in `ssedev-creds.env`) with a right-click. Nothing appears while you paste.
3. Leave the window open, then open http://localhost:8097/

`tsh ssh` does not work here: Teleport accounts only see the `t24-70` node, not ssedev.

**Either way**
- After a redeploy, press Ctrl+F5 so the browser loads the new page.

## Test accounts

Use **From 2025-01-01** and **To 2025-04-15** or later. The page fills these in when you click a chip.

| Account | Currency | Customer | Transactions | Use it to show |
|---|---|---|---|---|
| 20656 | USD | 100472 | 14 | The account with the most history (13 statement periods) |
| 23779 | USD | 100472 | 13 | A second account of the same customer |
| 135771 | USD | 100432 | 10 | A typical customer account |
| 132338 | GBP | 112255 | 2 | A non-USD account |
| 146137 | USD | 112255 | 3 | A small account; quick to read through |
| EURUSD1401600010001 | — | internal | — | An internal (position) account; shows raw transaction codes |

Other customer accounts with plenty of history: 15393, 22268, 23752, 23892, 135976, 15415, 15447, 20753, 23825.

## Test customers (all accounts)

| Customer | Accounts in the statement | Currencies | Use it to show |
|---|---|---|---|
| 112255 | 5 | EUR, GBP, USD | First demo: small enough to read, with totals per currency |
| 100472 | 9 | USD | Owns the accounts with the most history (20656, 23779); 37 transactions |
| 190209 | 100 | USD | A large customer; one PDF of about 157 KB in about 0.65 s |
| 100283 | 143 | EUR, GBP, JPY, USD | The largest customer, with four currencies |

The customer chips show a smaller number (e.g. 190209 · 24 accounts) because they count only accounts with statement history. The full customer statement also includes the inactive accounts, with zero movements.

Customer 999999999 doesn't exist. Use it to show the "has no accounts" message.

## Click path

**One account**
1. In **Statement on demand**, leave **By account** selected.
2. Click an account chip, or type an account number and the dates, then click **View**.
3. The page shows the balances, the timing, the reconciliation line and the transactions.
4. **PDF** or **CSV** downloads the same statement.

**All of a customer's accounts**
1. Click **By customer (all accounts)**.
2. Click a customer chip, or type a customer number, then click **View**.
3. The page shows one row per account and the totals per currency. Each currency is totalled separately, never added together.
4. **View lines** on an account row shows that account's transactions.
5. **PDF** gives one file: a summary page, then each account on its own page. **CSV** follows the same layout.

**Batch run**
1. In **Batch run**, set the number of accounts (e.g. 50 or 200) and click **Run batch**.
2. The page shows the totals: statements per minute, average, 95th percentile and slowest.
3. The generated files are listed underneath. A PDF opens in a new tab; a CSV downloads.

## Reference results (ssedev, 27 Oct 2026)

| Run | Result |
|---|---|
| Single statement | 30–300 ms to read, 20–100 ms for the PDF |
| Customer 112255 (5 accounts) | about 0.35 s |
| Customers 190209 / 100283 (100 / 143 accounts) | about 0.65 s each, PDF included |
| Batch of 50 | 0.4–0.8 s, 0 failures |
| Batch of 200 | 0.9 s with 4 workers, 0 failures |

## Known limits of the test data

- The history starts on 17 Mar 2025, so the full-history check reports a difference. The closing balance still matches the live balance.
- Internal accounts show raw transaction codes, and a couple of their entries are missing.
- A customer's accounts are read one after another. For corporates with hundreds of accounts, reading them in parallel is the next step.

## API

| Endpoint | Purpose |
|---|---|
| `GET /api/accounts?limit=` | Accounts with the most statement history |
| `GET /api/statement?account=&from=&to=&format=json\|pdf\|csv` | One account |
| `GET /api/customers?limit=` | Customers with several accounts |
| `GET /api/customer-statement?customer=&from=&to=&format=json\|pdf\|csv` | All of a customer's accounts |
| `POST /api/batch` `{count, from, to}` | Batch run |
| `GET /api/batch/{folder}` and `/api/batch/{folder}/{file}` | Files from a batch run |

Dates are `YYYY-MM-DD`. `company` is optional and defaults to the bank's main company.

## Build and redeploy

1. `./mvnw -q -B package` (Git Bash). This runs the tests.
2. Upload `target/statement-mvp.jar` to `~/apps/statement-mvp/statement-mvp.jar.new` on ssedev (first time: also copy `deploy/start.sh`, `deploy/stop.sh` and a filled-in `statement-mvp.env`, mode 600).
3. On ssedev, in `~/apps/statement-mvp`: `./stop.sh`, then `mv statement-mvp.jar.new statement-mvp.jar`, then `./start.sh`.
4. Check `curl localhost:8097/actuator/health`.

Never copy the jar over the running file; always use the `.new` file and the rename.

## Docs

- [docs/solution-design.md](docs/solution-design.md): the solution design (export of the Claude Doc).
- [docs/HISTORY.md](docs/HISTORY.md): what was built and when.
- [deploy/](deploy/): `start.sh`, `stop.sh` and an example env file. The real `statement-mvp.env` holds the T24 credentials and is never committed.
