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

# write pub key for making other nodes able to connect to bootnode
/opt/pantheon/bin/pantheon $@ --no-discovery export-pub-key /opt/pantheon/public-keys/bootnode

# run bootnode with discovery but no bootnodes as it's our bootnode.
/opt/pantheon/bin/pantheon $@