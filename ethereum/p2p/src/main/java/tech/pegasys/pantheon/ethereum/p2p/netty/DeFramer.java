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

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.BreachOfProtocolException;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.PeerDisconnectedException;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.Framer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.FramingException;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.HelloMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.WireMessageCodes;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;

import java.io.IOException;
import java.util.List;
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

  private final Callbacks callbacks;

  private final Framer framer;
  private final PeerInfo ourInfo;
  private final List<SubProtocol> subProtocols;
  private boolean hellosExchanged;
  private final LabelledMetric<Counter> outboundMessagesCounter;

  DeFramer(
      final Framer framer,
      final List<SubProtocol> subProtocols,
      final PeerInfo ourInfo,
      final Callbacks callbacks,
      final CompletableFuture<PeerConnection> connectFuture,
      final LabelledMetric<Counter> outboundMessagesCounter) {
    this.framer = framer;
    this.subProtocols = subProtocols;
    this.ourInfo = ourInfo;
    this.connectFuture = connectFuture;
    this.callbacks = callbacks;
    this.outboundMessagesCounter = outboundMessagesCounter;
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
                subProtocols, ourInfo.getCapabilities(), peerInfo.getCapabilities());
        final PeerConnection connection =
            new NettyPeerConnection(
                ctx, peerInfo, capabilityMultiplexer, callbacks, outboundMessagesCounter);
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
                new ApiHandler(capabilityMultiplexer, connection, callbacks, waitingForPong),
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

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable throwable)
      throws Exception {
    final Throwable cause =
        throwable instanceof DecoderException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
    if (cause instanceof FramingException) {
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
