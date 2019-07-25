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
package tech.pegasys.pantheon.ethereum.p2p.network;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.ethereum.p2p.NetworkingTestHelper.configWithRandomPorts;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryServiceException;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;

import java.io.IOException;
import java.util.Arrays;

import io.vertx.core.Vertx;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

public class NetworkingServiceLifecycleTest {

  private final Vertx vertx = Vertx.vertx();
  private final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
  private final NetworkingConfiguration config = configWithRandomPorts();

  @After
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void createP2PNetwork() throws IOException {
    final NetworkingConfiguration config = configWithRandomPorts();
    try (final P2PNetwork service = builder().build()) {
      service.start();
      final EnodeURL enode = service.getLocalEnode().get();
      final int udpPort = enode.getDiscoveryPortOrZero();
      final int tcpPort = enode.getListeningPortOrZero();

      assertEquals(config.getDiscovery().getAdvertisedHost(), enode.getIpAsString());
      assertThat(udpPort).isNotZero();
      assertThat(tcpPort).isNotZero();
      assertThat(service.streamDiscoveredPeers()).hasSize(0);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createP2PNetwork_NullHost() throws IOException {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost(null));
    try (final P2PNetwork broken = builder().config(config).build()) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createP2PNetwork_InvalidHost() throws IOException {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindHost("fake.fake.fake"));
    try (final P2PNetwork broken = builder().config(config).build()) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void createP2PNetwork_InvalidPort() throws IOException {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setBindPort(-1));
    try (final P2PNetwork broken = builder().config(config).build()) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test(expected = NullPointerException.class)
  public void createP2PNetwork_NullKeyPair() throws IOException {
    try (final P2PNetwork broken = builder().config(config).keyPair(null).build()) {
      Assertions.fail("Expected Exception");
    }
  }

  @Test
  public void startStopP2PNetwork() throws IOException {
    try (final P2PNetwork service = builder().build()) {
      service.start();
      service.stop();
    }
  }

  @Test
  public void startDiscoveryAgentBackToBack() throws IOException {
    try (final P2PNetwork service1 = builder().build();
        final P2PNetwork service2 = builder().build()) {
      service1.start();
      service1.stop();
      service2.start();
      service2.stop();
    }
  }

  @Test
  public void startDiscoveryPortInUse() throws IOException {
    try (final P2PNetwork service1 = builder().config(config).build()) {
      service1.start();
      final NetworkingConfiguration config = configWithRandomPorts();
      final int usedPort = service1.getLocalEnode().get().getDiscoveryPortOrZero();
      assertThat(usedPort).isNotZero();
      config.getDiscovery().setBindPort(usedPort);
      try (final P2PNetwork service2 = builder().config(config).build()) {
        try {
          service2.start();
        } catch (final Exception e) {
          assertThat(e).hasCauseExactlyInstanceOf(PeerDiscoveryServiceException.class);
          assertThat(e)
              .hasMessageStartingWith(
                  "tech.pegasys.pantheon.ethereum.p2p.discovery."
                      + "PeerDiscoveryServiceException: Failed to bind Ethereum UDP discovery listener to 0.0.0.0:");
          assertThat(e).hasMessageContaining("Address already in use");
        } finally {
          service1.stop();
          service2.stop();
        }
      }
    }
  }

  @Test
  public void createP2PNetwork_NoActivePeers() throws IOException {
    try (final P2PNetwork agent = builder().build()) {
      assertTrue(agent.streamDiscoveredPeers().collect(toList()).isEmpty());
      assertEquals(0, agent.getPeers().size());
    }
  }

  private DefaultP2PNetwork.Builder builder() {
    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .keyPair(keyPair)
        .config(config)
        .metricsSystem(new NoOpMetricsSystem())
        .clock(TestClock.fixed())
        .supportedCapabilities(Arrays.asList(Capability.create("eth", 63)));
  }
}
