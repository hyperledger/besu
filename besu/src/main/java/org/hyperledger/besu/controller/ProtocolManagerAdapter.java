/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.data.PluginProtocolManager;

import java.util.List;

/**
 * Adapter class that converts a PluginProtocolManager to a ProtocolManager.
 */
public class ProtocolManagerAdapter implements ProtocolManager {

  private final PluginProtocolManager pluginProtocolManager;

  private ProtocolManagerAdapter(final PluginProtocolManager pluginProtocolManager) {
    this.pluginProtocolManager = pluginProtocolManager;
  }

  public static ProtocolManager create(final PluginProtocolManager pluginProtocolManager) {
    return new ProtocolManagerAdapter(pluginProtocolManager);
  }

  @Override
  public String getSupportedProtocol() {
    return pluginProtocolManager.getSupportedProtocol();
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return pluginProtocolManager.getSupportedCapabilities();
  }

  @Override
  public void stop() {
    pluginProtocolManager.stop();
  }

  @Override
  public void awaitStop() throws InterruptedException {
    pluginProtocolManager.awaitStop();
  }

  @Override
  public void processMessage(final Capability cap, final Message message) {
    pluginProtocolManager.processMessage(cap, message);
  }

  @Override
  public void handleNewConnection(final PeerConnection peerConnection) {
    pluginProtocolManager.handleNewConnection(peerConnection);
  }

  @Override
  public void handleDisconnect(
      final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {
    pluginProtocolManager.handleDisconnect(peerConnection, disconnectReason, initiatedByPeer);
  }

  @Override
  public int getHighestProtocolVersion() {
    return pluginProtocolManager.getHighestProtocolVersion();
  }

  @Override
  public void close() {
    pluginProtocolManager.close();
  }
}
