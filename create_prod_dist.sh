#!/usr/bin/env bash
set -e # exit on error
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
cp prod_dist/${breadboard_version}/bin/breadboard install/breadboard-${breadboard_version}/bin/
cd install
cp breadboard-${breadboard_version}/lib/breadboard.breadboard-${breadboard_version}.jar ../target/universal/breadboard.breadboard-${breadboard_version}.jar
zip -rq breadboard-${breadboard_version}.zip breadboard-${breadboard_version}
