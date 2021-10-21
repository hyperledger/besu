/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.common.forking;

import java.util.List;
import java.util.Map;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

public class ForkingProtocolManager implements ProtocolManager, BlockAddedObserver {

  private final Map<Long, ProtocolManager> protocolManagerForks;
  private ProtocolManager activeProtocolManager;

  public ForkingProtocolManager(final Map<Long, ProtocolManager> protocolManagerForks) {
    this.protocolManagerForks = protocolManagerForks;
    this.activeProtocolManager = protocolManagerForks.get(0L);
  }

  @Override
  public String getSupportedProtocol() {
    return activeProtocolManager.getSupportedProtocol();
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return activeProtocolManager.getSupportedCapabilities();
  }

  @Override
  public void stop() {
    activeProtocolManager.stop();
  }

  @Override
  public void awaitStop() throws InterruptedException {
    activeProtocolManager.awaitStop();
  }

  @Override
  public void processMessage(final Capability cap, final Message message) {
    activeProtocolManager.processMessage(cap, message);
  }

  @Override
  public void handleNewConnection(final PeerConnection peerConnection) {
    activeProtocolManager.handleNewConnection(peerConnection);
  }

  @Override
  public void handleDisconnect(final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {
    activeProtocolManager.handleDisconnect(peerConnection, disconnectReason, initiatedByPeer);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      final long blockNumber = event.getBlock().getHeader().getNumber();
      if (protocolManagerForks.containsKey(blockNumber)) {
        final ProtocolManager newProtocolManager = protocolManagerForks.get(blockNumber);
        activeProtocolManager.stop();
        activeProtocolManager = newProtocolManager;
      }
    }
  }
}
