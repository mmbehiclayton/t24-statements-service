#!/bin/bash
# Starts the statement MVP in the background (port from statement-mvp.env).
cd "$(dirname "$0")"
if [ -f app.pid ] && kill -0 "$(cat app.pid)" 2>/dev/null; then echo "already running pid $(cat app.pid)"; exit 0; fi
set -a; . ./statement-mvp.env; set +a
nohup java -Xms256m -Xmx768m -jar statement-mvp.jar > logs/app.log 2>&1 &
echo $! > app.pid
echo "started pid $(cat app.pid)"
