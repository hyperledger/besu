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
      final TLSConfiguration p2pTLSConfiguration) {
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
  void addAdditionalOutboundHandlers(final Channel ch) throws GeneralSecurityException {
    if (tlsContextFactorySupplier.isPresent()) {
      final SslContext clientSslContext =
          tlsContextFactorySupplier.get().get().createNettyClientSslContext();
      addHandlersToChannelPipeline(ch, clientSslContext);
    }
  }

  @Override
  void addAdditionalInboundHandlers(final Channel ch) throws GeneralSecurityException {
    if (tlsContextFactorySupplier.isPresent()) {
      final SslContext serverSslContext =
          tlsContextFactorySupplier.get().get().createNettyServerSslContext();
      addHandlersToChannelPipeline(ch, serverSslContext);
    }
  }

  private void addHandlersToChannelPipeline(final Channel ch, final SslContext sslContext) {
    ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
    ch.pipeline().addLast(new SnappyFrameDecoder());
    ch.pipeline().addLast(new SnappyFrameEncoder());
    ch.pipeline()
        .addLast(
            new LengthFieldBasedFrameDecoder(
                LENGTH_MAX_MESSAGE_FRAME, 0, LENGTH_FRAME_SIZE, 0, LENGTH_FRAME_SIZE));
    ch.pipeline().addLast(new LengthFieldPrepender(LENGTH_FRAME_SIZE));
  }

  @Override
  public Handshaker buildInstance() {
    return new PlainHandshaker();
  }

  @Override
  public Framer buildFramer(final HandshakeSecrets secrets) {
    return new PlainFramer();
  }

  @VisibleForTesting
  static Supplier<TLSContextFactory> defaultTlsContextFactorySupplier(
      final TLSConfiguration tlsConfiguration) {
    if (tlsConfiguration == null) {
      throw new IllegalStateException("TLSConfiguration cannot be null when using TLS");
    }

    return () -> {
      try {
        return TLSContextFactory.buildFrom(tlsConfiguration);
      } catch (final Exception e) {
        throw new IllegalStateException("Error creating TLSContextFactory", e);
      }
    };
  }
}
