#!/bin/bash

export GOSS_PATH=tests/goss-linux-amd64
export GOSS_OPTS="$GOSS_OPTS --format junit"
export GOSS_FILES_STRATEGY=cp
DOCKER_IMAGE=$1

i=0

# Test for normal startup with ports opened
GOSS_FILES_PATH=tests/01 \
bash tests/dgoss \
run $DOCKER_IMAGE \
--network=dev \
--p2p-host=0.0.0.0 \
--rpc-http-enabled \
--rpc-http-host=0.0.0.0 \
--rpc-ws-enabled \
--rpc-ws-host=0.0.0.0 \
--graphql-http-enabled \
--graphql-http-host=0.0.0.0 \
> ./reports/01.xml || i=`expr $i + 1`

exit $i
