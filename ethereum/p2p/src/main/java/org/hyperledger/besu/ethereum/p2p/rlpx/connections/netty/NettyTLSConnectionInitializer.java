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

import static org.hyperledger.besu.ethereum.p2p.rlpx.RlpxFrameConstants.LENGTH_FRAME_SIZE;
import static org.hyperledger.besu.ethereum.p2p.rlpx.RlpxFrameConstants.LENGTH_MAX_MESSAGE_FRAME;

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

import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.Channel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.ssl.SslContext;

public class NettyTLSConnectionInitializer extends NettyConnectionInitializer {

  private final Optional<Supplier<TLSContextFactory>> tlsContextFactorySupplier;

  public NettyTLSConnectionInitializer(
      final NodeKey nodeKey,
      final RlpxConfiguration config,
      final LocalNode localNode,
      final PeerConnectionEventDispatcher eventDispatcher,
      final MetricsSystem metricsSystem,
      final Optional<TLSConfiguration> p2pTLSConfiguration) {
    this(
        nodeKey,
        config,
        localNode,
        eventDispatcher,
        metricsSystem,
        defaultTlsContextFactorySupplier(p2pTLSConfiguration));
  }

  @VisibleForTesting
  NettyTLSConnectionInitializer(
      final NodeKey nodeKey,
      final RlpxConfiguration config,
      final LocalNode localNode,
      final PeerConnectionEventDispatcher eventDispatcher,
      final MetricsSystem metricsSystem,
      final Supplier<TLSContextFactory> tlsContextFactorySupplier) {
    super(nodeKey, config, localNode, eventDispatcher, metricsSystem);
    this.tlsContextFactorySupplier = Optional.ofNullable(tlsContextFactorySupplier);
  }

  @Override
  protected void addAdditionalOutboundHandlers(final Channel ch) throws GeneralSecurityException {
    if (tlsContextFactorySupplier.isPresent()) {
      final SslContext sslContext =
          tlsContextFactorySupplier.get().get().createNettyClientSslContext();
      ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
      ch.pipeline().addLast(new SnappyFrameDecoder());
      ch.pipeline().addLast(new SnappyFrameEncoder());
      ch.pipeline()
          .addLast(
              new LengthFieldBasedFrameDecoder(
                  LENGTH_MAX_MESSAGE_FRAME, 0, LENGTH_FRAME_SIZE, 0, LENGTH_FRAME_SIZE));
      ch.pipeline().addLast(new LengthFieldPrepender(LENGTH_FRAME_SIZE));
    }
  }

  @Override
  protected void addAdditionalInboundHandlers(final Channel ch) throws GeneralSecurityException {
    if (tlsContextFactorySupplier.isPresent()) {
      final SslContext sslContext =
          tlsContextFactorySupplier.get().get().createNettyServerSslContext();
      ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
      ch.pipeline().addLast(new SnappyFrameDecoder());
      ch.pipeline().addLast(new SnappyFrameEncoder());
      ch.pipeline()
          .addLast(
              new LengthFieldBasedFrameDecoder(
                  LENGTH_MAX_MESSAGE_FRAME, 0, LENGTH_FRAME_SIZE, 0, LENGTH_FRAME_SIZE));
      ch.pipeline().addLast(new LengthFieldPrepender(LENGTH_FRAME_SIZE));
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

  private static Supplier<TLSContextFactory> defaultTlsContextFactorySupplier(
      final Optional<TLSConfiguration> tlsConfiguration) {
    if (tlsConfiguration.isEmpty()) {
      throw new IllegalStateException("TLSConfiguration cannot be empty when using TLS");
    }

    return () -> {
      try {
        return TLSContextFactory.buildFrom(tlsConfiguration.get());
      } catch (final Exception e) {
        throw new RuntimeException("Error creating TLSContextFactory", e);
      }
    };
  }
}
