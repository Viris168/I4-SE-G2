#!/usr/bin/env bash
# Records the Task 3 transcript into security-session.md (appends a "Task 3" section).
# 1. Start the app on a fresh run:   ./mvnw -q spring-boot:run
# 2. In another terminal:            bash docs/record-task3.sh
#
# Credentials default to the temporary Task 1 users (alice/alice, bob/bob, admin/admin).
# Once Task 2's database users are merged, override them, e.g.
#   ALICE=alice:Alice-pass-123 BOB=bob:Correct-Horse-9 ADMIN=admin:Admin-pass-123 bash docs/record-task3.sh
set -u
BASE=localhost:8080/api
DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$DIR/security-session.md"
JSON='Content-Type: application/json'
ALICE="${ALICE:-alice:alice}"
BOB="${BOB:-bob:bob}"
ADMIN="${ADMIN:-admin:admin}"

run() {   # run <title> <user:pass> <curl args...>   (password is masked in the printed command)
  local title="$1" creds="$2"; shift 2
  {
    echo "### $title"; echo; echo '```'
    printf '$ curl -i -u %s:***' "${creds%%:*}"; printf " '%s'" "$@"; echo
    curl -s -i -u "$creds" "$@" | grep -v -i -E '^(date|vary|keep-alive|connection|transfer-encoding|expires|pragma):' | tr -d '\r'
    echo; echo '```'; echo
  } >> "$OUT"
}

[ -f "$OUT" ] || echo "# Lab-05 · security session transcripts" > "$OUT"
# Re-running replaces the previous Task 3 section instead of adding a second one.
python3 - "$OUT" <<'PYEOF'
import sys, re
p = sys.argv[1]; s = open(p).read()
m = re.search(r'\n## Task 3 [^\n]*\n.*?(?=\n## (?!Task 3)|\Z)', s, re.S)
if m: open(p, 'w').write(s[:m.start()].rstrip('\n') + '\n' + s[m.end():])
PYEOF
cat >> "$OUT" <<'MD'

## Task 3 · Roles and method security

alice places an order (customer taken from the Authentication), bob is refused by the
`@PostAuthorize` owner check, admin passes through `hasRole('ADMIN')`. Method-level denials
return the same `application/problem+json` 403 as URL-level denials.

MD

run "1. alice creates an order - 201" "$ALICE" -X POST $BASE/orders -H "$JSON" -d '{"productId":2,"quantity":1}'
run "2. bob reads alice's order - 403 (@PostAuthorize owner check)" "$BOB" $BASE/orders/1
run "3. admin reads alice's order - 200" "$ADMIN" $BASE/orders/1
run "4. alice reads bob's history - 403 (@PreAuthorize)" "$ALICE" "$BASE/orders?customer=bob"
run "5. admin reads alice's history - 200" "$ADMIN" "$BASE/orders?customer=alice"
run "6. alice tries to ship - 403" "$ALICE" -X POST $BASE/orders/1/ship
run "7. admin ships - 204" "$ADMIN" -X POST $BASE/orders/1/ship

{
  echo "### 8. Security tests"; echo; echo '```'
  echo "\$ ./mvnw -q test -Dtest='Order*SecurityTest'"
  # -q hides surefire's summary, so print it from the reports it writes
  (cd "$DIR" && ./mvnw -q test -Dtest='Order*SecurityTest' >/dev/null 2>&1; \
   cat target/surefire-reports/edu.itc.shop.*Order*SecurityTest.txt 2>/dev/null | grep 'Tests run' \
   | awk -F'[ ,]+' '{r+=$3; f+=$5; e+=$7; s+=$9} END {printf "[INFO] Tests run: %d, Failures: %d, Errors: %d, Skipped: %d\n", r, f, e, s}')
  echo '```'; echo
} >> "$OUT"

echo "Appended Task 3 to $OUT"
