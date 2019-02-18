/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.util.uint.UInt256;

class BlockBroadcaster {

  private final EthContext ethContext;

  BlockBroadcaster(final EthContext ethContext) {
    this.ethContext = ethContext;
  }

  void propagate(final Block block, final UInt256 difficulty) {
    ethContext
        .getEthPeers()
        .availablePeers()
        .filter(ethPeer -> !ethPeer.hasSeenBlock(block.getHash()))
        .forEach(ethPeer -> ethPeer.propagateBlock(block, difficulty));
  }
}
