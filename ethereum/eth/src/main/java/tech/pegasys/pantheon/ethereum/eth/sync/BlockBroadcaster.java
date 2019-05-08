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
import tech.pegasys.pantheon.ethereum.eth.messages.NewBlockMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockBroadcaster {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;

  public BlockBroadcaster(final EthContext ethContext) {
    this.ethContext = ethContext;
  }

  public void propagate(final Block block, final UInt256 totalDifficulty) {
    final NewBlockMessage newBlockMessage = NewBlockMessage.create(block, totalDifficulty);
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(ethPeer -> !ethPeer.hasSeenBlock(block.getHash()))
        .forEach(
            ethPeer -> {
              ethPeer.registerKnownBlock(block.getHash());
              try {
                ethPeer.send(newBlockMessage);
              } catch (final PeerConnection.PeerNotConnected e) {
                LOG.trace("Failed to broadcast new block to peer", e);
              }
            });
  }
}
