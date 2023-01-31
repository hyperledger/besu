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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import static java.util.concurrent.TimeUnit.SECONDS;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.AbstractPeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

final class NettyPeerConnection extends AbstractPeerConnection {

  private final ChannelHandlerContext ctx;

  public NettyPeerConnection(
      final ChannelHandlerContext ctx,
      final Peer peer,
      final PeerInfo peerInfo,
      final CapabilityMultiplexer multiplexer,
      final PeerConnectionEventDispatcher connectionEventDispatcher,
      final LabelledMetric<Counter> outboundMessagesCounter,
      final boolean inboundInitiated) {
    super(
        peer,
        peerInfo,
        (InetSocketAddress) ctx.channel().localAddress(),
        (InetSocketAddress) ctx.channel().remoteAddress(),
        ctx.channel().id().asLongText(),
        multiplexer,
        connectionEventDispatcher,
        outboundMessagesCounter,
        inboundInitiated);

    this.ctx = ctx;
    ctx.channel()
        .closeFuture()
        .addListener(
            f ->
                terminateConnection(DisconnectMessage.DisconnectReason.TCP_SUBSYSTEM_ERROR, false));
  }

  @Override
  protected void doSendMessage(final Capability capability, final MessageData message) {
    ctx.channel().writeAndFlush(new OutboundMessage(capability, message));
  }

  @Override
  protected void closeConnectionImmediately() {
    ctx.close();
  }

  @Override
  protected void closeConnection() {
    ctx.channel().eventLoop().schedule((Callable<ChannelFuture>) ctx::close, 2L, SECONDS);
  }
}
