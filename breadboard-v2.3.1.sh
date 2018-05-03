#!/bin/sh
./bin/breadboard -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf/application-prod.conf -J-XX:+CMSClassUnloadingEnabled -J-XX:+UseConcMarkSweepGC
