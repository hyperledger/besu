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


PUBLIC_KEYS_DIR=${BESU_PUBLIC_KEY_DIRECTORY:=/opt/besu/public-keys/}
BOOTNODE_KEY_FILE="${PUBLIC_KEYS_DIR}bootnode_pubkey"

rm -rf /opt/besu/database

# write pub key for making other nodes able to connect to bootnode
/opt/besu/bin/besu $@ public-key export --to="${BOOTNODE_KEY_FILE}"

p2pip=`awk 'END{print $1}' /etc/hosts`

# run bootnode with discovery but no bootnodes as it's our bootnode.
/opt/besu/bin/besu $@ --bootnodes --p2p-host=$p2pip

