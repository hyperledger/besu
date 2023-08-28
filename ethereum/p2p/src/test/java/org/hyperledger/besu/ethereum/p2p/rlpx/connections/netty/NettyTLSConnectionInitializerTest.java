/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NettyTLSConnectionInitializerTest {

  private static final String PEER_HOST = "hyperledger.org";
  private static final int PEER_PORT = 30303;
  @Mock private NodeKey nodeKey;
  @Mock private RlpxConfiguration rlpxConfiguration;
  @Mock private LocalNode localNode;
  @Mock private PeerConnectionEventDispatcher eventDispatcher;
  @Mock private TLSContextFactory tlsContextFactory;
  @Mock private SslContext clientSslContext;
  @Mock private SslContext serverSslContext;
  @Mock private SslHandler clientSslHandler;
  @Mock private SslHandler serverSslHandler;
  @Mock private Peer peer;
  @Mock private EnodeURL enodeURL;

  private NettyTLSConnectionInitializer nettyTLSConnectionInitializer;

  @BeforeEach
  public void before() throws Exception {
    nettyTLSConnectionInitializer = createNettyTLSConnectionInitializer(false);

    when(tlsContextFactory.createNettyServerSslContext()).thenReturn(serverSslContext);
    when(serverSslContext.newHandler(any())).thenReturn(serverSslHandler);

    when(tlsContextFactory.createNettyClientSslContext()).thenReturn(clientSslContext);
    when(clientSslContext.newHandler(any())).thenReturn(clientSslHandler);
    when(peer.getEnodeURL()).thenReturn(enodeURL);
    when(enodeURL.getHost()).thenReturn(PEER_HOST);
    when(enodeURL.getListeningPort()).thenReturn(Optional.of(PEER_PORT));
  }

  private NettyTLSConnectionInitializer createNettyTLSConnectionInitializer(
      final boolean clientHelloSniHeaderEnabled) {
    return new NettyTLSConnectionInitializer(
        nodeKey,
        rlpxConfiguration,
        localNode,
        eventDispatcher,
        new NoOpMetricsSystem(),
        () -> tlsContextFactory,
        clientHelloSniHeaderEnabled);
  }

  @Test
  public void addAdditionalOutboundHandlersIncludesAllExpectedHandlersToChannelPipeline()
      throws Exception {
    final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    nettyTLSConnectionInitializer.addAdditionalOutboundHandlers(embeddedChannel, peer);

    // TLS
    assertThat(embeddedChannel.pipeline().get(SslHandler.class)).isEqualTo(clientSslHandler);

    // Snappy compression
    assertThat(embeddedChannel.pipeline().get(SnappyFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(SnappyFrameEncoder.class)).isNotNull();

    // Message Framing
    assertThat(embeddedChannel.pipeline().get(LengthFieldBasedFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(LengthFieldPrepender.class)).isNotNull();

    assertHandlersOrderInPipeline(embeddedChannel.pipeline());
  }

  @Test
  public void addAdditionalOutboundHandlersUsesSslHandlerWithSniHeaderEnabledIfConfigured()
      throws Exception {
    nettyTLSConnectionInitializer = createNettyTLSConnectionInitializer(true);
    when(clientSslContext.newHandler(any(), eq(PEER_HOST), eq(PEER_PORT)))
        .thenReturn(clientSslHandler);

    final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    nettyTLSConnectionInitializer.addAdditionalOutboundHandlers(embeddedChannel, peer);

    // Handler with SNI params was created
    verify(clientSslContext).newHandler(any(), eq(PEER_HOST), eq(PEER_PORT));

    // Other handlers are still present as expected
    assertHandlersOrderInPipeline(embeddedChannel.pipeline());
  }

  @Test
  public void addAdditionalInboundHandlersIncludesAllExpectedHandlersToChannelPipeline()
      throws Exception {
    final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    nettyTLSConnectionInitializer.addAdditionalInboundHandlers(embeddedChannel);

    // TLS
    assertThat(embeddedChannel.pipeline().get(SslHandler.class)).isEqualTo(serverSslHandler);

    // Snappy compression
    assertThat(embeddedChannel.pipeline().get(SnappyFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(SnappyFrameEncoder.class)).isNotNull();

    // Message Framing
    assertThat(embeddedChannel.pipeline().get(LengthFieldBasedFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(LengthFieldPrepender.class)).isNotNull();

    assertHandlersOrderInPipeline(embeddedChannel.pipeline());
  }

  private void assertHandlersOrderInPipeline(final ChannelPipeline pipeline) {
    // Appending '#0' because Netty adds it to the handler's names
    final ArrayList<String> expectedHandlerNamesInOrder =
        new ArrayList<>(
            ImmutableList.of(
                "SslHandler#0",
                "SnappyFrameDecoder#0",
                "SnappyFrameEncoder#0",
                "LengthFieldBasedFrameDecoder#0",
                "LengthFieldPrepender#0",
                "DefaultChannelPipeline$TailContext#0")); // This final handler is Netty's default

    final List<String> actualHandlerNamesInOrder = pipeline.names();
    assertThat(actualHandlerNamesInOrder).isEqualTo(expectedHandlerNamesInOrder);
  }

  @Test
  public void defaultTlsContextFactorySupplierThrowsErrorWithNullTLSConfiguration() {
    assertThatThrownBy(() -> NettyTLSConnectionInitializer.defaultTlsContextFactorySupplier(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("TLSConfiguration cannot be null when using TLS");
  }

  @Test
  public void defaultTlsContextFactorySupplierCapturesInternalError() {
    final TLSConfiguration tlsConfiguration = mock(TLSConfiguration.class);
    when(tlsConfiguration.getKeyStoreType()).thenThrow(new RuntimeException());

    assertThatThrownBy(
            () ->
                NettyTLSConnectionInitializer.defaultTlsContextFactorySupplier(tlsConfiguration)
                    .get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error creating TLSContextFactory");
  }
}
