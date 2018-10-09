#!/bin/sh

function waitForLogLine() {
    # Note, this function will wait forever
    local myStr=$1
    local myFile=$2
    until grep -q "${myStr}" "${myFile}"
    do
#      echo "Sleeping 0.1s"
      sleep 0.1s
    done
}

function logEnode() {
    waitForLogLine 'UDP listener up *self=' $datadir/geth.log
#    echo "ip_addr=\$(getent hosts boot | awk '{ print \$1 }')"
    ip_addr=$(hostname -i)
#    ip_addr="192.168.71.128"
    echo "ip_addr=$ip_addr"

    echo "enode=\$( grep enode \$datadir/geth.log | tail -n 1 | sed s/^.*enode:/enode:/ | sed \"s/\[\:\:\]/$ip_addr/g\" )"
    enode=$( grep enode $datadir/geth.log | tail -n 1 | sed s/^.*enode:/enode:/ | sed "s/\[\:\:\]/$ip_addr/g" )
    echo "enode=$enode"

    echo $enode > $BOOTENODEFILE
    echo cat file
    cat $BOOTENODEFILE
}

function sleepforever() {
    while true; do
        sleep 9999d
    done
}

datadir=REPLACE_DATA_DIR
BOOTENODEFILE=$datadir/enode.log

# Note: aliases dependent on $nodedir need to be loaded here instead of ~/.bash_aliases
# because ~/.bash_aliases is loaded before this script is sourced.
alias geth="geth --datadir=$datadir "
alias peers="geth attach --exec 'admin.peers'"