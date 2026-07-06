#!/usr/bin/env bash
set -e # exit on error
set -u # exit on unset variable
set -o pipefail # exit on pipe failure

breadboard_version="v2.5.0"

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

# Build the in-place upgrade kit from the install tree (install/breadboard-${breadboard_version}) the
# steps above just produced. The applier (scripts/apply-patch.sh) is version-agnostic and re-runnable:
# it overwrites whatever Breadboard is installed with this kit's jar + groovy, so the kit is named for
# the target version only. The kit is staged fresh under target/ (a throwaway) -- the tracked source is
# scripts/apply-patch.sh, NOT the kit dir, so rebuilding never destroys anything. A recipient unzips
# and runs:
#   ./upgrade-${breadboard_version}-in-place/apply-patch.sh /path/to/your-breadboard-install
set -e   # packaging errors must abort -- unlike the best-effort cleanup rm's above (set +e)

built_install="install/breadboard-${breadboard_version}"
built_jar="${built_install}/lib/breadboard.breadboard-${breadboard_version}.jar"
apply_script="scripts/apply-patch.sh"
[ -f "$built_jar" ]    || { echo "ERROR: built jar not found, cannot build patch: ${built_jar}" >&2; exit 1; }
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
cp "$built_install"/groovy/*.groovy "$kit/groovy/"

# Zip from INSIDE the staging dir so the archive's top-level entry is ${kit_name}/ (no path prefix),
# then move the finished zip into scripts/. (Relative output name sidesteps an MSYS zip quirk with
# absolute output paths.)
( cd "$staging" && zip -rq "${kit_name}.zip" "$kit_name" )
mv "$staging/${kit_name}.zip" "$patch_zip"
rm -rf "$staging"
echo "Built in-place patch: ${patch_zip}  (apply-patch.sh + jar + groovy from the ${breadboard_version} build)"
