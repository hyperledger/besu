#!/bin/sh
echo HI!

source /gethUtils.sh

BOOTNODE_CMD=bootnode

$BOOTNODE_CMD --genkey=$nodedir/boot.key
$BOOTNODE_CMD  --nodekey=$nodedir/boot.key --addr=:30303 &> $datadir/geth.log &

logEnode

sleepforever
