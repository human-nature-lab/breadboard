#!/bin/sh
#./start -Dapplication.secret="pleasechangethis" -Dconfig.file=application-prod.conf
activator -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf/application-prod.conf ~run
