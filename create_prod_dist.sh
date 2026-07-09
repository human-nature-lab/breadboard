#!/usr/bin/env bash
set -e # exit on error
set -u # exit on unset variable
set -o pipefail # exit on pipe failure

# The version is NOT hardcoded here -- it is discovered from what `sbt dist`
# produces (below), which comes from Build.scala's appVersion. That honors the
# $BREADBOARD_VERSION env var: CI sets it from the pushed git tag, and it
# defaults to v2.5.0 locally. Deriving it keeps this script in lockstep with the
# build no matter how the version was chosen.

# Build local packages and wire them into the main frontend tree before webpack.
cd frontend/
pnpm install --frozen-lockfile
pnpm run build:all
cd ..

# read a .env file if it exists
if [ -f .env ]; then
  echo "Reading .env file"
  source ./.env
fi

# Activator was deprecated; use sbt directly (same as `activator dist` for this project).
if [ -n "${JAVA_HOME:-}" ]; then
  sbt -java-home "$JAVA_HOME" clean
  sbt -java-home "$JAVA_HOME" dist
else
  sbt clean
  sbt dist
fi

# Build the in-place upgrade kit from the dist that 'sbt dist' just produced
# (target/universal/breadboard-${breadboard_version}.zip). We source the jar from that FRESH dist
# output -- NOT from a hand-maintained install/ tree, which is gitignored and trivially left stale
# (doing exactly that once shipped a pre-feature jar in the patch). The runtime groovy/ scripts are not
# bundled by sbt dist, so they come straight from the repo's tracked groovy/ dir. The applier
# (scripts/apply-patch.sh) is version-agnostic and re-runnable: it overwrites whatever Breadboard is
# installed with this kit's jar + groovy, so the kit is named for the target version only. The kit is
# staged fresh under target/ (a throwaway) -- the tracked source is scripts/apply-patch.sh, NOT the kit
# dir, so rebuilding never destroys anything. A recipient unzips and runs:
#   ./upgrade-${breadboard_version}-in-place/apply-patch.sh /path/to/your-breadboard-install
set -e   # packaging errors must abort -- unlike the best-effort cleanup rm's above (set +e)

# Unzip the fresh dist into a throwaway under target/ and read the jar from there, so the kit ships
# exactly what the sbt dist above built (not a stale copy). `sbt clean` emptied target/ first, so
# there is exactly one zip; derive breadboard_version from its name (its single source of truth).
dist_zip="$(ls -t target/universal/breadboard-*.zip 2>/dev/null | head -1 || true)"
[ -n "${dist_zip:-}" ] && [ -f "$dist_zip" ] || { echo "ERROR: dist zip not found (did 'sbt dist' run?): target/universal/breadboard-*.zip" >&2; exit 1; }
breadboard_version="$(basename "$dist_zip" .zip | sed 's/^breadboard-//')"
echo "Packaging breadboard ${breadboard_version} (from ${dist_zip})"
dist_unzipped="target/dist-unzipped"
rm -rf "$dist_unzipped"
mkdir -p "$dist_unzipped"
unzip -q "$dist_zip" -d "$dist_unzipped"
built_jar="${dist_unzipped}/breadboard-${breadboard_version}/lib/breadboard.breadboard-${breadboard_version}.jar"
# Runtime groovy scripts are loaded from disk (not bundled in the jar / dist), so ship the repo's copy.
groovy_src="groovy"
apply_script="scripts/apply-patch.sh"
[ -f "$built_jar" ]    || { echo "ERROR: built jar not found in dist: ${built_jar}" >&2; exit 1; }
ls "$groovy_src"/*.groovy >/dev/null 2>&1 || { echo "ERROR: no groovy scripts in ${groovy_src}/" >&2; exit 1; }
[ -f "$apply_script" ] || { echo "ERROR: apply script not found: ${apply_script}" >&2; exit 1; }

kit_name="upgrade-${breadboard_version}-in-place"   # also the archive's top-level dir on unzip
staging="target/patch-build"            # throwaway; under target/ so it never touches tracked files
kit="${staging}/${kit_name}"
patch_zip="scripts/${kit_name}.zip"

# Assemble fresh: applier + new jar + the ${breadboard_version} groovy, all SIBLINGS in the kit dir
# (apply-patch.sh resolves the jar and groovy/ relative to its own directory).
rm -rf "$staging" "$patch_zip"
mkdir -p "$kit/groovy"
cp "$apply_script" "$kit/"
chmod +x "$kit/apply-patch.sh"
cp "$built_jar" "$kit/"
cp "$groovy_src"/*.groovy "$kit/groovy/"

# Zip from INSIDE the staging dir so the archive's top-level entry is ${kit_name}/ (no path prefix),
# then move the finished zip into scripts/. (Relative output name sidesteps an MSYS zip quirk with
# absolute output paths.)
( cd "$staging" && zip -rq "${kit_name}.zip" "$kit_name" )
mv "$staging/${kit_name}.zip" "$patch_zip"
rm -rf "$staging"
echo "Built in-place patch: ${patch_zip}  (apply-patch.sh + jar + groovy from the ${breadboard_version} build)"

# TODO #11 -- bundle a Java 8 runtime so recipients need no host Java. This produces the
# self-contained per-platform zips (dist + JDK + groovy + wrapper) and the Docker build context
# from the dist we just unpacked. Kept as a separate, re-runnable script so it can also run in CI
# right after the dist step, or by hand to re-bundle without rebuilding the app. SKIP_BUNDLE=1
# stops after the patch kit above.
if [ "${SKIP_BUNDLE:-0}" = "1" ]; then
  echo "SKIP_BUNDLE=1 -- skipping the Java-8-bundled platform zips and Docker context."
else
  ./scripts/bundle-runtime.sh "${dist_unzipped}/breadboard-${breadboard_version}" "${breadboard_version}"
fi
