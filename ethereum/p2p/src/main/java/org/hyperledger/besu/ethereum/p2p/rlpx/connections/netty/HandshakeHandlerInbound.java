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

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.ethereum.p2p.rlpx.framing.FramerProvider;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakerProvider;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HandshakeHandlerInbound extends AbstractHandshakeHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RlpxAgent.class);

  public HandshakeHandlerInbound(
      final NodeKey nodeKey,
      final List<SubProtocol> subProtocols,
      final LocalNode localNode,
      final CompletableFuture<PeerConnection> connectionFuture,
      final PeerConnectionEventDispatcher connectionEventDispatcher,
      final MetricsSystem metricsSystem,
      final HandshakerProvider handshakerProvider,
      final FramerProvider framerProvider,
      final PeerTable peerTable) {
    super(
        subProtocols,
        localNode,
        Optional.empty(),
        connectionFuture,
        connectionEventDispatcher,
        metricsSystem,
        handshakerProvider,
        framerProvider,
        true,
        peerTable);
    handshaker.prepareResponder(nodeKey);
  }

  @Override
  protected Optional<ByteBuf> nextHandshakeMessage(
      final ByteBuf msg, final ChannelHandlerContext ctx) {
    final Optional<ByteBuf> nextMsg;
    LOG.atTrace()
        .setMessage("INBOUND: Handshake message received from {}, msg {}")
        .addArgument(ctx.channel().remoteAddress())
        .addArgument(msg)
        .log();
    if (handshaker.getStatus() == Handshaker.HandshakeStatus.IN_PROGRESS) {
      nextMsg = handshaker.handleMessage(msg);
      LOG.atTrace()
          .setMessage("INBOUND: Handshake message handled, form {}, next message {}")
          .addArgument(ctx.channel().remoteAddress())
          .addArgument(nextMsg)
          .log();
    } else {
      nextMsg = Optional.empty();
    }
    return nextMsg;
  }
}
