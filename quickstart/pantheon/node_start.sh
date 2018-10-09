#!/bin/sh -e

# write pub key for checking that network works
node_id=`hostname`

/opt/pantheon/bin/pantheon $@ export-pub-key "/opt/pantheon/public-keys/${node_id}"

BOOTNODE_KEY_FILE=/opt/pantheon/public-keys/bootnode

# sleep loop to wait for the public key file to be written
while [ ! -f $BOOTNODE_KEY_FILE ]
do
  sleep 1
done

# get bootnode enode address
bootnode_pubkey=`sed 's/^0x//' $BOOTNODE_KEY_FILE`
boonode_ip=`getent hosts bootnode | awk '{ print $1 }'`
BOOTNODE_P2P_PORT="30303"

bootnode_enode_address="enode://${bootnode_pubkey}@${boonode_ip}:${BOOTNODE_P2P_PORT}"

# run with bootnode param
/opt/pantheon/bin/pantheon $@ --bootnodes=$bootnode_enode_address