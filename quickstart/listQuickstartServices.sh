#!/bin/sh -e

# Copyright 2018 ConsenSys AG.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
# an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

QUICKSTART_FOLDER=quickstart

me=`basename "$0"`

if [ ! -f gradlew ]; then
    echo "Please run this script from the project root using : ${QUICKSTART_FOLDER}/${me}"
    exit 1
fi

COMPOSE_CONFIG_FILE_OPTION="-f ${QUICKSTART_FOLDER}/docker-compose.yml"
EXPLORER_SERVICE=explorer
HOST=${DOCKER_PORT_2375_TCP_ADDR:-"localhost"}

# Displays services list with port mapping
docker-compose ${COMPOSE_CONFIG_FILE_OPTION} ps

# Get individual port mapping for exposed services
explorerMapping=`docker-compose ${COMPOSE_CONFIG_FILE_OPTION} port explorer 80`

#Check if we run in a tty before using exec and otherwise set $TERM as it fails if not set.
if [ ! -t 1 ] ;
then
  export TERM=xterm-color
fi

# Displays links to exposed services
ORANGE='\033[0;33m'
CYAN='\033[0;36m'
BOLD=$(tput bold)
NORMAL=$(tput sgr0)

echo "${CYAN}****************************************************************"
dots=""
maxRetryCount=50
while [ "$(curl -m 1 -s -o /dev/null -w ''%{http_code}'' http://${HOST}:${explorerMapping##*:})" != "200" ] && [ ${#dots} -le ${maxRetryCount} ]
do
  dots=$dots"."
  printf "${CYAN} Block explorer is starting, please wait ${ORANGE}$dots${NORMAL}\\r"
  sleep 1
done

if [ ${#dots} -gt ${maxRetryCount} ]; then
  (>&2 echo "${ORANGE}ERROR: Web block explorer is not started at http://${HOST}:${explorerMapping##*:}$ !${CYAN}   *                                                                             ")
  echo "****************************************************************${NORMAL}"
else
    echo "JSON-RPC ${BOLD}HTTP${NORMAL}${CYAN} service endpoint      : ${ORANGE}http://${HOST}:${explorerMapping##*:}/jsonrpc${CYAN}   *"
    echo "JSON-RPC ${BOLD}WebSocket${NORMAL}${CYAN} service endpoint : ${ORANGE}http://${HOST}:${explorerMapping##*:}/jsonws${CYAN}   *"
  echo "${CYAN}Web block explorer address          : ${ORANGE}http://${HOST}:${explorerMapping##*:}${CYAN}   *                                                                             "
  echo "****************************************************************${NORMAL}"
fi
