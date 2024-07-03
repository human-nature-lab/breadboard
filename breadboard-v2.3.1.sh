#!/bin/sh
chmod u+x ./bin/breadboard
chmod u+x ./db/breadboard.h2.db
./bin/breadboard -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf/application-prod.conf -J-XX:+CMSClassUnloadingEnabled -J-XX:+UseConcMarkSweepGC
