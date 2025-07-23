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
package org.hyperledger.besu.services;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.plugin.data.p2p.Capability;
import org.hyperledger.besu.plugin.data.p2p.Message;
import org.hyperledger.besu.plugin.data.p2p.MessageData;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.p2p.P2PMessageService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.p2p.ProtocolManagerService;
import org.hyperledger.besu.plugin.services.p2p.ValidatorNetworkService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service to enable and disable P2P discovery. */
public class P2PServiceImpl
    implements P2PService, P2PMessageService, ValidatorNetworkService, ProtocolManagerService {

  private static final Logger LOG = LoggerFactory.getLogger(P2PServiceImpl.class);

  private final P2PNetwork p2PNetwork;
  private final Optional<ValidatorProvider> validatorProvider;
  private final Map<String, Consumer<Message>> messageHandlers = new ConcurrentHashMap<>();
  private final Map<String, ProtocolManagerService.ProtocolManager> protocolManagers =
      new ConcurrentHashMap<>();
  private final Map<ValidatorNetworkService.ValidatorConnectionTracker, Object> connectionTrackers =
      new ConcurrentHashMap<>();

  /**
   * Creates a new P2PServiceImpl.
   *
   * @param p2PNetwork the P2P network to enable and disable.
   */
  public P2PServiceImpl(final P2PNetwork p2PNetwork) {
    this(p2PNetwork, Optional.empty());
  }

  /**
   * Creates a new P2PServiceImpl with validator support.
   *
   * @param p2PNetwork the P2P network to enable and disable.
   * @param validatorProvider the validator provider for consensus support
   */
  public P2PServiceImpl(
      final P2PNetwork p2PNetwork, final Optional<ValidatorProvider> validatorProvider) {
    this.p2PNetwork = p2PNetwork;
    this.validatorProvider = validatorProvider;
  }

  /** Enables P2P discovery. */
  @Override
  public void enableDiscovery() {
    p2PNetwork.start();
  }

  @Override
  public void disableDiscovery() {
    p2PNetwork.stop();
  }

  @Override
  public void registerMessageHandler(
      final String protocolName, final Consumer<Message> messageHandler) {
    messageHandlers.put(protocolName, messageHandler);
  }

  @Override
  public void unregisterMessageHandler(final String protocolName) {
    messageHandlers.remove(protocolName);
  }

  @Override
  public void sendMessage(
      final PeerConnection peerConnection, final Capability capability, final MessageData message)
      throws PeerConnection.PeerNotConnected {
    try {
      final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection corePeerConnection =
          getCorePeerConnection(peerConnection);
      final org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability coreCapability =
          toCoreCapability(capability);
      final org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData coreMessage =
          toCoreMessageData(message);

      corePeerConnection.send(coreCapability, coreMessage);
    } catch (
        final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected
            e) {
      throw new PeerConnection.PeerNotConnected(e.getMessage());
    }
  }

  @Override
  public void sendMessageForProtocol(
      final PeerConnection peerConnection, final String protocolName, final MessageData message)
      throws PeerConnection.PeerNotConnected {
    try {
      final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection corePeerConnection =
          getCorePeerConnection(peerConnection);
      final org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData coreMessage =
          toCoreMessageData(message);

      corePeerConnection.sendForProtocol(protocolName, coreMessage);
    } catch (
        final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected
            e) {
      throw new PeerConnection.PeerNotConnected(e.getMessage());
    }
  }

  @Override
  public List<PeerConnection> getPeerConnections() {
    return p2PNetwork.getPeers().stream()
        .map(this::toPluginPeerConnection)
        .collect(Collectors.toList());
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    // Get capabilities from registered protocol managers or P2P network's local node
    return protocolManagers.values().stream()
        .flatMap(pm -> pm.getSupportedCapabilities().stream())
        .collect(Collectors.toList());
  }

  @Override
  public void sendToValidators(final MessageData message) {
    sendToValidators(message, List.of());
  }

  @Override
  public void sendToValidators(final MessageData message, final Collection<Address> denylist) {
    if (validatorProvider.isEmpty()) {
      LOG.warn("Cannot send to validators: no validator provider configured");
      return;
    }

    final Collection<Address> validators = validatorProvider.get().getValidatorsAtHead();
    final Set<Address> denySet = Set.copyOf(denylist);

    getPeerConnections().stream()
        .filter(
            peer -> {
              final Address peerAddress = peer.getPeerInfo().getAddress();
              return validators.contains(peerAddress) && !denySet.contains(peerAddress);
            })
        .forEach(
            peer -> {
              try {
                // Find a suitable protocol for sending the message
                final Optional<String> protocol = findSuitableProtocol(peer);
                if (protocol.isPresent()) {
                  sendMessageForProtocol(peer, protocol.get(), message);
                } else {
                  LOG.debug(
                      "No suitable protocol found for validator peer {}",
                      peer.getPeerInfo().getAddress());
                }
              } catch (final PeerConnection.PeerNotConnected e) {
                LOG.debug("Failed to send message to validator peer: {}", e.getMessage());
              }
            });
  }

  @Override
  public List<PeerConnection> getValidatorPeerConnections() {
    if (validatorProvider.isEmpty()) {
      return List.of();
    }

    final Collection<Address> validators = validatorProvider.get().getValidatorsAtHead();
    return getPeerConnections().stream()
        .filter(peer -> validators.contains(peer.getPeerInfo().getAddress()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Address> getValidatorAddresses() {
    return validatorProvider
        .map(provider -> List.copyOf(provider.getValidatorsAtHead()))
        .orElse(List.of());
  }

  @Override
  public boolean isValidator(final PeerConnection peerConnection) {
    return isValidator(peerConnection.getPeerInfo().getAddress());
  }

  @Override
  public boolean isValidator(final Address address) {
    return validatorProvider
        .map(provider -> provider.getValidatorsAtHead().contains(address))
        .orElse(false);
  }

  @Override
  public void registerConnectionTracker(
      final ValidatorNetworkService.ValidatorConnectionTracker tracker) {
    connectionTrackers.put(tracker, new Object());
  }

  @Override
  public void unregisterConnectionTracker(
      final ValidatorNetworkService.ValidatorConnectionTracker tracker) {
    connectionTrackers.remove(tracker);
  }

  @Override
  public void registerProtocolManager(
      final String protocolName, final ProtocolManagerService.ProtocolManager protocolManager) {
    protocolManagers.put(protocolName, protocolManager);
  }

  @Override
  public void unregisterProtocolManager(final String protocolName) {
    protocolManagers.remove(protocolName);
  }

  @Override
  public List<String> getRegisteredProtocols() {
    return List.copyOf(protocolManagers.keySet());
  }

  @Override
  public boolean isProtocolRegistered(final String protocolName) {
    return protocolManagers.containsKey(protocolName);
  }

  // Helper methods for type conversion
  private org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection getCorePeerConnection(
      final PeerConnection pluginPeerConnection) {
    // This would need to be implemented based on how plugin peer connections wrap core connections
    // For now, assume the plugin peer connection has a way to access the underlying core connection
    if (pluginPeerConnection instanceof P2PServiceImpl.PluginPeerConnectionWrapper) {
      return ((PluginPeerConnectionWrapper) pluginPeerConnection).getCorePeerConnection();
    }
    throw new IllegalArgumentException(
        "Unknown plugin peer connection type: " + pluginPeerConnection.getClass());
  }

  private PeerConnection toPluginPeerConnection(
      final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection corePeerConnection) {
    return new PluginPeerConnectionWrapper(corePeerConnection);
  }

  private org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability toCoreCapability(
      final Capability pluginCapability) {
    return org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability.create(
        pluginCapability.getName(), pluginCapability.getVersion());
  }

  private org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData toCoreMessageData(
      final MessageData pluginMessage) {
    if (pluginMessage instanceof PluginMessageDataWrapper) {
      return ((PluginMessageDataWrapper) pluginMessage).getCoreMessageData();
    }
    // Create adapter for external plugin message data
    return new CoreMessageDataAdapter(pluginMessage);
  }

  private Optional<String> findSuitableProtocol(final PeerConnection peerConnection) {
    // Find the first registered protocol that the peer supports
    return protocolManagers.keySet().stream()
        .filter(
            protocolName ->
                peerConnection.getAgreedCapabilities().stream()
                    .anyMatch(cap -> cap.getName().equals(protocolName)))
        .findFirst();
  }

  // Wrapper classes for type conversion
  private static class PluginPeerConnectionWrapper implements PeerConnection {
    private final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection
        corePeerConnection;

    public PluginPeerConnectionWrapper(
        final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection
            corePeerConnection) {
      this.corePeerConnection = corePeerConnection;
    }

    public org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection
        getCorePeerConnection() {
      return corePeerConnection;
    }

    @Override
    public void send(final Capability capability, final MessageData message)
        throws PeerNotConnected {
      try {
        final org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability coreCapability =
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability.create(
                capability.getName(), capability.getVersion());
        final org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData coreMessage =
            new CoreMessageDataAdapter(message);
        corePeerConnection.send(coreCapability, coreMessage);
      } catch (
          final org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected
              e) {
        throw new PeerNotConnected(e.getMessage());
      }
    }

    @Override
    public Set<Capability> getAgreedCapabilities() {
      return corePeerConnection.getAgreedCapabilities().stream()
          .map(PluginCapabilityWrapper::new)
          .collect(Collectors.toSet());
    }

    @Override
    public org.hyperledger.besu.plugin.data.p2p.Peer getPeer() {
      return new PluginPeerWrapper(corePeerConnection.getPeer());
    }

    @Override
    public org.hyperledger.besu.plugin.data.p2p.PeerInfo getPeerInfo() {
      return new PluginPeerInfoWrapper(corePeerConnection.getPeerInfo());
    }

    @Override
    public void terminateConnection(
        final org.hyperledger.besu.plugin.data.p2p.DisconnectReason reason,
        final boolean peerInitiated) {
      final org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
          coreReason = toCoreDisconnectReason(reason);
      corePeerConnection.terminateConnection(coreReason, peerInitiated);
    }

    @Override
    public void disconnect(final org.hyperledger.besu.plugin.data.p2p.DisconnectReason reason) {
      final org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
          coreReason = toCoreDisconnectReason(reason);
      corePeerConnection.disconnect(coreReason);
    }

    @Override
    public boolean isDisconnected() {
      return corePeerConnection.isDisconnected();
    }

    @Override
    public java.net.InetSocketAddress getLocalAddress() {
      return corePeerConnection.getLocalAddress();
    }

    @Override
    public java.net.InetSocketAddress getRemoteAddress() {
      return corePeerConnection.getRemoteAddress();
    }

    @Override
    public long getInitiatedAt() {
      return corePeerConnection.getInitiatedAt();
    }

    @Override
    public boolean inboundInitiated() {
      return corePeerConnection.inboundInitiated();
    }

    @Override
    public void setStatusSent() {
      corePeerConnection.setStatusSent();
    }

    @Override
    public void setStatusReceived() {
      corePeerConnection.setStatusReceived();
    }

    @Override
    public boolean getStatusExchanged() {
      return corePeerConnection.getStatusExchanged();
    }

    private org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
        toCoreDisconnectReason(
            final org.hyperledger.besu.plugin.data.p2p.DisconnectReason pluginReason) {
      return switch (pluginReason) {
        case CLIENT_QUITTING ->
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
                .CLIENT_QUITTING;
        case NETWORK_IDLE ->
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
                .TIMEOUT;
        case TOO_MANY_PEERS ->
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
                .TOO_MANY_PEERS;
        case ALREADY_CONNECTED ->
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
                .ALREADY_CONNECTED;
        default ->
            org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason
                .UNKNOWN;
      };
    }
  }

  private static class PluginCapabilityWrapper implements Capability {
    private final org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability coreCapability;

    public PluginCapabilityWrapper(
        final org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability coreCapability) {
      this.coreCapability = coreCapability;
    }

    @Override
    public String getName() {
      return coreCapability.getName();
    }

    @Override
    public int getVersion() {
      return coreCapability.getVersion();
    }
  }

  private static class PluginPeerWrapper implements org.hyperledger.besu.plugin.data.p2p.Peer {
    private final org.hyperledger.besu.ethereum.p2p.peers.Peer corePeer;

    public PluginPeerWrapper(final org.hyperledger.besu.ethereum.p2p.peers.Peer corePeer) {
      this.corePeer = corePeer;
    }

    @Override
    public String getEnodeURL() {
      return corePeer.getEnodeURL().toString();
    }

    @Override
    public Address getAddress() {
      // Extract address from enode URL's node ID using the proper utility
      final org.hyperledger.besu.crypto.SECPPublicKey publicKey =
          org.hyperledger.besu.crypto.SignatureAlgorithmFactory.getInstance()
              .createPublicKey(corePeer.getEnodeURL().getNodeId());
      return org.hyperledger.besu.ethereum.core.Util.publicKeyToAddress(publicKey);
    }
  }

  private static class PluginPeerInfoWrapper
      implements org.hyperledger.besu.plugin.data.p2p.PeerInfo {
    private final org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo corePeerInfo;

    public PluginPeerInfoWrapper(
        final org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo corePeerInfo) {
      this.corePeerInfo = corePeerInfo;
    }

    @Override
    public Address getAddress() {
      return corePeerInfo.getAddress();
    }
  }

  private static class PluginMessageDataWrapper implements MessageData {
    private final org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData coreMessageData;

    @SuppressWarnings("unused")
    public PluginMessageDataWrapper(
        final org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData coreMessageData) {
      this.coreMessageData = coreMessageData;
    }

    public org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData getCoreMessageData() {
      return coreMessageData;
    }

    @Override
    public int getSize() {
      return coreMessageData.getSize();
    }

    @Override
    public int getCode() {
      return coreMessageData.getCode();
    }

    @Override
    public org.apache.tuweni.bytes.Bytes getData() {
      return coreMessageData.getData();
    }

    @Override
    public MessageData wrapMessageData(final java.math.BigInteger requestId) {
      // For now, return this instance as wrapping is not commonly used
      return this;
    }

    @Override
    public java.util.Map.Entry<java.math.BigInteger, MessageData> unwrapMessageData() {
      // For now, return a default entry as unwrapping is not commonly used
      return new java.util.AbstractMap.SimpleEntry<>(java.math.BigInteger.ZERO, this);
    }
  }

  private static class CoreMessageDataAdapter
      implements org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData {
    private final MessageData pluginMessageData;

    public CoreMessageDataAdapter(final MessageData pluginMessageData) {
      this.pluginMessageData = pluginMessageData;
    }

    @Override
    public int getSize() {
      return pluginMessageData.getSize();
    }

    @Override
    public int getCode() {
      return pluginMessageData.getCode();
    }

    @Override
    public org.apache.tuweni.bytes.Bytes getData() {
      return pluginMessageData.getData();
    }
  }
}
