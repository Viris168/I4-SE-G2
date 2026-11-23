#!/usr/bin/env bash
# One-shot: build, start a fresh server, record docs/task2-http.md, stop the server.
# Usage (from shop-rest/):  bash docs/run-task2.sh
set -u
cd "$(dirname "$0")/.."
java -version 2>&1 | head -1
mvn -version 2>&1 | head -1

mvn -q compile || { echo "Build failed"; exit 1; }
mvn -q exec:java > target/server.log 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null; pkill -f "exec:java" 2>/dev/null' EXIT

for i in $(seq 1 60); do
  curl -s -o /dev/null localhost:8080/api/products && break
  sleep 1
done
curl -s -o /dev/null localhost:8080/api/products || { echo "Server did not start:"; cat target/server.log; exit 1; }

bash docs/record-task2.sh
echo "---- status lines ----"
grep '^HTTP/' docs/task2-http.md
