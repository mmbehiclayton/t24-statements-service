# AI workflow rules

## Working with the user

- Ask one question at a time before non-trivial work.
- Confirm before anything hard to reverse or outward-facing: `git push`, a deploy, sending or sharing the doc.
- Never put rollback or undo commands in replies unless asked. The user runs every block they are given.
- Never ask the user to paste keys or passwords. Never print secrets; credential files are read by scripts, not echoed.
- After a feature or deploy, end with a short click path for using it on the demo page.

## Code

- Read-only against T24. Read by `RECID` (single id, chunked id list or RECID range). No table scans in the statement path; the demo helpers (`/api/accounts`, `/api/customers`) may aggregate.
- Look fields up by name through `FieldMap`; never hard-code a field number.
- Company tables come from `T24Reader.companyTable(company, application)`; never hard-code `FBNK_`.
- Money is `BigDecimal`. Never add different currencies together.
- Keep the style of the surrounding code: small services, records for data, no extra frameworks.
- `./mvnw -q -B package` must pass before a commit.

## Solution design doc (Claude Doc, master)

- Write so that both the bank's executive management and its technical teams can follow the logic and reasoning: precise and comprehensive, with no fine detail left out.
- **Never** name in the doc: the client or bank, any country, IP addresses, host names, the dev or test server, the T24 model bank, or R25. The MVP is "a working build tested on a T24 test system". Screenshots show the generic "Demo Bank" only, with no URL or host.
- Solution design only: no team, effort, price or commercial content.
- Size one environment (UAT). One service (API, workers, scheduler in one application) in every section and every diagram.
- Leave the "___ seconds" benchmark blank empty.
- Keep wording consistent across sections. When one statement changes (for example the number of copies, a later-phase feature, or the balance check), search the whole doc for the same claim and fix every place, the diagrams included.
- Prefer diagrams where a flow or structure is explained (Claude Docs widgets). Every typed label carries a `data-claude-text-id`. Check a screenshot before publishing.
- After doc edits: re-export with the `sync-solution-doc` skill, commit, and ask before pushing.

## Deploy (dev server)

- Use the `deploy-ssedev` skill. Never overwrite the running jar; upload `.new`, stop, rename, start, then check health.
- The helper scripts and credentials live outside the repo in `C:\Users\USER\.sse-deploy` (`ssedev.py`, `statement-tunnel.py`, `ssedev-creds.env`). Never copy them into this repo.
