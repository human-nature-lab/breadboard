#!/usr/bin/env bash
breadboard_version="v2.3.0"
cd frontend
webpack -p --config webpack/webpack.prod.js
cd ..
activator dist
rm -r install/breadboard-${breadboard_version}
rm install/breadboard-${breadboard_version}.zip
unzip target/universal/breadboard-${breadboard_version}.zip -d install
mkdir install/breadboard-${breadboard_version}/groovy
cp groovy/hello.groovy install/breadboard-${breadboard_version}/groovy
mkdir install/breadboard-${breadboard_version}/db
cp db/breadboard.h2.db.default.${breadboard_version} install/breadboard-${breadboard_version}/db/breadboard.h2.db
cp license.txt install/breadboard-${breadboard_version}/
cp breadboard-${breadboard_version}.bat install/breadboard-${breadboard_version}/breadboard.bat
cp breadboard-${breadboard_version}.sh install/breadboard-${breadboard_version}/breadboard.sh
cp ../breadboard-wiki/Release-History.md install/breadboard-${breadboard_version}/CHANGELOG.md
rm -r install/breadboard-${breadboard_version}/conf/evolutions
rm install/breadboard-${breadboard_version}/conf/application.conf
rm install/breadboard-${breadboard_version}/conf/application-dev.conf
rm install/breadboard-${breadboard_version}/conf/generated.keystore
cd install
zip -r install/breadboard-${breadboard_version}.zip breadboard-${breadboard_version}
