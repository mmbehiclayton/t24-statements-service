# Statement Service MVP: Claude Code instructions

## Always read first

- [context/project-overview.md](context/project-overview.md): what this project is, who it is for, and where things stand.
- [context/architecture.md](context/architecture.md): code map, how T24 is read, and how a statement is built.
- [context/ai-workflow-rules.md](context/ai-workflow-rules.md): rules for code, the solution design doc and deploys.
- [context/progress-tracker.md](context/progress-tracker.md): status, open items and session notes. Update it at the end of every session.
- [docs/HISTORY.md](docs/HISTORY.md): dated log of what was built and changed.

## Critical rules

1. **Read-only against T24, always.** The service only runs `SELECT`s by `RECID`. Never add an `INSERT`, `UPDATE`, `DELETE`, DDL or a PL/SQL call against T24, and never add a write path to the T24 datasource (it is marked `hikari.read-only=true`).
2. **Separate from SSE.** This is not part of the SSE migration platform. Don't import SSE code, don't share SSE services, ports or env files, and don't add entries to the SSE changelog. On the dev server it runs on its own port (8097), in its own folder `~/apps/statement-mvp`.
3. **Never run `git push` without the user's explicit confirmation.** This repo works directly on `main` (the user chose that for this repo).
4. **The repo is public on GitHub.** Never commit credentials, IP addresses, the real `statement-mvp.env`, client or bank names, or real customer data. `*.env` is ignored; only `*.env.example` is committed.
5. **The Claude Doc is the master copy of the solution design.** [docs/solution-design.md](docs/solution-design.md) is an export of it. Edit the Claude Doc, then re-export (skill `sync-solution-doc`). Don't edit the markdown copy as the source of truth.
6. **Never copy a jar over the running file.** Upload as `statement-mvp.jar.new`, then stop, rename and start (skill `deploy-ssedev`).

## Build and test

- Git Bash: `./mvnw -q -B package` (runs the tests). PowerShell: `.\mvnw.cmd -q -B package`.
- Output: `target/statement-mvp.jar` (Java 21, Spring Boot).
- Run locally only with a filled `statement-mvp.env` pointing at a **test** T24 database.

## Skills in this repo

- `.claude/skills/deploy-ssedev`: build, upload and restart the MVP on the dev server, then check its health.
- `.claude/skills/sync-solution-doc`: export the Claude Doc to `docs/solution-design.md`, restore the screenshots, commit.
