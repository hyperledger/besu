#!/usr/bin/env sh
##
## Copyright contributors to Besu.
##
## Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
## an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
## specific language governing permissions and limitations under the License.
##
## SPDX-License-Identifier: Apache-2.0
##

# call
../../../build/install/evmtool/bin/evm  \
  --code=5B600080808060045AFA50600056 \
  --sender=0xd1cf9d73a91de6630c2bb068ba5fddf9f0deac09 \
  --receiver=0x588108d3eab34e94484d7cda5a1d31804ca96fe7 \
  --genesis=evmtool-genesis.json \
  --gas 100000000 \
  --repeat=10

# pop
../../../build/install/evmtool/bin/evm  \
  --code=5B600080808060045A505050505050600056 \
  --sender=0xd1cf9d73a91de6630c2bb068ba5fddf9f0deac09 \
  --receiver=0x588108d3eab34e94484d7cda5a1d31804ca96fe7 \
  --genesis=evmtool-genesis.json \
  --gas 100000000 \
  --repeat=10
