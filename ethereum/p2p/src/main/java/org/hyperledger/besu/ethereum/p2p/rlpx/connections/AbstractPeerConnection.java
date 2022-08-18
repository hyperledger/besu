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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.WireMessageCodes;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPeerConnection implements PeerConnection {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPeerConnection.class);

  private final Peer peer;
  private final PeerInfo peerInfo;
  private final InetSocketAddress localAddress;
  private final InetSocketAddress remoteAddress;
  private final String connectionId;
  private final CapabilityMultiplexer multiplexer;

  private final Set<Capability> agreedCapabilities;
  private final Map<String, Capability> protocolToCapability = new HashMap<>();
  private final AtomicBoolean disconnected = new AtomicBoolean(false);
  protected final PeerConnectionEventDispatcher connectionEventDispatcher;
  private final LabelledMetric<Counter> outboundMessagesCounter;

  protected AbstractPeerConnection(
      final Peer peer,
      final PeerInfo peerInfo,
      final InetSocketAddress localAddress,
      final InetSocketAddress remoteAddress,
      final String connectionId,
      final CapabilityMultiplexer multiplexer,
      final PeerConnectionEventDispatcher connectionEventDispatcher,
      final LabelledMetric<Counter> outboundMessagesCounter) {
    this.peer = peer;
    this.peerInfo = peerInfo;
    this.localAddress = localAddress;
    this.remoteAddress = remoteAddress;
    this.connectionId = connectionId;
    this.multiplexer = multiplexer;

    this.agreedCapabilities = multiplexer.getAgreedCapabilities();
    for (final Capability cap : agreedCapabilities) {
      protocolToCapability.put(cap.getName(), cap);
    }
    this.connectionEventDispatcher = connectionEventDispatcher;
    this.outboundMessagesCounter = outboundMessagesCounter;
  }

  @Override
  public void send(final Capability capability, final MessageData message) throws PeerNotConnected {
    if (isDisconnected()) {
      throw new PeerNotConnected("Attempt to send message to a closed peer connection");
    }
    doSend(capability, message);
  }

  private void doSend(final Capability capability, final MessageData message) {
    if (capability != null) {
      // Validate message is valid for this capability
      final SubProtocol subProtocol = multiplexer.subProtocol(capability);
      if (subProtocol == null
          || !subProtocol.isValidMessageCode(capability.getVersion(), message.getCode())) {
        throw new UnsupportedOperationException(
            "Attempt to send unsupported message ("
                + message.getCode()
                + ") via cap "
                + capability);
      }
      outboundMessagesCounter
          .labels(
              capability.toString(),
              subProtocol.messageName(capability.getVersion(), message.getCode()),
              Integer.toString(message.getCode()))
          .inc();
    } else {
      outboundMessagesCounter
          .labels(
              "Wire",
              WireMessageCodes.messageName(message.getCode()),
              Integer.toString(message.getCode()))
          .inc();
    }

    LOG.trace("Writing {} to {} via protocol {}", message, peerInfo, capability);
    doSendMessage(capability, message);
  }

  protected abstract void doSendMessage(final Capability capability, final MessageData message);

  @Override
  public PeerInfo getPeerInfo() {
    return peerInfo;
  }

  @Override
  public Capability capability(final String protocol) {
    return protocolToCapability.get(protocol);
  }

  @Override
  public Peer getPeer() {
    return peer;
  }

  @Override
  public Set<Capability> getAgreedCapabilities() {
    return agreedCapabilities;
  }

  @Override
  public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {
    if (disconnected.compareAndSet(false, true)) {
      connectionEventDispatcher.dispatchDisconnect(this, reason, peerInitiated);
    }
    // Always ensure the context gets closed immediately even if we previously sent a disconnect
    // message and are waiting to close.
    closeConnectionImmediately();
    LOG.debug("Terminating connection {}, reason {}", System.identityHashCode(this), reason);
  }

  protected abstract void closeConnectionImmediately();

  protected abstract void closeConnection();

  @Override
  public void disconnect(final DisconnectReason reason) {
    if (disconnected.compareAndSet(false, true)) {
      connectionEventDispatcher.dispatchDisconnect(this, reason, false);
      doSend(null, DisconnectMessage.create(reason));
      LOG.debug("Disconnecting connection {}, reason {}", System.identityHashCode(this), reason);
      closeConnection();
    }
  }

  @Override
  public boolean isDisconnected() {
    return disconnected.get();
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return localAddress;
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AbstractPeerConnection)) {
      return false;
    }
    final AbstractPeerConnection that = (AbstractPeerConnection) o;
    return Objects.equals(this.connectionId, that.connectionId)
        && Objects.equals(this.peer, that.peer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, peer);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeId", peerInfo.getNodeId())
        .add("clientId", peerInfo.getClientId())
        .add(
            "caps",
            agreedCapabilities.stream().map(Capability::toString).collect(Collectors.joining(", ")))
        .toString();
  }
}
