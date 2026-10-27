#!/bin/bash
cd "$(dirname "$0")"
[ -f app.pid ] && kill "$(cat app.pid)" 2>/dev/null && echo "stopped pid $(cat app.pid)"; rm -f app.pid
