#!/usr/bin/env bash
#
# restore.sh -- revert a password changed by change-password.sh, using the saved original
# hash. Companion to change-password.sh for temporarily impersonating a Breadboard user.
#
# Backs up the database (db-backups/<timestamp>/) before restoring, stops the server if it
# is running, writes the saved original hash(es) back into the users table, and clears the
# .bb-password-restore directory so change-password.sh can be used again.
#
# Usage:
#   ./restore.sh --install /path/to/breadboard --email me@example.com
#   ./restore.sh --install /path/to/breadboard --all
#
#   --install   Breadboard install dir. Required.
#   --email     User to restore. Either this or --all is required.
#   --all       Restore all users with a pending saved password.
#   --h2-jar    Override the H2 jar location (default: <install>/lib/*h2*.jar).
#
# Requires `java` on PATH. The server is left stopped -- start it again when done.
#
set -euo pipefail

EMAIL=""; ALL=0; INSTALL=""; H2_JAR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) INSTALL="${2:-}"; shift 2;;
    --email)   EMAIL="${2:-}"; shift 2;;
    --all)     ALL=1; shift;;
    --h2-jar)  H2_JAR="${2:-}"; shift 2;;
    -h|--help) awk 'NR>1{ if(/^#/){sub(/^# ?/,"");print} else exit }' "$0"; exit 0;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2;;
  esac
done
[[ -n "$INSTALL" && -d "$INSTALL" ]] || { echo "ERROR: --install must point to a Breadboard install dir" >&2; exit 2; }
[[ -n "$EMAIL" || "$ALL" -eq 1 ]] || { echo "ERROR: --email EMAIL or --all is required" >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "ERROR: 'java' not found on PATH" >&2; exit 1; }

[[ -z "$H2_JAR" ]] && H2_JAR="$(ls "$INSTALL"/lib/*h2*.jar 2>/dev/null | head -1 || true)"
[[ -f "$H2_JAR" ]] || { echo "ERROR: H2 jar not found under $INSTALL/lib (use --h2-jar)" >&2; exit 1; }
DB_BASE="$INSTALL/db/breadboard"
[[ -f "$DB_BASE.h2.db" ]] || { echo "ERROR: database not found at $DB_BASE.h2.db" >&2; exit 1; }
URL="jdbc:h2:$DB_BASE;MODE=MYSQL;IFEXISTS=TRUE"

STATE_DIR="$INSTALL/.bb-password-restore"
[[ -d "$STATE_DIR" ]] || { echo "Nothing to restore (no saved passwords under $STATE_DIR)."; exit 0; }

# collect the state file(s) to process (do this before touching the server)
files=()
if [[ "$ALL" -eq 1 ]]; then
  for f in "$STATE_DIR"/*; do [[ -f "$f" ]] && files+=("$f"); done
else
  f="$STATE_DIR/$(printf '%s' "$EMAIL" | sed 's/[^A-Za-z0-9._@-]/_/g')"
  [[ -f "$f" ]] && files+=("$f")
fi
[[ ${#files[@]} -gt 0 ]] || { echo "Nothing to restore for '${EMAIL:-(all)}'."; exit 0; }

# --- stop the server if it's running (unlock the H2 file DB) -----------------
PID_FILE="$INSTALL/RUNNING_PID"
if [[ -f "$PID_FILE" ]]; then
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "==> Breadboard is running (PID $pid); stopping it..."
    kill "$pid" 2>/dev/null || true
    for ((i=0; i<30; i++)); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid" 2>/dev/null || true; sleep 2; fi
    if kill -0 "$pid" 2>/dev/null; then echo "ERROR: could not stop PID $pid; stop it manually and re-run." >&2; exit 1; fi
    echo "    server stopped."
  fi
  rm -f "$PID_FILE"
fi

# --- back up the database before restoring -----------------------------------
BK_DIR="$INSTALL/db-backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BK_DIR"
cp -p "$INSTALL"/db/breadboard.* "$BK_DIR"/ 2>/dev/null || cp -p "$DB_BASE.h2.db" "$BK_DIR"/
echo "==> Database backed up to $BK_DIR"

sql_quote() { printf '%s' "$1" | sed "s/'/''/g"; }
restored=0; failed=0
for f in "${files[@]}"; do
  SAVED_EMAIL="$(sed -n 's/^email=//p' "$f" | head -1)"
  SAVED_HASH="$(sed -n 's/^password=//p' "$f" | head -1)"
  if [[ -z "$SAVED_EMAIL" || "$SAVED_HASH" != \$2* ]]; then
    echo "WARN: skipping malformed state file: $f" >&2; failed=$((failed+1)); continue
  fi
  E_EMAIL="$(sql_quote "$SAVED_EMAIL")"; E_HASH="$(sql_quote "$SAVED_HASH")"
  SQL_FILE="$(mktemp)"
  # bcrypt hashes contain no quotes or backslashes, so the saved value embeds safely.
  printf '%s\n' "UPDATE users SET password = '$E_HASH' WHERE email = '$E_EMAIL';" > "$SQL_FILE"
  if java -cp "$H2_JAR" org.h2.tools.RunScript -url "$URL" -user "" -password "" -script "$SQL_FILE" >/dev/null 2>&1; then
    rm -f "$SQL_FILE" "$f"
    echo "OK: restored original password for '$SAVED_EMAIL'."
    restored=$((restored+1))
  else
    rm -f "$SQL_FILE"
    echo "ERROR: failed to restore '$SAVED_EMAIL' (state file kept: $f)." >&2
    failed=$((failed+1))
  fi
done

# clear the restore directory once empty (lets change-password.sh run again); rmdir only
# succeeds if all state files were restored, so a partial failure safely keeps the rest.
rmdir "$STATE_DIR" 2>/dev/null || true

if [[ "$failed" -gt 0 ]]; then
  echo "Restored $restored user(s); $failed problem(s). $STATE_DIR kept." >&2
  exit 1
fi
echo "Restored $restored user(s). Cleared $STATE_DIR. Start the server again when ready."
