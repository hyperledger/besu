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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NettyTLSConnectionInitializerTest {

  @Mock private NodeKey nodeKey;
  @Mock private RlpxConfiguration rlpxConfiguration;
  @Mock private LocalNode localNode;
  @Mock private PeerConnectionEventDispatcher eventDispatcher;
  @Mock private TLSContextFactory tlsContextFactory;
  @Mock private SslContext sslContext;
  @Mock private SslHandler sslHandler;

  private NettyTLSConnectionInitializer nettyTLSConnectionInitializer;

  @Before
  public void before() throws Exception {
    nettyTLSConnectionInitializer =
        new NettyTLSConnectionInitializer(
            nodeKey,
            rlpxConfiguration,
            localNode,
            eventDispatcher,
            new NoOpMetricsSystem(),
            () -> tlsContextFactory);

    when(tlsContextFactory.createNettyServerSslContext()).thenReturn(sslContext);
    when(tlsContextFactory.createNettyClientSslContext()).thenReturn(sslContext);
    when(sslContext.newHandler(any())).thenReturn(sslHandler);
  }

  @Test
  public void addAdditionalOutboundHandlersIncludesAllExpectedHandlersToChannelPipeline()
      throws Exception {
    final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    nettyTLSConnectionInitializer.addAdditionalOutboundHandlers(embeddedChannel);

    // TLS
    assertThat(embeddedChannel.pipeline().get(SslHandler.class)).isNotNull();

    // Snappy compression
    assertThat(embeddedChannel.pipeline().get(SnappyFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(SnappyFrameEncoder.class)).isNotNull();

    // Message Framing
    assertThat(embeddedChannel.pipeline().get(LengthFieldBasedFrameDecoder.class)).isNotNull();
    assertThat(embeddedChannel.pipeline().get(LengthFieldPrepender.class)).isNotNull();

    assertHandlersOrderInPipeline(embeddedChannel.pipeline());
  }

  @Test
  public void addAdditionalInboundHandlersIncludesAllExpectedHandlersToChannelPipeline()
      throws Exception {
    final EmbeddedChannel embeddedChannel = new EmbeddedChannel();
    nettyTLSConnectionInitializer.addAdditionalInboundHandlers(embeddedChannel);

    // TLS
    assertThat(embeddedChannel.pipeline().get(SslHandler.class)).isNotNull();

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
