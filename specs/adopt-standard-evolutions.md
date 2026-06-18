# Refactor: Adopt standard Play evolutions, retire the custom migration handler

**Branch:** `feat/standard-migrations`
**Goal:** Make Play's built-in **evolutions** the single, live database-migration mechanism, and remove the custom migration logic in `app/Global.java`. Preserve the ability to bring a legacy (pre‑v2.4) database forward as an explicit, opt‑in one‑time step.

---

## 1. Current state (what we found)

The project currently has **two** migration systems that overlap almost completely, and the standard one is switched off:

| | Play Evolutions (`conf/evolutions/default/1–29.sql`) | Custom handler (`Global.onStart`) |
|---|---|---|
| **Status today** | **Disabled** — `evolutionplugin=disabled` in both `application-dev.conf` and `application-prod.conf`, and **physically deleted** from the prod build (`create_prod_dist.sh`: `rm -r .../conf/evolutions`) | **The only thing actually running** in dev/prod |
| **What it does** | Creates the full schema as SQL (tables, languages/translations, `bonus_amount`, `default_language_id`, `uid`, `breadboard_version`, `messages`) | Same DDL **plus** `file_mode`, **plus** Java-only data migration: seed languages, set each user's default language, generate experiment `uid`s, back up v2.2 `client.html`/`client-graph.js` to disk, create `Content` translations |

**Fresh installs get their schema from neither system** — `create_prod_dist.sh` ships a pre-built H2 file (`db/breadboard.h2.db.default.<ver>` → installed as `breadboard.h2.db`) that already has the current schema. So:

- For new + already-migrated v2.4 installs, `Global.onStart` runs two harmless `SELECT`s and does nothing.
- The custom handler's **only real job** is upgrading a *legacy pre‑v2.4* DB that someone copies over a fresh install.
- `breadboard_version` is read by **nothing** except `Global.java` itself.

**The test harness is already the target end-state.** `test/BaseTest.java` boots a `FakeApplication` with `applyEvolutions.default=true`, sets `application.global=play.GlobalSettings` (custom `Global` disabled), and preserves the `play_evolutions` table across truncation. It is proof the app runs correctly on standard evolutions.

### Authoritative list of evolution gaps

`BaseTest.java:64‑68` patches exactly the columns the evolution files miss:

```java
alter table experiments add column if not exists file_mode bit default 0;
alter table experiment_instances add column if not exists version integer default 0;
```

These two columns exist in the shipped DB (Ebean DDL generation created them) but in **no** evolution. They must be added as evolutions before evolutions can be the source of truth.

---

## 2. The two hard problems

Adopting evolutions is not just flipping a config flag. Two things must be handled or production breaks:

1. **Baselining existing databases.** Every live DB (shipped seeded DB + field DBs) already has the full schema but **no `play_evolutions` table**. The moment the plugin is enabled, Play sees "0 evolutions applied" and tries to apply **all** of 1..N against a DB that already has every table → `table already exists` → startup halts. We must mark the current evolutions as already-applied, with **Play-correct hashes**.

2. **Legacy upgrade can't be pure SQL.** A pre‑v2.4 DB needs the Java/filesystem migration (seed languages, set defaults, generate `uid`s, back up v2.2 client files to disk, build translations). Evolutions are SQL-only, so this logic must survive as an opt-in task — it cannot simply be deleted.

---

## 3. Plan (phased)

### Phase 0 — Verify schema convergence (do first)
- Build a fresh DB by running evolutions 1..29 on an empty H2 (`MODE=MYSQL`), then diff its `information_schema` against the shipped `breadboard.h2.db.default.<ver>`.
- Known deltas to expect: the two gap columns above. **Investigate any others** — the evolution lineage must describe the same schema the shipped DB actually has, or future evolutions will mismatch.
- Also sanity-check internal evolution consistency: the `default_language` → `default_language_id` churn (evo 25 → 26), `messages` being created by both evo 29 and the handler, and the `experiment_languages` vs `experiments_languages` typo in `24.sql`'s `!Downs`.

### Phase 1 — Make evolutions complete
- Add `conf/evolutions/default/30.sql` for the two gap columns, written defensively so it's a no-op on DBs that already have them:
  ```sql
  # --- !Ups
  alter table experiments add column if not exists file_mode bit default 0;
  alter table experiment_instances add column if not exists version integer default 0;

  # --- !Downs
  alter table experiments drop column if exists file_mode;
  alter table experiment_instances drop column if exists version;
  ```
  (H2 in `MODE=MYSQL` supports `add column if not exists` — the handler and `BaseTest` already rely on it.)
- Add `conf/evolutions/default/31.sql` to drop the now-orphaned `breadboard_version` table (its only reader was the handler we're deleting; its schema-version-marker role is taken over by `play_evolutions`). **Do not** edit `28.sql` to stop creating it — editing an applied evolution changes its hash and triggers a revert+re-run. Forward-drop instead:
  ```sql
  # --- !Ups
  drop table if exists breadboard_version;

  # --- !Downs
  create table breadboard_version ( version varchar(255) );
  ```
  On a fresh empty DB, `28.sql` creates it and `31.sql` immediately drops it (one wasted op, harmless). On baselined field DBs, `31.sql` runs post-baseline and removes the leftover. The legacy importer (Phase 4) may still read the table *transiently* during its one-time run.

### Phase 2 — Turn the evolutions plugin on
- Remove `evolutionplugin=disabled` from `application-dev.conf` and `application-prod.conf`.
- Add to `application-prod.conf` (prod boots non-interactively via `-Dconfig.file=conf/application-prod.conf`):
  ```
  applyEvolutions.default=true
  applyDownEvolutions.default=false   # never auto-drop in prod
  ```
  Add `applyEvolutions.default=true` to dev too (otherwise dev shows the "Apply this script now!" web page).
- In `create_prod_dist.sh`, **remove** the `rm -r install/breadboard-${breadboard_version}/conf/evolutions` line so the scripts ship with the distribution.

### Phase 3 — Baseline existing databases (the footgun)
- **Generate a correct baseline empirically** (don't hand-compute hashes): on a fresh empty H2, enable evolutions, let Play apply 1..30 cleanly, then dump the resulting `play_evolutions` rows. Those rows carry Play's own hashes — guaranteed correct.
- **New installs — ship an empty DB (decided).** Stop shipping a pre-seeded `breadboard.h2.db.default.<ver>`; ship an empty DB file (or none) and let evolutions build the full schema + `play_evolutions` on first boot. Update `create_prod_dist.sh` accordingly (drop the `cp db/breadboard.h2.db.default.<ver> ...` step, or point it at an empty DB). **Verify the first-run admin bootstrap still works** — with no seeded admin row, first boot must fall through to the existing `CreateFirstUser` flow (`app/models/CreateFirstUser.java`). Confirm it triggers when the `users` table is empty; this replaces the old `initial-data.yml`/`InitialData` seeding.
- **Existing field DBs** (already at v2.4 schema, no `play_evolutions`): deliver a one-time **insert-only** baseline (the captured `play_evolutions` rows, no DDL) so Play treats 1..N as applied without recreating tables. Note: `31.sql` will then drop their leftover `breadboard_version`.
- **Verify:** boot once against an empty DB (full build, then "no evolutions to apply" on second boot) and against a baselined field DB (no DDL re-run).

### Phase 4 — Extract the legacy upgrade into an opt-in one-time task
- Move the salvageable logic out of `Global.onStart` into a dedicated class (e.g. `app/jobs/LegacyDatabaseUpgrade.java`): the pre‑v2.3 schema block, the data migration loops, the filesystem backup of v2.2 client files, and `version2Point4Upgrade()`.
- Trigger it **explicitly, never on every startup**, via a `-Dbreadboard.legacyUpgrade=true` system-property flag (decided) — matches the existing launcher pattern in `breadboard-*.sh/.bat`, checked once at boot. The check can live in a minimal `Global.onStart` (see Phase 5); when the flag is absent, startup does nothing migration-related.
- After it brings a legacy DB to the current schema + data, it must **stamp the `play_evolutions` baseline** (same rows as Phase 3) so the upgraded DB joins the evolution lineage and subsequent boots are clean.
- Document the procedure: when to run it, that it is one-time, and that it must run before normal startup on a legacy DB.

### Phase 5 — Remove the custom handler from the hot path
- Delete the migration logic from `Global.onStart`: the `breadboard_version` table check, both branches, and `version2Point4Upgrade()`.
- Remove the `onStart` override entirely (the `GlobalSettings` default is a no-op) **unless** the Phase‑4 flag check lives there — in which case reduce it to just that check.
- Remove the unused `InitialData` inner class (no callers in the codebase).
- **Keep** `onStop` (destroys the child `process`) and `onRequest`.
- Drop now-unused imports (`Ebean`, `SqlRow`, `FileUtils`, `LanguageController`, `Yaml`, SQL/`DB` types) — keep what `onStop`/`onRequest` still need.

### Phase 6 — Test & verify
| Scenario | Expected |
|---|---|
| `sbt test` (in-memory H2, already on evolutions) | Green. `BaseTest` lines 64‑68 can be deleted once `30.sql` covers them — confirm tests still pass. |
| Fresh install from shipped **empty** DB | First boot applies 1..31 (schema + `play_evolutions`); `file_mode` + `experiment_instances.version` present, `breadboard_version` gone; `CreateFirstUser` bootstraps the admin; second boot reports "no evolutions to apply". |
| Existing v2.4 field DB + insert-only baseline | Boots clean, no DDL re-applied; `31.sql` drops the leftover `breadboard_version`. |
| Add a throwaway evolution (temp high number, e.g. `99.sql`) | Applies automatically on next prod boot (proves `applyEvolutions.default`). Then revert. |
| Legacy v2.2/v2.3 DB + `-Dbreadboard.legacyUpgrade=true` | Upgrade runs once (schema + data + file backups), `play_evolutions` baselined; subsequent normal boots are clean. |

---

## 4. Risks & footguns
- **Hash mismatch in the baseline** → Play runs downs+ups → data loss. Mitigate by capturing hashes from Play itself (Phase 3) and verifying a clean boot.
- **Schema divergence** between the evolution lineage and the real shipped/field schema → a future evolution assumes a column/shape that isn't there. Mitigate with the Phase 0 diff.
- **`applyDownEvolutions` in prod** → editing/removing an evolution could DROP tables on boot. Keep it `false` in prod.
- **Test harness coupling** → `BaseTest` builds schema from evolutions + two manual patches; after `30.sql`, remove the patches (lines 64‑68) so there's one source of truth. Keep the `play_evolutions` truncation guard (line 89).

## 5. Decisions (locked)
1. **Legacy-upgrade trigger:** `-Dbreadboard.legacyUpgrade=true` system-property flag, checked once in a minimal `Global.onStart`.
2. **New-install DB strategy:** ship an **empty** DB; evolutions build the schema on first boot; `CreateFirstUser` bootstraps the admin. No more pre-seeded `breadboard.h2.db.default.<ver>`.
3. **`breadboard_version` table:** **remove** it via a forward `31.sql` (`drop table if exists`); do not edit the applied `28.sql`. Schema-version tracking is owned by `play_evolutions`.

## 6. Suggested commit/PR sequencing
Each is independently reviewable and, except the last, leaves the app working:
1. Complete evolutions: add `30.sql` (gap columns) + `31.sql` (drop `breadboard_version`); delete the manual patches in `BaseTest.java:64‑68`; run `sbt test`.
2. Enable the plugin: config changes + `create_prod_dist.sh` (ship evolutions, ship empty DB). Land the baseline tooling for field DBs here.
3. Extract `LegacyDatabaseUpgrade` + the `-Dbreadboard.legacyUpgrade` flag; reduce `Global` to the flag check + `onStop`/`onRequest`; delete `InitialData` and dead imports.
