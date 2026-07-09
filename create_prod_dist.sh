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

# turn off error checking for the next commands
set +e
rm -r install/breadboard-${breadboard_version}
rm install/breadboard-${breadboard_version}.zip
unzip target/universal/breadboard-${breadboard_version}.zip -d install
mkdir install/breadboard-${breadboard_version}/groovy
cp groovy/*.groovy install/breadboard-${breadboard_version}/groovy
# No seed DB is shipped: the app boots with an empty H2 database and Play evolutions build
# the schema on first start (H2 creates db/breadboard.h2.db, and the dir, on demand). We still
# create db/ here so the directory ships in the install tree. The first admin account is
# created via POST /createFirstUser, which is open until a user exists.
mkdir install/breadboard-${breadboard_version}/db
cp prod_dist/license.txt install/breadboard-${breadboard_version}/
cp breadboard-${breadboard_version}.bat install/breadboard-${breadboard_version}/breadboard.bat
cp breadboard-${breadboard_version}.sh install/breadboard-${breadboard_version}/breadboard.sh
cp ../breadboard-wiki/Release-History.md install/breadboard-${breadboard_version}/CHANGELOG.md
rm -r install/breadboard-${breadboard_version}/conf/evolutions
rm -r install/breadboard-${breadboard_version}/share
rm install/breadboard-${breadboard_version}/conf/application.conf
rm install/breadboard-${breadboard_version}/conf/application-dev.conf
rm install/breadboard-${breadboard_version}/conf/generated.keystore
# The sbt-generated bin/breadboard (unzipped above) already has the correct, current
# classpath. The sbt-native-packager template just doesn't add --add-modules java.xml.bind
# for JDK 9+ (java.xml.bind was removed from the JDK), so inject that block in place. This
# keeps the launcher matched to the build -- no hand-maintained copy to drift out of date.
bin_launcher="install/breadboard-${breadboard_version}/bin/breadboard"
if grep -q "add-modules java.xml.bind" "$bin_launcher"; then
  echo "bin/breadboard already contains the JDK9 --add-modules block; skipping injection"
elif grep -q "# run sbt" "$bin_launcher"; then
  awk '
    /# run sbt/ && !injected {
      print ""
      print "  # If using JDK9 we need to add --add-modules java.xml.bind"
      print "  if [[ \"$java_version\" > \"9\" ]]; then"
      print "    addJava \"--add-modules java.xml.bind\""
      print "  fi"
      print ""
      injected = 1
    }
    { print }
  ' "$bin_launcher" > "$bin_launcher.tmp" && mv "$bin_launcher.tmp" "$bin_launcher"
  chmod +x "$bin_launcher"
  echo "Injected JDK9 --add-modules block into bin/breadboard"
else
  echo "WARNING: anchor '# run sbt' not found in $bin_launcher; launcher may fail on JDK 9+" >&2
fi
cd install
cp breadboard-${breadboard_version}/lib/breadboard.breadboard-${breadboard_version}.jar ../target/universal/breadboard.breadboard-${breadboard_version}.jar
zip -rq breadboard-${breadboard_version}.zip breadboard-${breadboard_version}
cd ..

# --- Build the in-place upgrade kit from the freshly-rebuilt install tree above -----------------
# install/breadboard-${breadboard_version} is regenerated from target/universal on every run (just
# above), so it is no longer the stale, hand-maintained dir that once shipped a pre-feature jar in the
# patch. The applier (scripts/apply-patch.sh) is version-agnostic and re-runnable: it overwrites
# whatever Breadboard is installed with this kit's jar + groovy, so the kit is named for the target
# version only. The kit is staged fresh under target/ (a throwaway) -- the tracked source is
# scripts/apply-patch.sh, NOT the kit dir, so rebuilding never destroys anything. A recipient unzips
# and runs:
#   ./upgrade-${breadboard_version}-in-place/apply-patch.sh /path/to/your-breadboard-install
set -e   # packaging errors must abort -- unlike the best-effort install steps above (set +e)

built_install="install/breadboard-${breadboard_version}"
built_jar="${built_install}/lib/breadboard.breadboard-${breadboard_version}.jar"
# Runtime groovy scripts are loaded from disk (not bundled in the jar), so ship the repo's copy.
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
