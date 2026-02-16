#!/bin/bash

# read a .env file if it exists
if [ -f .env ]; then
  echo "Reading .env file"
  source .env
fi

RUN_ARGS="-Dconfig.file=conf/application-dev.conf -DAMT_ACCESS_KEY=$AMT_ACCESS_KEY -DAMT_SECRET_KEY=$AMT_SECRET_KEY"
sbt -java-home "$JAVA8_HOME" "run $RUN_ARGS"