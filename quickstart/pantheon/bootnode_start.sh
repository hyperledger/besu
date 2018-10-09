#!/bin/sh -e

# write pub key for making other nodes able to connect to bootnode
/opt/pantheon/bin/pantheon $@ --no-discovery export-pub-key /opt/pantheon/public-keys/bootnode

# run bootnode with discovery but no bootnodes as it's our bootnode.
/opt/pantheon/bin/pantheon $@ --bootnodes=""