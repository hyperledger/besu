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
package tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.netty;

import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.BreachOfProtocolException;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.PeerDisconnectedException;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.UnexpectedPeerConnectionException;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.p2p.peers.LocalNode;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.Framer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.FramingException;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.HelloMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.WireMessageCodes;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.LabelledMetric;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class DeFramer extends ByteToMessageDecoder {

  private static final Logger LOG = LogManager.getLogger();

  private final CompletableFuture<PeerConnection> connectFuture;

  private final PeerConnectionEventDispatcher connectionEventDispatcher;

  private final Framer framer;
  private final LocalNode localNode;
  // The peer we are expecting to connect to, if such a peer is known
  private final Optional<Peer> expectedPeer;
  private final List<SubProtocol> subProtocols;
  private boolean hellosExchanged;
  private final LabelledMetric<Counter> outboundMessagesCounter;

  DeFramer(
      final Framer framer,
      final List<SubProtocol> subProtocols,
      final LocalNode localNode,
      final Optional<Peer> expectedPeer,
      final PeerConnectionEventDispatcher connectionEventDispatcher,
      final CompletableFuture<PeerConnection> connectFuture,
      final MetricsSystem metricsSystem) {
    this.framer = framer;
    this.subProtocols = subProtocols;
    this.localNode = localNode;
    this.expectedPeer = expectedPeer;
    this.connectFuture = connectFuture;
    this.connectionEventDispatcher = connectionEventDispatcher;
    this.outboundMessagesCounter =
        metricsSystem.createLabelledCounter(
            PantheonMetricCategory.NETWORK,
            "p2p_messages_outbound",
            "Count of each P2P message sent outbound.",
            "protocol",
            "name",
            "code");
  }

  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
    MessageData message;
    while ((message = framer.deframe(in)) != null) {

      if (hellosExchanged) {
        out.add(message);
      } else if (message.getCode() == WireMessageCodes.HELLO) {
        hellosExchanged = true;
        // Decode first hello and use the payload to modify pipeline
        final PeerInfo peerInfo;
        try {
          peerInfo = HelloMessage.readFrom(message).getPeerInfo();
        } catch (final RLPException e) {
          LOG.debug("Received invalid HELLO message", e);
          connectFuture.completeExceptionally(e);
          ctx.close();
          return;
        }
        LOG.debug("Received HELLO message: {}", peerInfo);
        if (peerInfo.getVersion() >= 5) {
          LOG.debug("Enable compression for p2pVersion: {}", peerInfo.getVersion());
          framer.enableCompression();
        }

        final CapabilityMultiplexer capabilityMultiplexer =
            new CapabilityMultiplexer(
                subProtocols,
                localNode.getPeerInfo().getCapabilities(),
                peerInfo.getCapabilities());
        final Peer peer = expectedPeer.orElse(createPeer(peerInfo, ctx));
        final PeerConnection connection =
            new NettyPeerConnection(
                ctx,
                peer,
                peerInfo,
                capabilityMultiplexer,
                connectionEventDispatcher,
                outboundMessagesCounter);

        // Check peer is who we expected
        if (expectedPeer.isPresent()
            && !Objects.equals(expectedPeer.get().getId(), peerInfo.getNodeId())) {
          String unexpectedMsg =
              String.format(
                  "Expected id %s, but got %s", expectedPeer.get().getId(), peerInfo.getNodeId());
          connectFuture.completeExceptionally(new UnexpectedPeerConnectionException(unexpectedMsg));
          connection.disconnect(DisconnectReason.UNEXPECTED_ID);
        }

        // Check that we have shared caps
        if (capabilityMultiplexer.getAgreedCapabilities().size() == 0) {
          LOG.debug(
              "Disconnecting from {} because no capabilities are shared.", peerInfo.getClientId());
          connectFuture.completeExceptionally(
              new IncompatiblePeerException("No shared capabilities"));
          connection.disconnect(DisconnectReason.USELESS_PEER);
        }

        // Setup next stage
        final AtomicBoolean waitingForPong = new AtomicBoolean(false);
        ctx.channel()
            .pipeline()
            .addLast(
                new IdleStateHandler(15, 0, 0),
                new WireKeepAlive(connection, waitingForPong),
                new ApiHandler(
                    capabilityMultiplexer, connection, connectionEventDispatcher, waitingForPong),
                new MessageFramer(capabilityMultiplexer, framer));
        connectFuture.complete(connection);
      } else if (message.getCode() == WireMessageCodes.DISCONNECT) {
        DisconnectMessage disconnectMessage = DisconnectMessage.readFrom(message);
        LOG.debug(
            "Peer disconnected before sending HELLO.  Reason: " + disconnectMessage.getReason());
        ctx.close();
        connectFuture.completeExceptionally(
            new PeerDisconnectedException(disconnectMessage.getReason()));
      } else {
        // Unexpected message - disconnect
        LOG.debug(
            "Message received before HELLO's exchanged, disconnecting.  Code: {}, Data: {}",
            message.getCode(),
            message.getData().toString());
        ctx.writeAndFlush(
                new OutboundMessage(
                    null, DisconnectMessage.create(DisconnectReason.BREACH_OF_PROTOCOL)))
            .addListener((f) -> ctx.close());
        connectFuture.completeExceptionally(
            new BreachOfProtocolException("Message received before HELLO's exchanged"));
      }
    }
  }

  private Peer createPeer(final PeerInfo peerInfo, final ChannelHandlerContext ctx) {
    final InetSocketAddress remoteAddress = ((InetSocketAddress) ctx.channel().remoteAddress());
    int port = peerInfo.getPort();
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .nodeId(peerInfo.getNodeId())
            .ipAddress(remoteAddress.getAddress())
            .listeningPort(port)
            // Discovery information is unknown, so disable it
            .disableDiscovery()
            .build());
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable throwable)
      throws Exception {
    final Throwable cause =
        throwable instanceof DecoderException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
    if (cause instanceof FramingException || cause instanceof RLPException) {
      LOG.debug("Invalid incoming message", throwable);
      if (connectFuture.isDone() && !connectFuture.isCompletedExceptionally()) {
        connectFuture.get().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
        return;
      }
    } else if (cause instanceof IOException) {
      // IO failures are routine when communicating with random peers across the network.
      LOG.debug("IO error while processing incoming message", throwable);
    } else {
      LOG.error("Exception while processing incoming message", throwable);
    }
    if (connectFuture.isDone() && !connectFuture.isCompletedExceptionally()) {
      connectFuture.get().terminateConnection(DisconnectReason.TCP_SUBSYSTEM_ERROR, false);
    } else {
      connectFuture.completeExceptionally(throwable);
      ctx.close();
    }
  }
}
