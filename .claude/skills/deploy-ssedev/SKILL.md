---
name: deploy-ssedev
description: Build the statement MVP and deploy it to the dev server (port 8097) safely - upload as .new, stop, rename, start, health check. Use when asked to deploy, redeploy or restart the statement service.
---

# Deploy the statement MVP to the dev server

Confirm with the user before deploying. Helpers live outside the repo in `C:\Users\USER\.sse-deploy`:
- `ssedev.py run "<cmd>"` runs a command on the dev server;
- `ssedev.py put <local> <remote>` uploads a file (a relative remote path is the home dir).

Both read `ssedev-creds.env` and never print it. Don't print or copy that file.

## Steps

1. Build and test (Git Bash, repo root):
   ```bash
   ./mvnw -q -B package
   ```
   Stop if the tests fail.
2. Upload next to the running jar, never over it:
   ```bash
   python C:/Users/USER/.sse-deploy/ssedev.py put target/statement-mvp.jar apps/statement-mvp/statement-mvp.jar.new
   ```
   If `deploy/start.sh` or `deploy/stop.sh` changed, upload them too.
3. Swap and restart:
   ```bash
   python C:/Users/USER/.sse-deploy/ssedev.py run "cd ~/apps/statement-mvp && ./stop.sh && sleep 2 && mv statement-mvp.jar.new statement-mvp.jar && ./start.sh"
   ```
4. Check health (allow about 20 s for start-up):
   ```bash
   python C:/Users/USER/.sse-deploy/ssedev.py run "sleep 20; curl -s localhost:8097/actuator/health; tail -n 20 ~/apps/statement-mvp/logs/app.log"
   ```
   Expect `{"status":"UP"}`. On failure, show the log lines and stop. Don't improvise fixes on the server.
5. Smoke test one statement:
   ```bash
   python C:/Users/USER/.sse-deploy/ssedev.py run "curl -s 'localhost:8097/api/statement?account=20656&from=2025-01-01&to=2025-04-15' | head -c 400"
   ```
6. Record it: add a dated line to `docs/HISTORY.md` and a session note to `context/progress-tracker.md`. Do not add anything to the SSE changelog.
7. Tell the user how to open it: run `python C:\Users\USER\.sse-deploy\statement-tunnel.py`, open http://localhost:8097/, and press Ctrl+F5.
