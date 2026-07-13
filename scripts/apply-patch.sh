#!/usr/bin/env bash
#
# In-place patch / upgrade: install this kit's Breadboard build over an existing install.
# ------------------------------------------------------------------------------
# This applier is RE-RUNNABLE and version-agnostic. It overwrites whatever Breadboard
# is currently installed (any version, including a previous copy of THIS version) with
# the jar + groovy scripts bundled in this kit -- so you can use it for continuous
# updates, not just a one-time v2.3.1 -> v2.5.0 hop.
#
# What it does AUTOMATICALLY:
#   1. Stops the server if it's running (via the RUNNING_PID file) so the DB is quiescent.
#   2. Backs up the database and the files being replaced into a timestamped dir.
#   3. Installs this kit's application jar (breadboard.breadboard-${NEW_VERSION}.jar) and
#      removes ANY previously installed breadboard.breadboard-*.jar from lib/ (so old
#      version-stamped jars never accumulate on the classpath).
#   4. Re-points BOTH launchers' classpath (bin/breadboard and bin/breadboard.bat) at the
#      installed jar -- whatever app jar they referenced before is rewritten to this one.
#   5. Replaces the on-disk groovy/ scripts (loaded at runtime, not bundled in the jar).
#
# The schema migration (evolution 30: adds experiments.file_mode and retires the
# breadboard_version table) is bundled INSIDE the new jar and is applied by Play
# automatically on first boot -- but ONLY if evolutions are enabled in
# conf/application-prod.conf. That file holds your secrets/URLs, so the patch will
# NOT edit it; you must make the small change yourself once (see "MANUAL STEP" below and
# README.md). Play tracks which evolutions ran, so re-running this patch does not re-apply
# a migration that's already been applied.
#
# Usage:  ./apply-patch.sh /path/to/breadboard-installation
#
set -euo pipefail

NEW_VERSION="v2.5.0"
NEW_JAR_NAME="breadboard.breadboard-${NEW_VERSION}.jar"
# Matches ANY installed app jar regardless of its version stamp (org.name is breadboard.breadboard).
APP_JAR_RE='breadboard\.breadboard-[0-9A-Za-z._-]+\.jar'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NEW_JAR_SRC="$SCRIPT_DIR/${NEW_JAR_NAME}"
GROOVY_SRC="$SCRIPT_DIR/groovy"

# --- locate & validate the target install ------------------------------------
INSTALL_DIR="${1:-}"
if [[ -z "$INSTALL_DIR" ]]; then
  echo "Usage: $0 /path/to/breadboard-installation" >&2
  exit 2
fi
INSTALL_DIR="$(cd "$INSTALL_DIR" 2>/dev/null && pwd || true)"
[[ -n "$INSTALL_DIR" && -d "$INSTALL_DIR" ]] || { echo "ERROR: install dir not found: ${1}" >&2; exit 2; }

NEW_JAR="$INSTALL_DIR/lib/${NEW_JAR_NAME}"
BIN="$INSTALL_DIR/bin/breadboard"
BIN_BAT="$INSTALL_DIR/bin/breadboard.bat"
DB="$INSTALL_DIR/db/breadboard.h2.db"
PROD_CONF="$INSTALL_DIR/conf/application-prod.conf"
GROOVY_DIR="$INSTALL_DIR/groovy"

# --- preflight checks (fail before changing anything) ------------------------
[[ -f "$NEW_JAR_SRC" ]] || {
  echo "ERROR: patch artifact missing: $NEW_JAR_SRC" >&2
  echo "       Repopulate it from the dist zip, e.g.:" >&2
  echo "       unzip -j breadboard-${NEW_VERSION}.zip 'breadboard-${NEW_VERSION}/lib/${NEW_JAR_NAME}' -d '$SCRIPT_DIR'" >&2
  exit 1
}
ls "$GROOVY_SRC"/*.groovy >/dev/null 2>&1 || {
  echo "ERROR: groovy scripts missing from patch dir: $GROOVY_SRC" >&2
  echo "       Repopulate them from the ${NEW_VERSION} source tree, e.g.:" >&2
  echo "       cp /path/to/breadboard/groovy/*.groovy '$GROOVY_SRC/'" >&2
  exit 1
}
[[ -d "$INSTALL_DIR/lib" ]] || { echo "ERROR: '$INSTALL_DIR' is not a Breadboard install (missing lib/)." >&2; exit 1; }
[[ -f "$BIN" || -f "$BIN_BAT" ]] || { echo "ERROR: '$INSTALL_DIR' has no launcher (missing bin/breadboard and bin/breadboard.bat)." >&2; exit 1; }
# Confirm each launcher that exists references SOME app jar (so we know we can safely re-point
# it). A launcher that already names this exact jar is fine -- that's the re-run / same-version case.
for f in "$BIN" "$BIN_BAT"; do
  [[ -f "$f" ]] || continue
  grep -Eq "$APP_JAR_RE" "$f" || {
    echo "ERROR: $f does not reference a breadboard app jar; refusing to auto-patch the launcher." >&2
    echo "       Edit it manually (see README.md) and re-run." >&2
    exit 1
  }
done

# Report what we're replacing (informational; the applier does not depend on the old version).
OLD_JARS=( "$INSTALL_DIR"/lib/breadboard.breadboard-*.jar )
echo "Target install : $INSTALL_DIR"
if [[ -e "${OLD_JARS[0]}" ]]; then
  echo "Currently in lib/: $(for j in "${OLD_JARS[@]}"; do basename "$j"; done | paste -sd' ' -)"
else
  echo "Currently in lib/: (no breadboard app jar found)"
fi
echo "Installing     : ${NEW_JAR_NAME}"
echo

# --- stop the server if it's running (copying a live H2 DB can corrupt the backup) ---
# Play writes RUNNING_PID in the install dir while up. If it's present and the process is
# alive, shut it down -- SIGTERM first so Play flushes H2 and removes the lock cleanly,
# escalating to SIGKILL only if needed. A stale pid file (process gone) is just removed.
PID_FILE="$INSTALL_DIR/RUNNING_PID"
if [[ -f "$PID_FILE" ]]; then
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "==> Breadboard is running (PID $pid); stopping it before patching..."
    kill "$pid" 2>/dev/null || true
    for ((i=0; i<30; i++)); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then
      echo "    not down after 30s; sending SIGKILL"
      kill -9 "$pid" 2>/dev/null || true
      sleep 2
    fi
    if kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: could not stop PID $pid (permissions?). Stop it manually and re-run." >&2
      exit 1
    fi
    echo "    server stopped."
  else
    echo "==> Stale RUNNING_PID found (process not alive); cleaning it up."
  fi
  rm -f "$PID_FILE"
fi

# --- backup BEFORE touching anything -----------------------------------------
TS="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$INSTALL_DIR/backup-pre-${NEW_VERSION}-${TS}"
echo "==> Backing up to: $BACKUP_DIR"
mkdir -p "$BACKUP_DIR/lib" "$BACKUP_DIR/bin" "$BACKUP_DIR/db" "$BACKUP_DIR/conf"
if [[ -f "$DB" ]]; then
  cp -p "$INSTALL_DIR"/db/breadboard.* "$BACKUP_DIR/db/" 2>/dev/null || cp -p "$DB" "$BACKUP_DIR/db/"
  echo "    backed up: database (db/breadboard.*)"
else
  echo "    note: no existing database at $DB (a fresh one is built from evolutions on boot)"
fi
# Back up every installed app jar (there should be exactly one, but glob to be safe).
if [[ -e "${OLD_JARS[0]}" ]]; then cp -p "${OLD_JARS[@]}" "$BACKUP_DIR/lib/"; fi
[[ -f "$BIN" ]]     && cp -p "$BIN" "$BACKUP_DIR/bin/"     || true
[[ -f "$BIN_BAT" ]] && cp -p "$BIN_BAT" "$BACKUP_DIR/bin/" || true
[[ -f "$PROD_CONF" ]] && cp -p "$PROD_CONF" "$BACKUP_DIR/conf/" || true
# groovy backup is a hard gate (no '|| true'): if it exists, a failed copy aborts before
# we replace the scripts. cp -pr creates "$BACKUP_DIR/groovy" (not pre-created above).
if [[ -d "$GROOVY_DIR" ]]; then cp -pr "$GROOVY_DIR" "$BACKUP_DIR/"; fi
echo "    backed up: app jar(s), launchers, application-prod.conf, groovy/"

# --- apply --------------------------------------------------------------------
echo "==> Installing ${NEW_JAR_NAME} (removing any previously installed breadboard app jar)"
rm -f "$INSTALL_DIR"/lib/breadboard.breadboard-*.jar
cp -p "$NEW_JAR_SRC" "$NEW_JAR"

# Re-point a launcher's classpath: rewrite whatever breadboard app jar it names to the one we
# just installed. Idempotent -- if it already names $NEW_JAR_NAME the sed is a no-op.
repoint_launcher() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  echo "==> Re-pointing $(basename "$f") classpath -> ${NEW_JAR_NAME}"
  sed -E -i.bak "s/${APP_JAR_RE}/${NEW_JAR_NAME}/g" "$f"
  rm -f "$f.bak"
  grep -q "${NEW_JAR_NAME}" "$f" || {
    echo "ERROR: could not update $f classpath. Restore from $BACKUP_DIR and patch by hand." >&2
    exit 1
  }
}
repoint_launcher "$BIN"
repoint_launcher "$BIN_BAT"

# Replace the on-disk groovy scripts wholesale (remove the old set first so scripts that
# were renamed or removed upstream don't linger; the old set is in the backup dir).
echo "==> Replacing groovy/ scripts (loaded at runtime, not in the jar)"
mkdir -p "$GROOVY_DIR"
rm -f "$GROOVY_DIR"/*.groovy
cp -p "$GROOVY_SRC"/*.groovy "$GROOVY_DIR/"

cat <<EOF

==============================================================================
 Files patched. Backup: $BACKUP_DIR
==============================================================================

 >>> MANUAL STEP (first time enabling evolutions only) <<<
 Edit conf/application-prod.conf so the schema migration runs on boot:

     1. Remove / comment out:   evolutionplugin=disabled
     2. Add:                    applyEvolutions.default=true
     3. Set:                    application.version="${NEW_VERSION}"

 (The patch does not edit this file because it holds your secrets and settings.
  Once these are set they stay set -- later re-runs of this patch need no change here.)

 Then start Breadboard. On first boot Play applies any not-yet-applied evolutions to
 your database (e.g. evolution 30: adds experiments.file_mode, drops the
 breadboard_version table). Play tracks what it has already run, so re-running this
 patch will not re-apply a migration. Your data is preserved; the pre-patch database
 is in $BACKUP_DIR/db/ for rollback.

 See README.md for verification and rollback steps.
EOF
