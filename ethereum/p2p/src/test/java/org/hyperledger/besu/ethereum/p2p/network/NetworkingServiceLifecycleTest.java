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
package org.hyperledger.besu.ethereum.p2p.network;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.p2p.NetworkingTestHelper.configWithRandomPorts;
import static org.junit.jupiter.api.Assumptions.assumingThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryServiceException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import io.vertx.core.Vertx;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class NetworkingServiceLifecycleTest {

  private final Vertx vertx = Vertx.vertx();
  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final NetworkingConfiguration config = configWithRandomPorts();

  @AfterEach
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void createP2PNetwork() throws IOException {
    final NetworkingConfiguration config = configWithRandomPorts();
    final DefaultP2PNetwork.Builder builder = getP2PNetworkBuilder();
    try (final P2PNetwork service = builder.build()) {
      service.start();
      final EnodeURL enode = service.getLocalEnode().get();
      final int udpPort = enode.getDiscoveryPortOrZero();
      final int tcpPort = enode.getListeningPortOrZero();

      assertThat(enode.getIpAsString()).isEqualTo(config.getDiscovery().getAdvertisedHost());
      assertThat(udpPort).isNotZero();
      assertThat(tcpPort).isNotZero();
      assertThat(service.streamDiscoveredPeers()).hasSize(0);
    }
  }

  @Nonnull
  private DefaultP2PNetwork.Builder getP2PNetworkBuilder() {
    final DefaultP2PNetwork.Builder builder = builder();
    final MutableBlockchain blockchainMock = mock(MutableBlockchain.class);
    final Block blockMock = mock(Block.class);
    when(blockMock.getHash()).thenReturn(Hash.ZERO);
    when(blockchainMock.getGenesisBlock()).thenReturn(blockMock);
    builder
        .blockchain(blockchainMock)
        .blockNumberForks(Collections.emptyList())
        .timestampForks(Collections.emptyList())
        .allConnectionsSupplier(Stream::empty)
        .allActiveConnectionsSupplier(Stream::empty);
    return builder;
  }

  @Test
  public void createP2PNetwork_NullHost() {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost(null));
    final DefaultP2PNetwork.Builder p2pNetworkBuilder = getP2PNetworkBuilder().config(config);
    assertThatThrownBy(
            () -> {
              try (final P2PNetwork ignored = p2pNetworkBuilder.build()) {
                Assertions.fail("Expected Exception");
              }
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createP2PNetwork_InvalidHost() {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost("fake.fake.fake"));
    final DefaultP2PNetwork.Builder p2pNetworkBuilder = getP2PNetworkBuilder().config(config);
    assertThatThrownBy(
            () -> {
              try (final P2PNetwork ignored = p2pNetworkBuilder.build()) {
                Assertions.fail("Expected Exception");
              }
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createP2PNetwork_InvalidPort() {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindPort(-1));
    final DefaultP2PNetwork.Builder p2pNetworkBuilder = getP2PNetworkBuilder().config(config);
    assertThatThrownBy(
            () -> {
              try (final P2PNetwork ignored = p2pNetworkBuilder.build()) {
                Assertions.fail("Expected Exception");
              }
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createP2PNetwork_NullKeyPair() {
    final DefaultP2PNetwork.Builder p2pNetworkBuilder = builder().config(config);
    assertThatThrownBy(() -> p2pNetworkBuilder.nodeKey(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void startStopP2PNetwork() throws IOException {
    try (final P2PNetwork service = getP2PNetworkBuilder().build()) {
      service.start();
      service.stop();
    }
  }

  @Test
  public void startDiscoveryAgentBackToBack() throws IOException {
    try (final P2PNetwork service1 = getP2PNetworkBuilder().build();
        final P2PNetwork service2 = getP2PNetworkBuilder().build()) {
      service1.start();
      service1.stop();
      service2.start();
      service2.stop();
    }
  }

  @Test
  public void startDiscoveryPortInUse() {
    assumingThat(
        System.getProperty("user.language").startsWith("en"),
        () -> {
          try (final P2PNetwork service1 = getP2PNetworkBuilder().config(config).build()) {
            service1.start();
            final NetworkingConfiguration config = configWithRandomPorts();
            final int usedPort = service1.getLocalEnode().get().getDiscoveryPortOrZero();
            assertThat(usedPort).isNotZero();
            config.getDiscovery().setBindPort(usedPort);
            try (final P2PNetwork service2 = getP2PNetworkBuilder().config(config).build()) {
              try {
                service2.start();
              } catch (final Exception e) {
                assertThat(e).hasCauseExactlyInstanceOf(PeerDiscoveryServiceException.class);
                assertThat(e)
                    .hasMessageStartingWith(
                        "org.hyperledger.besu.ethereum.p2p.discovery."
                            + "PeerDiscoveryServiceException: Failed to bind Ethereum UDP discovery listener to 0.0.0.0:");
                assertThat(e).hasMessageContaining("Address already in use");
              } finally {
                service1.stop();
                service2.stop();
              }
            }
          }
        });
  }

  @Test
  public void createP2PNetwork_NoActivePeers() throws IOException {
    try (final P2PNetwork agent = getP2PNetworkBuilder().build()) {
      assertThat(agent.streamDiscoveredPeers().collect(toList())).isEmpty();
      assertThat(agent.getPeers()).isEmpty();
    }
  }

  private DefaultP2PNetwork.Builder builder() {
    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .nodeKey(nodeKey)
        .config(config)
        .metricsSystem(new NoOpMetricsSystem())
        .supportedCapabilities(Arrays.asList(Capability.create("eth", 63)))
        .storageProvider(new InMemoryKeyValueStorageProvider());
  }
}
