#!/bin/sh

source /gethUtils.sh

cd /go-ethereum
geth --datadir=$datadir init $datadir/genesis.json
geth -verbosity 6 --datadir=$datadir --syncmode "full" --rpc --rpcapi eth,web3,admin --rpcaddr 0.0.0.0 --bootnodes="$geth_bootnodes" --networkid 15 &> $datadir/geth.log &

logEnode

sleepforever