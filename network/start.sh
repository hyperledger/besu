#!/bin/bash

if [[ "$1" == "--no-data" ]]
  then
    echo "Cleaning data..."
    kill $(ps -ef | grep -v grep | grep java | awk '{print $2}') &>/dev/null
    find ./node-1/data/* ! -wholename './node-1/data/key*' -exec rm -rf {} +
    find ./node-2/data/* ! -wholename './node-2/data/key*' -exec rm -rf {} +
    find ./node-3/data/* ! -wholename './node-3/data/key*' -exec rm -rf {} +
    find ./node-4/data/* ! -wholename './node-4/data/key*' -exec rm -rf {} +
    find . -name "node.log" -delete
    if [[ "$2" == "--only" ]]
      then
          echo "Done!"
          exit 0
    fi
fi

stty -echo
echo Starting 4 nodes, please wait for logs...

cd ./node-4
../../build/install/besu/bin/besu --data-path=data --genesis-file=../genesis.json --bootnodes=enode://7c19f5309e268264c9a986704a2f24e01f466a541ef910f68cc831324ba1ee2cb96dad8c5071139dc3419fd37f920f20158e7563040eaf4b20a7da0930e7cbda@127.0.0.1:30303 --p2p-port=30306 --rpc-http-enabled --rpc-http-api=ETH,NET,QBFT --host-allowlist="*" --rpc-http-cors-origins="all" --rpc-http-port=8548 &> ./node.log &

cd ../node-3
../../build/install/besu/bin/besu --data-path=data --genesis-file=../genesis.json --bootnodes=enode://7c19f5309e268264c9a986704a2f24e01f466a541ef910f68cc831324ba1ee2cb96dad8c5071139dc3419fd37f920f20158e7563040eaf4b20a7da0930e7cbda@127.0.0.1:30303 --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,QBFT --host-allowlist="*" --rpc-http-cors-origins="all" --rpc-http-port=8547 &> ./node.log &

cd ../node-2
../../build/install/besu/bin/besu --logging=DEBUG --data-path=data --genesis-file=../genesis.json --bootnodes=enode://7c19f5309e268264c9a986704a2f24e01f466a541ef910f68cc831324ba1ee2cb96dad8c5071139dc3419fd37f920f20158e7563040eaf4b20a7da0930e7cbda@127.0.0.1:30303 --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,QBFT --host-allowlist="*" --rpc-http-cors-origins="all" --rpc-http-port=8546 &> ./node.log &

cd ../node-1
export BESU OPTS=-agentLib:jdwp==transport=dt_socket,server=y,suspend=no,address=5000 
../../build/install/besu/bin/besu --data-path=data --genesis-file=../genesis.json --rpc-http-enabled --rpc-http-api=ETH,NET,QBFT --host-allowlist="*" --rpc-http-cors-origins="all"
unset BESU OPTS
cd ..

echo 
echo Stopping nodes $(ps -ef | grep -v grep | grep -v defunct | grep java | awk '{print $2}')
kill $(ps -ef | grep -v grep | grep java | awk '{print $2}') &>/dev/null

stty echo
