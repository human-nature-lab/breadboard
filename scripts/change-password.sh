#!/usr/bin/env bash
#
# change-password.sh -- temporarily set a Breadboard user's password, saving the previous
# one so restore.sh can put it back. Breadboard has no real multi-user / impersonation
# support, so the workflow is: change a user's password to one you know, sign in as them,
# then run restore.sh to revert their original password.
#
# We can't recover the original *plaintext* (it's bcrypt-hashed), but we save the original
# password HASH and restore.sh writes it back verbatim -- so the user's real password works
# again afterwards, exactly as before.
#
# Safety:
#   * refuses to run if a restore is already pending (the .bb-password-restore directory
#     exists) -- run restore.sh first so a real original is never lost;
#   * backs up the database (db-backups/<timestamp>/) before changing it;
#   * stops the server if it is running (the H2 file DB is locked while it runs).
#
# Usage:
#   ./change-password.sh --install /path/to/breadboard --email me@example.com [--password TEMP]
#
#   --install   Breadboard install dir (contains lib/, db/). Required.
#   --email     User to change. Required.
#   --password  Temporary password. If omitted, you are prompted (input hidden).
#   --h2-jar / --jbcrypt-jar   Override jar locations (defaults: <install>/lib/*).
#
# After signing in as the user, run restore.sh to revert and clear the saved state.
# Requires `java` on PATH. The server is left stopped -- start it again to sign in.
#
set -euo pipefail

EMAIL=""; PASSWORD=""; PASSWORD_SET=0; INSTALL=""; H2_JAR=""; JBCRYPT_JAR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --install)     INSTALL="${2:-}"; shift 2;;
    --email)       EMAIL="${2:-}"; shift 2;;
    --password)    PASSWORD="${2:-}"; PASSWORD_SET=1; shift 2;;
    --h2-jar)      H2_JAR="${2:-}"; shift 2;;
    --jbcrypt-jar) JBCRYPT_JAR="${2:-}"; shift 2;;
    -h|--help)     awk 'NR>1{ if(/^#/){sub(/^# ?/,"");print} else exit }' "$0"; exit 0;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2;;
  esac
done
[[ -n "$INSTALL" && -d "$INSTALL" ]] || { echo "ERROR: --install must point to a Breadboard install dir" >&2; exit 2; }
[[ -n "$EMAIL" ]] || { echo "ERROR: --email is required" >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "ERROR: 'java' not found on PATH" >&2; exit 1; }

[[ -z "$H2_JAR" ]]      && H2_JAR="$(ls "$INSTALL"/lib/*h2*.jar 2>/dev/null | head -1 || true)"
[[ -z "$JBCRYPT_JAR" ]] && JBCRYPT_JAR="$(ls "$INSTALL"/lib/*jbcrypt*.jar 2>/dev/null | head -1 || true)"
[[ -f "$H2_JAR" ]]      || { echo "ERROR: H2 jar not found under $INSTALL/lib (use --h2-jar)" >&2; exit 1; }
[[ -f "$JBCRYPT_JAR" ]] || { echo "ERROR: jbcrypt jar not found under $INSTALL/lib (use --jbcrypt-jar)" >&2; exit 1; }
DB_BASE="$INSTALL/db/breadboard"
[[ -f "$DB_BASE.h2.db" ]] || { echo "ERROR: database not found at $DB_BASE.h2.db" >&2; exit 1; }
URL="jdbc:h2:$DB_BASE;MODE=MYSQL;IFEXISTS=TRUE"
case "$(uname -s 2>/dev/null || echo)" in MINGW*|MSYS*|CYGWIN*) CPSEP=';';; *) CPSEP=':';; esac

STATE_DIR="$INSTALL/.bb-password-restore"

# --- guard: refuse if a restore is already pending ---------------------------
if [[ -e "$STATE_DIR" ]]; then
  echo "ERROR: a password restore is already pending: $STATE_DIR" >&2
  echo "       Run restore.sh first (it clears that directory), then try again." >&2
  exit 1
fi

# --- stop the server if it's running (unlock + quiesce the H2 file DB) --------
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

# --- back up the database before changing it ---------------------------------
BK_DIR="$INSTALL/db-backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BK_DIR"
cp -p "$INSTALL"/db/breadboard.* "$BK_DIR"/ 2>/dev/null || cp -p "$DB_BASE.h2.db" "$BK_DIR"/
echo "==> Database backed up to $BK_DIR"

sql_quote() { printf '%s' "$1" | sed "s/'/''/g"; }
E_EMAIL="$(sql_quote "$EMAIL")"

# read the user's current password hash (this is what we save for restore)
CUR_HASH="$(java -cp "$H2_JAR" org.h2.tools.Shell -url "$URL" -user "" -password "" \
              -sql "select password from users where email = '$E_EMAIL';" 2>/dev/null \
            | sed -n '2p' | tr -d '[:space:]')"
[[ "$CUR_HASH" == \$2* ]] || {
  echo "ERROR: could not read a bcrypt password for '$EMAIL' (user missing?). No changes made." >&2
  exit 1
}

# new temporary password
if [[ "$PASSWORD_SET" -eq 0 ]]; then read -r -s -p "New temporary password for $EMAIL: " PASSWORD; echo; fi
[[ -n "$PASSWORD" ]] || { echo "ERROR: password must not be empty" >&2; exit 1; }
E_PASS="$(sql_quote "$PASSWORD")"

# save the original hash BEFORE changing anything
( umask 077; mkdir -p "$STATE_DIR" )
STATE_FILE="$STATE_DIR/$(printf '%s' "$EMAIL" | sed 's/[^A-Za-z0-9._@-]/_/g')"
{ printf 'email=%s\n' "$EMAIL"; printf 'password=%s\n' "$CUR_HASH"; } > "$STATE_FILE"
chmod 600 "$STATE_FILE" 2>/dev/null || true

# set the new password
SQL="CREATE ALIAS IF NOT EXISTS BB_HASHPW  FOR \"org.mindrot.jbcrypt.BCrypt.hashpw\";
CREATE ALIAS IF NOT EXISTS BB_GENSALT FOR \"org.mindrot.jbcrypt.BCrypt.gensalt\";
UPDATE users SET password = BB_HASHPW('$E_PASS', BB_GENSALT()) WHERE email = '$E_EMAIL';
DROP ALIAS IF EXISTS BB_HASHPW;
DROP ALIAS IF EXISTS BB_GENSALT;"
SQL_FILE="$(mktemp)"; ERR_FILE="$(mktemp)"; chmod 600 "$SQL_FILE"
trap 'rm -f "$SQL_FILE" "$ERR_FILE"' EXIT
printf '%s\n' "$SQL" > "$SQL_FILE"

if java -cp "$H2_JAR${CPSEP}$JBCRYPT_JAR" org.h2.tools.RunScript \
        -url "$URL" -user "" -password "" -script "$SQL_FILE" >"$ERR_FILE" 2>&1; then
  echo "OK: temporary password set for '$EMAIL'. Original hash saved under $STATE_DIR."
  echo "    Start the server and sign in; then revert with:"
  echo "    ./restore.sh --install '$INSTALL' --email '$EMAIL'"
else
  rm -rf "$STATE_DIR"   # change failed -- remove the state we just created (and the guard dir)
  echo "ERROR: failed to change password (no changes made):" >&2
  sed 's/^/    /' "$ERR_FILE" >&2
  echo "    A database backup remains at $BK_DIR" >&2
  exit 1
fi
