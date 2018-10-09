#!/bin/sh -e

QUICKSTART_FOLDER=quickstart

me=`basename "$0"`

if [ ! -f gradlew ]; then
    echo "Please run this script from the project root using : ${QUICKSTART_FOLDER}/${me}"
    exit 1
fi

COMPOSE_CONFIG_FILE_OPTION="-f ${QUICKSTART_FOLDER}/docker-compose.yml"

docker-compose ${COMPOSE_CONFIG_FILE_OPTION} down
docker-compose ${COMPOSE_CONFIG_FILE_OPTION} rm -sfv