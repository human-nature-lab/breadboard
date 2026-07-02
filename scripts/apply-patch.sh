#!/usr/bin/env bash
#
# In-place patch: Breadboard v2.3.1 -> v2.5.0
# ------------------------------------------------------------------------------
# What it does AUTOMATICALLY:
#   1. Stops the server if it's running (via the RUNNING_PID file) so the DB is quiescent.
#   2. Backs up the database and the files being replaced into a timestamped dir.
#   3. Installs the new application jar (breadboard.breadboard-v2.5.0.jar) and
#      removes the old v2.3.1 jar.
#   4. Re-points the bin/breadboard launcher classpath at the renamed jar.
#   5. Replaces the on-disk groovy/ scripts (loaded at runtime, not bundled in the jar).
#
# The schema migration (evolution 30: adds experiments.file_mode and retires the
# breadboard_version table) is bundled INSIDE the new jar and is applied by Play
# automatically on first boot -- but ONLY if evolutions are enabled in
# conf/application-prod.conf. That file holds your secrets/URLs, so the patch will
# NOT edit it; you must make the small change yourself (see "MANUAL STEP" below and
# README.md). The Windows breadboard.bat launcher loads lib/* by glob, so it needs
# no change once the old jar is removed.
#
# Usage:  ./apply-patch.sh /path/to/breadboard-v2.3.1
#
set -euo pipefail

OLD_VERSION="v2.3.1"
NEW_VERSION="v2.5.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NEW_JAR_SRC="$SCRIPT_DIR/breadboard.breadboard-${NEW_VERSION}.jar"
GROOVY_SRC="$SCRIPT_DIR/groovy"

# --- locate & validate the target install ------------------------------------
INSTALL_DIR="${1:-}"
if [[ -z "$INSTALL_DIR" ]]; then
  echo "Usage: $0 /path/to/breadboard-installation" >&2
  exit 2
fi
INSTALL_DIR="$(cd "$INSTALL_DIR" 2>/dev/null && pwd || true)"
[[ -n "$INSTALL_DIR" && -d "$INSTALL_DIR" ]] || { echo "ERROR: install dir not found: ${1}" >&2; exit 2; }

OLD_JAR="$INSTALL_DIR/lib/breadboard.breadboard-${OLD_VERSION}.jar"
NEW_JAR="$INSTALL_DIR/lib/breadboard.breadboard-${NEW_VERSION}.jar"
BIN="$INSTALL_DIR/bin/breadboard"
DB="$INSTALL_DIR/db/breadboard.h2.db"
PROD_CONF="$INSTALL_DIR/conf/application-prod.conf"
GROOVY_DIR="$INSTALL_DIR/groovy"

# --- preflight checks (fail before changing anything) ------------------------
[[ -f "$NEW_JAR_SRC" ]] || {
  echo "ERROR: patch artifact missing: $NEW_JAR_SRC" >&2
  echo "       Repopulate it from the dist zip, e.g.:" >&2
  echo "       unzip -j breadboard-${NEW_VERSION}.zip 'breadboard-${NEW_VERSION}/lib/breadboard.breadboard-${NEW_VERSION}.jar' -d '$SCRIPT_DIR'" >&2
  exit 1
}
ls "$GROOVY_SRC"/*.groovy >/dev/null 2>&1 || {
  echo "ERROR: groovy scripts missing from patch dir: $GROOVY_SRC" >&2
  echo "       Repopulate them from the v2.5.0 source tree, e.g.:" >&2
  echo "       cp /path/to/breadboard/groovy/*.groovy '$GROOVY_SRC/'" >&2
  exit 1
}
[[ -d "$INSTALL_DIR/lib" && -f "$BIN" ]] || { echo "ERROR: '$INSTALL_DIR' is not a Breadboard install (missing lib/ or bin/breadboard)." >&2; exit 1; }
[[ -f "$NEW_JAR" ]] && { echo "ERROR: $NEW_JAR already present -- looks already patched. Aborting." >&2; exit 1; }
[[ -f "$OLD_JAR" ]] || { echo "ERROR: $OLD_VERSION jar not found at $OLD_JAR -- is this really a $OLD_VERSION install?" >&2; exit 1; }
grep -q "breadboard.breadboard-${OLD_VERSION}.jar" "$BIN" || {
  echo "ERROR: $BIN does not reference the $OLD_VERSION jar; refusing to auto-patch the launcher." >&2
  echo "       Edit it manually (see README.md) and re-run with the jar swap done by hand." >&2
  exit 1
}

echo "Target install : $INSTALL_DIR"
echo "Patch          : $OLD_VERSION -> $NEW_VERSION"
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
cp -p "$OLD_JAR" "$BACKUP_DIR/lib/"
cp -p "$BIN" "$BACKUP_DIR/bin/"
[[ -f "$PROD_CONF" ]] && cp -p "$PROD_CONF" "$BACKUP_DIR/conf/" || true
# groovy backup is a hard gate (no '|| true'): if it exists, a failed copy aborts before
# we replace the scripts. cp -pr creates "$BACKUP_DIR/groovy" (not pre-created above).
if [[ -d "$GROOVY_DIR" ]]; then cp -pr "$GROOVY_DIR" "$BACKUP_DIR/"; fi
echo "    backed up: $OLD_VERSION jar, bin/breadboard, application-prod.conf, groovy/"

# --- apply --------------------------------------------------------------------
echo "==> Installing $NEW_VERSION application jar"
cp -p "$NEW_JAR_SRC" "$NEW_JAR"
rm -f "$OLD_JAR"

echo "==> Re-pointing bin/breadboard classpath ($OLD_VERSION -> $NEW_VERSION)"
sed -i.bak "s/breadboard\.breadboard-${OLD_VERSION}\.jar/breadboard.breadboard-${NEW_VERSION}.jar/g" "$BIN"
rm -f "$BIN.bak"
grep -q "breadboard.breadboard-${NEW_VERSION}.jar" "$BIN" || {
  echo "ERROR: could not update $BIN classpath. Restore from $BACKUP_DIR and patch by hand." >&2
  exit 1
}

# Replace the on-disk groovy scripts wholesale (remove the old set first so scripts that
# were renamed or removed in v2.5.0 don't linger; the old set is in the backup dir).
echo "==> Replacing groovy/ scripts (loaded at runtime, not in the jar)"
mkdir -p "$GROOVY_DIR"
rm -f "$GROOVY_DIR"/*.groovy
cp -p "$GROOVY_SRC"/*.groovy "$GROOVY_DIR/"

cat <<EOF

==============================================================================
 Files patched. Backup: $BACKUP_DIR
==============================================================================

 >>> MANUAL STEP REQUIRED before restarting <<<
 Edit conf/application-prod.conf so the schema migration runs on boot:

     1. Remove / comment out:   evolutionplugin=disabled
     2. Add:                    applyEvolutions.default=true
     3. Set:                    application.version="${NEW_VERSION}"

 (The patch does not edit this file because it holds your secrets and settings.)

 Then start Breadboard. On first boot Play applies evolution 30 to your database
 (adds experiments.file_mode, drops the breadboard_version table). Your data is
 preserved; the pre-patch database is in $BACKUP_DIR/db/ for rollback.

 See README.md for verification and rollback steps.
EOF