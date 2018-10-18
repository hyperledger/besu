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