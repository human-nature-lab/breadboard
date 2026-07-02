#!/usr/bin/env bash
set -e # exit on error
set -o pipefail # exit on pipe failure

breadboard_version="v2.5.0"

# The full-release baseline that the in-place upgrade patch (built at the end of this script) is
# applied ON TOP of. The patch upgrades a breadboard-${patch_from_version} install to
# ${breadboard_version}, so bump breadboard_version to the target (e.g. v2.5.0) when cutting a patch.
patch_from_version="v2.3.1"

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
  sbt -java-home "$JAVA_HOME" dist
else
  sbt dist
fi

# turn off error checking for the next commands
set +e
rm -r install/breadboard-${breadboard_version}
rm install/breadboard-${breadboard_version}.zip
unzip target/universal/breadboard-${breadboard_version}.zip -d install
mkdir install/breadboard-${breadboard_version}/groovy
cp groovy/*.groovy install/breadboard-${breadboard_version}/groovy
mkdir install/breadboard-${breadboard_version}/db
cp db/breadboard.h2.db.default.${breadboard_version} install/breadboard-${breadboard_version}/db/breadboard.h2.db
cp prod_dist/license.txt install/breadboard-${breadboard_version}/
cp breadboard-${breadboard_version}.bat install/breadboard-${breadboard_version}/breadboard.bat
cp breadboard-${breadboard_version}.sh install/breadboard-${breadboard_version}/breadboard.sh
cp ../breadboard-wiki/Release-History.md install/breadboard-${breadboard_version}/CHANGELOG.md
rm -r install/breadboard-${breadboard_version}/conf/evolutions
rm -r install/breadboard-${breadboard_version}/share
rm install/breadboard-${breadboard_version}/conf/application.conf
rm install/breadboard-${breadboard_version}/conf/application-dev.conf
rm install/breadboard-${breadboard_version}/conf/generated.keystore
cp prod_dist/${breadboard_version}/bin/breadboard install/breadboard-${breadboard_version}/bin/
cd install
cp breadboard-${breadboard_version}/lib/breadboard.breadboard-${breadboard_version}.jar ../target/universal/breadboard.breadboard-${breadboard_version}.jar
zip -rq breadboard-${breadboard_version}.zip breadboard-${breadboard_version}
cd ..

# ---------------------------------------------------------------------------
# In-place upgrade patch (keeps apply-patch.sh, the jar, and the groovy scripts in sync)
# ---------------------------------------------------------------------------
# Assemble the in-place upgrade kit from THIS build's artifacts so the applier, the compiled jar, and
# the interpreted groovy/*.groovy can never drift apart. The jar and groovy come from the single
# install tree (install/breadboard-${breadboard_version}) the steps above just produced; the applier
# is the tracked scripts/apply-patch.sh. The kit is staged fresh under target/ (a throwaway) -- the
# tracked source is scripts/apply-patch.sh, NOT the kit dir, so rebuilding never destroys anything.
# A recipient unzips and runs:
#   ./patch-${patch_from_version}-to-${breadboard_version}/apply-patch.sh /path/to/breadboard-${patch_from_version}
set -e   # packaging errors must abort -- unlike the best-effort cleanup rm's above (set +e)

patch_to_version="${breadboard_version}"
built_install="install/breadboard-${patch_to_version}"
built_jar="${built_install}/lib/breadboard.breadboard-${patch_to_version}.jar"
apply_script="scripts/apply-patch.sh"
[ -f "$built_jar" ]    || { echo "ERROR: built jar not found, cannot build patch: ${built_jar}" >&2; exit 1; }
[ -f "$apply_script" ] || { echo "ERROR: apply script not found: ${apply_script}" >&2; exit 1; }

kit_name="patch-${patch_from_version}-to-${patch_to_version}"
staging="target/patch-build"            # throwaway; under target/ so it never touches tracked files
kit="${staging}/${kit_name}"
patch_zip="scripts/upgrade-${patch_to_version}-in-place.zip"

# Assemble fresh: applier + new jar + the ${patch_to_version} groovy, all SIBLINGS in the kit dir
# (apply-patch.sh resolves the jar and groovy/ relative to its own directory).
rm -rf "$staging" "$patch_zip"
mkdir -p "$kit/groovy"
cp "$apply_script" "$kit/"
chmod +x "$kit/apply-patch.sh"
cp "$built_jar" "$kit/"
cp "$built_install"/groovy/*.groovy "$kit/groovy/"

# Zip from INSIDE the staging dir so the archive's top-level entry is ${kit_name}/ (no path prefix),
# then move the finished zip into scripts/. (Relative output name sidesteps an MSYS zip quirk with
# absolute output paths.)
( cd "$staging" && zip -rq "${kit_name}.zip" "$kit_name" )
mv "$staging/${kit_name}.zip" "$patch_zip"
rm -rf "$staging"
echo "Built in-place patch: ${patch_zip}  (apply-patch.sh + jar + groovy from the ${patch_to_version} build)"
