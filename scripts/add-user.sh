#!/usr/bin/env bash
#
# add-user.sh -- add an admin user to a Breadboard install's H2 database directly.
#
# The password is bcrypt-hashed with the SAME jbcrypt library the app uses (driven from
# H2 via CREATE ALIAS over the bundled jar), so the new user can log in normally. This is
# useful because the built-in POST /createFirstUser endpoint only works while the user
# table is empty -- there is otherwise no way to add an admin without the UI.
#
# The user is also attached to every existing experiment (the ownedExperiments relation,
# via the users_experiments table) so it can see and sync them. Use --no-experiments to skip.
#
# Idempotent: safe to run repeatedly. An existing user is left as-is (password and uid
# preserved); the run just ensures it is attached to every CURRENT experiment, so re-running
# also picks up experiments created since. Pass --reset-password to (re)set the password.
#
# Usage:
#   ./add-user.sh --install /path/to/breadboard --email me@example.com [--password PASS]
#                 [--role admin] [--name "Full Name"] [--lang eng] [--no-experiments]
#                 [--reset-password]
#
#   --install   Breadboard install dir (contains lib/, db/). Required.
#   --email     Login email (primary key). Required.
#   --password  Password. If omitted, you are prompted (input hidden).
#   --role      Defaults to "admin".
#   --name      Display name. Optional.
#   --lang      Default-language code. Defaults to "eng".
#   --no-experiments  Do NOT attach the user to existing experiments (default: attach to all).
#   --reset-password  If the user already exists, reset its password to the given one.
#   --h2-jar / --jbcrypt-jar   Override jar locations (defaults: <install>/lib/*).
#
# Requires: the server STOPPED (the H2 file database is locked while it runs) and `java`
# on PATH. Run as a user that can read/write the install's db/ directory.
#
set -euo pipefail

ROLE="admin"; NAME=""; LANG_CODE="eng"; EMAIL=""; PASSWORD=""; PASSWORD_SET=0
INSTALL=""; H2_JAR=""; JBCRYPT_JAR=""; ATTACH_EXPERIMENTS=1; RESET_PASSWORD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install)     INSTALL="${2:-}"; shift 2;;
    --email)       EMAIL="${2:-}"; shift 2;;
    --password)    PASSWORD="${2:-}"; PASSWORD_SET=1; shift 2;;
    --role)        ROLE="${2:-}"; shift 2;;
    --name)        NAME="${2:-}"; shift 2;;
    --lang)        LANG_CODE="${2:-}"; shift 2;;
    --no-experiments) ATTACH_EXPERIMENTS=0; shift;;
    --reset-password) RESET_PASSWORD=1; shift;;
    --h2-jar)      H2_JAR="${2:-}"; shift 2;;
    --jbcrypt-jar) JBCRYPT_JAR="${2:-}"; shift 2;;
    -h|--help)     awk 'NR>1{ if(/^#/){sub(/^# ?/,"");print} else exit }' "$0"; exit 0;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2;;
  esac
done

[[ -n "$INSTALL" && -d "$INSTALL" ]] || { echo "ERROR: --install must point to a Breadboard install dir" >&2; exit 2; }
[[ -n "$EMAIL" ]] || { echo "ERROR: --email is required" >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "ERROR: 'java' not found on PATH" >&2; exit 1; }

# --- locate the jars and the database ----------------------------------------
[[ -z "$H2_JAR" ]]      && H2_JAR="$(ls "$INSTALL"/lib/*h2*.jar 2>/dev/null | head -1 || true)"
[[ -z "$JBCRYPT_JAR" ]] && JBCRYPT_JAR="$(ls "$INSTALL"/lib/*jbcrypt*.jar 2>/dev/null | head -1 || true)"
[[ -f "$H2_JAR" ]]      || { echo "ERROR: H2 jar not found under $INSTALL/lib (use --h2-jar)" >&2; exit 1; }
[[ -f "$JBCRYPT_JAR" ]] || { echo "ERROR: jbcrypt jar not found under $INSTALL/lib (use --jbcrypt-jar)" >&2; exit 1; }
DB_BASE="$INSTALL/db/breadboard"
[[ -f "$DB_BASE.h2.db" ]] || { echo "ERROR: database not found at $DB_BASE.h2.db" >&2; exit 1; }

# --- refuse if the server is running (the H2 file DB is exclusively locked) ---
PID_FILE="$INSTALL/RUNNING_PID"
if [[ -f "$PID_FILE" ]]; then
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "ERROR: Breadboard appears to be running (PID $pid). Stop it first -- the H2 file" >&2
    echo "       database is locked while the server is up." >&2
    exit 1
  fi
fi

# --- get the password (prompt if not supplied, input hidden) -----------------
if [[ "$PASSWORD_SET" -eq 0 ]]; then
  read -r -s -p "Password for $EMAIL: " PASSWORD; echo
fi
[[ -n "$PASSWORD" ]] || { echo "ERROR: password must not be empty" >&2; exit 1; }

# --- build the SQL ------------------------------------------------------------
# Escape single quotes for SQL ('' is the literal-quote escape). H2 treats backslash as a
# literal even in MODE=MYSQL, so quotes are the only thing to escape. Values are spliced
# into a double-quoted string (expanded once), never re-evaluated -- so a '$' or backtick
# in the password is inserted literally, not run by the shell.
sql_quote() { printf '%s' "$1" | sed "s/'/''/g"; }
E_EMAIL="$(sql_quote "$EMAIL")"; E_PASS="$(sql_quote "$PASSWORD")"
E_ROLE="$(sql_quote "$ROLE")";   E_LANG="$(sql_quote "$LANG_CODE")"
if [[ -n "$NAME" ]]; then NAME_SQL="'$(sql_quote "$NAME")'"; else NAME_SQL="NULL"; fi

# Create the user only if absent. This makes re-runs idempotent and preserves an existing
# user's password and uid. FROM (VALUES(1)) gives the SELECT a single row to gate on.
USER_SQL="INSERT INTO users (email, name, password, uid, role, current_script,
                   experiment_instance_id, selected_experiment_id, default_language_id)
SELECT '$E_EMAIL', $NAME_SQL, BB_HASHPW('$E_PASS', BB_GENSALT()),
       CAST(RANDOM_UUID() AS VARCHAR), '$E_ROLE', '', -1, NULL,
       (SELECT MIN(id) FROM languages WHERE code = '$E_LANG')
FROM (VALUES(1)) WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = '$E_EMAIL');"

# Optionally reset an existing user's password (the insert-if-absent above never changes it).
RESET_SQL=""
if [[ "$RESET_PASSWORD" -eq 1 ]]; then
  RESET_SQL="UPDATE users SET password = BB_HASHPW('$E_PASS', BB_GENSALT()) WHERE email = '$E_EMAIL';"
fi

# Attach the user to every experiment it is not already linked to: idempotent, and also
# picks up experiments created since a previous run. Empty-safe when there are none.
ATTACH_SQL=""
if [[ "$ATTACH_EXPERIMENTS" -eq 1 ]]; then
  ATTACH_SQL="INSERT INTO users_experiments (users_email, experiments_id)
SELECT '$E_EMAIL', e.id FROM experiments e
WHERE NOT EXISTS (SELECT 1 FROM users_experiments ue
                  WHERE ue.users_email = '$E_EMAIL' AND ue.experiments_id = e.id);"
fi

SQL="CREATE ALIAS IF NOT EXISTS BB_HASHPW  FOR \"org.mindrot.jbcrypt.BCrypt.hashpw\";
CREATE ALIAS IF NOT EXISTS BB_GENSALT FOR \"org.mindrot.jbcrypt.BCrypt.gensalt\";
$USER_SQL
$RESET_SQL
$ATTACH_SQL
DROP ALIAS IF EXISTS BB_HASHPW;
DROP ALIAS IF EXISTS BB_GENSALT;"

SQL_FILE="$(mktemp)"; ERR_FILE="$(mktemp)"
chmod 600 "$SQL_FILE"
trap 'rm -f "$SQL_FILE" "$ERR_FILE"' EXIT
printf '%s\n' "$SQL" > "$SQL_FILE"

# classpath separator differs on Windows (Git Bash) vs. Linux/macOS
case "$(uname -s 2>/dev/null || echo)" in MINGW*|MSYS*|CYGWIN*) CPSEP=';';; *) CPSEP=':';; esac

# --- run it -------------------------------------------------------------------
echo "Ensuring user '$EMAIL' (role=$ROLE, lang=$LANG_CODE) in $DB_BASE.h2.db ..."
if java -cp "$H2_JAR${CPSEP}$JBCRYPT_JAR" org.h2.tools.RunScript \
        -url "jdbc:h2:$DB_BASE;MODE=MYSQL;IFEXISTS=TRUE" -user "" -password "" \
        -script "$SQL_FILE" >"$ERR_FILE" 2>&1; then
  msg="OK: user '$EMAIL' is present"
  [[ "$ATTACH_EXPERIMENTS" -eq 1 ]] && msg="$msg and attached to all current experiments"
  [[ "$RESET_PASSWORD" -eq 1 ]]     && msg="$msg; password set to the value provided"
  echo "$msg."
else
  echo "ERROR: failed to apply changes:" >&2
  sed 's/^/    /' "$ERR_FILE" >&2
  exit 1
fi
