#!/bin/sh -e
me=`basename "$0"`

PARAMS=""
while (( "$#" )); do
  case "$1" in
    -h|--help)
      echo "Usage:"
      exit 0
      ;;
    -s|--skip-build)
      SKIP_BUILD=true
      shift 1
      ;;
    -d|--use-default-static-ports)
      export RPC_PORT_MAPPING="8545:"
      export WS_PORT_MAPPING="8546:"
      export EXPLORER_PORT_MAPPING="3000:"

      break 2
      ;;
    --rpc-port)
      export RPC_PORT_MAPPING="${2}:"
      shift 2
      ;;
    --ws-port)
      export WS_PORT_MAPPING="${2}:"
      shift 2
      ;;
    --explorer-port)
      export EXPLORER_PORT_MAPPING="${2}:"
      shift 2
      ;;
    --) # end argument parsing
      shift
      break
      ;;
    -*|--*=) # unsupported flags
      echo "Error: Unsupported flag $1, try ${me} -h or ${me} --help for complete usage help." >&2
      exit 1
      ;;
    *) # preserve positional arguments
      PARAMS="$PARAMS $1"
      shift
      ;;
  esac
done
# set positional arguments in their proper place
eval set -- "$PARAMS"

QUICKSTART_FOLDER=quickstart

if [ ! -f gradlew ]; then
    echo "Please, run this script from the project root using : ${QUICKSTART_FOLDER}/${me}"
    exit 1
fi

COMPOSE_CONFIG_FILE_OPTION="-f ${QUICKSTART_FOLDER}/docker-compose.yml"

# Build and run containers and network

if [ ! ${SKIP_BUILD} ];then
  docker-compose ${COMPOSE_CONFIG_FILE_OPTION} build --force-rm
fi
docker-compose ${COMPOSE_CONFIG_FILE_OPTION} up -d --scale node=4

${QUICKSTART_FOLDER}/listQuickstartServices.sh