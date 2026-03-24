/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.framing.FramingException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapProtocolManager implements ProtocolManager {
  private static final Logger LOG = LoggerFactory.getLogger(SnapProtocolManager.class);

  private final List<Capability> supportedCapabilities;
  private final EthPeers ethPeers;
  private final EthMessages snapMessages;

  public SnapProtocolManager(
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final SnapSyncConfiguration snapConfig,
      final EthPeers ethPeers,
      final EthMessages snapMessages,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Synchronizer synchronizer) {
    this.ethPeers = ethPeers;
    this.snapMessages = snapMessages;
    this.supportedCapabilities = calculateCapabilities(protocolSchedule);
    new SnapServer(
        snapConfig, snapMessages, worldStateStorageCoordinator, protocolContext, synchronizer);
  }

  private List<Capability> calculateCapabilities(final ProtocolSchedule protocolSchedule) {
    final ImmutableList.Builder<Capability> capabilities = ImmutableList.builder();
    capabilities.add(SnapProtocol.SNAP1);
    if (protocolSchedule.anyMatch(spec -> spec.spec().isBlockAccessListEnabled())) {
      capabilities.add(SnapProtocol.SNAP2);
    }

    return capabilities.build();
  }

  @Override
  public String getSupportedProtocol() {
    return SnapProtocol.NAME;
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  @Override
  public void stop() {}

  @Override
  public void awaitStop() throws InterruptedException {}

  /**
   * This function is called by the P2P framework when a SNAP message has been received.
   *
   * @param cap The capability under which the message was transmitted.
   * @param message The message to be decoded.
   */
  @Override
  public void processMessage(final Capability cap, final Message message) {
    final int code = message.getData().getCode();
    LOG.trace("Process snap message {}, {}", cap, code);
    final EthPeer ethPeer = ethPeers.peer(message.getConnection());
    if (ethPeer == null) {
      LOG.debug(
          "Ignoring message received from unknown peer connection: {}", message.getConnection());
      return;
    }

    final EthMessage ethMessage = new EthMessage(ethPeer, message.getData());
    if (!ethPeer.validateReceivedMessage(ethMessage, getSupportedProtocol())) {
      LOG.debug("Unsolicited message {} received from, disconnecting: {}", code, ethPeer);
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_UNSOLICITED_MESSAGE_RECEIVED);
      return;
    }

    Optional<MessageData> maybeResponseData = Optional.empty();
    try {
      final MessageData messageData = AbstractSnapMessageData.create(message);
      final EthMessage decodedEthMessage = new EthMessage(ethPeer, messageData);

      // This will handle responses
      ethPeers.dispatchMessage(ethPeer, decodedEthMessage, getSupportedProtocol());

      // This will handle requests
      final Map.Entry<BigInteger, MessageData> requestIdAndEthMessage =
          decodedEthMessage.getData().unwrapMessageData();
      maybeResponseData =
          snapMessages
              .dispatch(new EthMessage(ethPeer, requestIdAndEthMessage.getValue()), cap)
              .map(responseData -> responseData.wrapMessageData(requestIdAndEthMessage.getKey()));
    } catch (final FramingException e) {
      LOG.atDebug()
          .setMessage("Disconnecting peer {} due to decompression failure for message code {}")
          .addArgument(ethPeer::getLoggableId)
          .addArgument(code)
          .setCause(e)
          .log();
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    } catch (final RLPException e) {
      LOG.debug(
          "Received malformed message code={} (BREACH_OF_PROTOCOL), disconnecting: {}",
          code,
          ethPeer,
          e);
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    }
    maybeResponseData.ifPresent(
        responseData -> {
          try {
            ethPeer.send(responseData, getSupportedProtocol());
          } catch (final PeerConnection.PeerNotConnected error) {
            LOG.atTrace()
                .setMessage("Peer disconnected before we could respond - nothing to do {}")
                .addArgument(error.getMessage())
                .log();
          }
        });
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {}

  @Override
  public void handleDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {}

  @Override
  public int getHighestProtocolVersion() {
    return getSupportedCapabilities().stream()
        .max(Comparator.comparing(Capability::getVersion))
        .map(Capability::getVersion)
        .orElse(0);
  }
}
