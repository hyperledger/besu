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

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.plain.PlainFramer;
import org.hyperledger.besu.ethereum.p2p.plain.PlainHandshaker;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.ethereum.p2p.rlpx.framing.Framer;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeSecrets;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

public class NettyTLSConnectionInitializer extends NettyConnectionInitializer {

  private final Optional<TLSConfiguration> p2pTLSConfiguration;

  public NettyTLSConnectionInitializer(
      final NodeKey nodeKey,
      final RlpxConfiguration config,
      final LocalNode localNode,
      final PeerConnectionEventDispatcher eventDispatcher,
      final MetricsSystem metricsSystem,
      final Optional<TLSConfiguration> p2pTLSConfiguration) {
    super(nodeKey, config, localNode, eventDispatcher, metricsSystem);
    this.p2pTLSConfiguration = p2pTLSConfiguration;
  }

  @Override
  protected void addAdditionalOutboundHandlers(final SocketChannel ch)
      throws GeneralSecurityException, IOException {
    if (p2pTLSConfiguration.isPresent()) {
      SslContext sslContext =
          TLSContextFactory.buildFrom(p2pTLSConfiguration.get()).createNettyClientSslContext();
      ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
    }
  }

  @Override
  protected void addAdditionalInboundHandlers(final SocketChannel ch)
      throws GeneralSecurityException, IOException {
    if (p2pTLSConfiguration.isPresent()) {
      SslContext sslContext =
          TLSContextFactory.buildFrom(p2pTLSConfiguration.get()).createNettyServerSslContext();
      ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
    }
  }

  @Override
  public Handshaker buildInstance() {
    return new PlainHandshaker();
  }

  @Override
  public Framer buildFramer(final HandshakeSecrets secrets) {
    return new PlainFramer();
  }
}
