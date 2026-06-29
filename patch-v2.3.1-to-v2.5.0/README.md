# Breadboard in-place patch: v2.3.1 → v2.5.0

Upgrades an existing **v2.3.1** installation to **v2.5.0** without a full reinstall —
preserving the database and your `conf/` settings. Only the application jar changed
between these versions (dependency jars are identical), so this is a minimal, safe patch.

## What's in this directory
| File | Purpose |
|---|---|
| `apply-patch.sh` | The patch script (run it on the server). |
| `breadboard.breadboard-v2.5.0.jar` | New application jar, extracted from `target/universal/breadboard-v2.5.0.zip`. **Build artifact — not committed to git.** |
| `README.md` | This file. |

If the jar is missing (e.g. fresh git checkout), repopulate it from the dist zip:
```bash
unzip -j breadboard-v2.5.0.zip 'breadboard-v2.5.0/lib/breadboard.breadboard-v2.5.0.jar' -d .
```

## Prerequisites
- **Stop the Breadboard server first.** The script copies the live H2 database for the
  backup; copying it while the app holds it open can corrupt the copy.
- Java 8 (unchanged from v2.3.1 — the runtime requirement is the same).

## Run it
```bash
./apply-patch.sh /path/to/breadboard-v2.3.1
# add --yes to skip the "is the server stopped?" prompt:
# ./apply-patch.sh /path/to/breadboard-v2.3.1 --yes
```

### What the script does automatically
1. **Backs up** (to `<install>/backup-pre-v2.5.0-<timestamp>/`): the database, the old
   `v2.3.1` jar, `bin/breadboard`, and `conf/application-prod.conf`.
2. **Installs** `breadboard.breadboard-v2.5.0.jar` and removes the old `v2.3.1` jar.
3. **Re-points** the `bin/breadboard` launcher classpath at the renamed jar (the Unix
   launcher hardcodes the jar name; the Windows `breadboard.bat` uses a `lib/*` glob and
   needs no change once the old jar is gone).

It refuses to run if the install doesn't look like v2.3.1, if it's already patched, or if
`bin/breadboard` doesn't reference the expected jar — so it won't half-apply.

## MANUAL STEP — enable evolutions (required)
The patch does **not** touch `conf/application-prod.conf` because it holds your secrets,
URLs, and AMT keys. Make these three edits yourself before restarting:

1. Remove or comment out: `evolutionplugin=disabled`
2. Add: `applyEvolutions.default=true`
3. Set: `application.version="v2.5.0"`

Why: v2.5.0 replaces the old hand-rolled migration code with **Play evolutions**. The new
evolution (`30.sql`) is bundled inside the jar; Play applies it on first boot **only if
evolutions are enabled**. Without step 2 the schema won't migrate (and the version footer
won't read the right value without step 3).

## First boot
On the first start after patching, Play applies **evolution 30** to your database:
- adds the `experiments.file_mode` column (if absent), and
- drops the now-unused `breadboard_version` table.

This is the same migration validated against a copy of the production seed database, where
it applies cleanly on top of the existing evolutions and reverts nothing. Your data is
untouched; the pre-patch database copy in the backup dir is the safety net.

## Verify
- The app starts without an evolutions error in the logs.
- The design console footer (bottom-right) reads **"Breadboard v2.5.0"**.
- `breadboard_version` table is gone; `play_evolutions` now lists revision `30` as applied.

## Roll back
Stop the server and restore from the backup dir created by the script:
```bash
B=/path/to/breadboard-v2.3.1/backup-pre-v2.5.0-<timestamp>
cp -p "$B/db/"breadboard.*            /path/to/breadboard-v2.3.1/db/
cp -p "$B/lib/"breadboard.breadboard-v2.3.1.jar  /path/to/breadboard-v2.3.1/lib/
cp -p "$B/bin/breadboard"             /path/to/breadboard-v2.3.1/bin/
cp -p "$B/conf/application-prod.conf" /path/to/breadboard-v2.3.1/conf/
rm -f /path/to/breadboard-v2.3.1/lib/breadboard.breadboard-v2.5.0.jar
```
Restoring the **database** matters most: once v2.5.0 has applied evolution 30, the schema
no longer matches v2.3.1, so a rollback must restore the pre-patch DB copy as well as the jar.
