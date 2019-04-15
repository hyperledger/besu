/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.netty;

import static java.util.concurrent.TimeUnit.SECONDS;
import static tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason.TCP_SUBSYSTEM_ERROR;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.WireMessageCodes;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.common.base.MoreObjects;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class NettyPeerConnection implements PeerConnection {

  private static final Logger LOG = LogManager.getLogger();

  private final ChannelHandlerContext ctx;
  private final PeerInfo peerInfo;
  private final Set<Capability> agreedCapabilities;
  private final Map<String, Capability> protocolToCapability = new HashMap<>();
  private final AtomicBoolean disconnectDispatched = new AtomicBoolean(false);
  private final AtomicBoolean disconnected = new AtomicBoolean(false);
  private final Callbacks callbacks;
  private final CapabilityMultiplexer multiplexer;
  private final LabelledMetric<Counter> outboundMessagesCounter;

  public NettyPeerConnection(
      final ChannelHandlerContext ctx,
      final PeerInfo peerInfo,
      final CapabilityMultiplexer multiplexer,
      final Callbacks callbacks,
      final LabelledMetric<Counter> outboundMessagesCounter) {
    this.ctx = ctx;
    this.peerInfo = peerInfo;
    this.multiplexer = multiplexer;
    this.agreedCapabilities = multiplexer.getAgreedCapabilities();
    for (final Capability cap : agreedCapabilities) {
      protocolToCapability.put(cap.getName(), cap);
    }
    this.callbacks = callbacks;
    this.outboundMessagesCounter = outboundMessagesCounter;
    ctx.channel().closeFuture().addListener(f -> terminateConnection(TCP_SUBSYSTEM_ERROR, false));
  }

  @Override
  public void send(final Capability capability, final MessageData message) throws PeerNotConnected {
    if (isDisconnected()) {
      throw new PeerNotConnected("Attempt to send message to a closed peer connection");
    }
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
    ctx.channel().writeAndFlush(new OutboundMessage(capability, message));
  }

  @Override
  public PeerInfo getPeerInfo() {
    return peerInfo;
  }

  @Override
  public Capability capability(final String protocol) {
    return protocolToCapability.get(protocol);
  }

  @Override
  public Set<Capability> getAgreedCapabilities() {
    return agreedCapabilities;
  }

  @Override
  public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {
    if (disconnectDispatched.compareAndSet(false, true)) {
      LOG.debug("Disconnected ({}) from {}", reason, peerInfo);
      callbacks.invokeDisconnect(this, reason, peerInitiated);
      disconnected.set(true);
    }
    // Always ensure the context gets closed immediately even if we previously sent a disconnect
    // message and are waiting to close.
    ctx.close();
  }

  @Override
  public void disconnect(final DisconnectReason reason) {
    if (disconnectDispatched.compareAndSet(false, true)) {
      LOG.debug("Disconnecting ({}) from {}", reason, peerInfo);
      callbacks.invokeDisconnect(this, reason, false);
      try {
        send(null, DisconnectMessage.create(reason));
      } catch (final PeerNotConnected e) {
        // The connection has already been closed - nothing left to do
        return;
      }
      disconnected.set(true);
      ctx.channel().eventLoop().schedule((Callable<ChannelFuture>) ctx::close, 2L, SECONDS);
    }
  }

  @Override
  public boolean isDisconnected() {
    return disconnected.get();
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return (InetSocketAddress) ctx.channel().localAddress();
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) ctx.channel().remoteAddress();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("clientId", peerInfo.getClientId())
        .add("nodeId", peerInfo.getNodeId())
        .add(
            "caps",
            agreedCapabilities.stream().map(Capability::toString).collect(Collectors.joining(", ")))
        .toString();
  }
}
