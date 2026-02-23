/*
 * Copyright contributors to Besu.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.ConnectionInitializer.ListeningAddresses;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEventDispatcher;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerLookup;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.NetworkUtility;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class NettyConnectionInitializerTest {

  private NettyConnectionInitializer initializer;

  @AfterEach
  public void tearDown() throws Exception {
    if (initializer != null) {
      initializer.stop().get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void start_bindsIpv4Address() throws Exception {
    initializer = createInitializer(ipv4OnlyConfig());

    final ListeningAddresses addrs = initializer.start().get(10, TimeUnit.SECONDS);

    assertThat(addrs.ipv4Address().getPort()).isGreaterThan(0);
    assertThat(addrs.ipv6Address()).isEmpty();

    // Verify the IPv4 socket actually accepts TCP connections
    try (var conn = new Socket(addrs.ipv4Address().getAddress(), addrs.ipv4Address().getPort())) {
      assertThat(conn.isConnected()).isTrue();
    }
  }

  @Test
  public void start_dualStack_bindsBothAddresses() throws Exception {
    assumeTrue(NetworkUtility.isIPv6Available(), "IPv6 not available on this host");

    initializer = createInitializer(dualStackConfig());

    final ListeningAddresses addrs = initializer.start().get(10, TimeUnit.SECONDS);

    assertThat(addrs.ipv4Address().getPort()).isGreaterThan(0);
    assertThat(addrs.ipv6Address()).isPresent();
    assertThat(addrs.ipv6Address().get().getPort()).isGreaterThan(0);

    // Verify the IPv4 socket actually accepts TCP connections
    try (var conn = new Socket(addrs.ipv4Address().getAddress(), addrs.ipv4Address().getPort())) {
      assertThat(conn.isConnected()).isTrue();
    }

    // Verify the IPv6 socket actually accepts TCP connections
    final InetSocketAddress ipv6Addr = addrs.ipv6Address().get();
    try (var conn = new Socket(ipv6Addr.getAddress(), ipv6Addr.getPort())) {
      assertThat(conn.isConnected()).isTrue();
    }
  }

  @Test
  public void start_dualStack_ipv4AndIpv6PortsAreIndependent() throws Exception {
    assumeTrue(NetworkUtility.isIPv6Available(), "IPv6 not available on this host");

    initializer = createInitializer(dualStackConfig());

    final ListeningAddresses addrs = initializer.start().get(10, TimeUnit.SECONDS);

    // Both sockets are bound with ephemeral ports — they should differ
    assertThat(addrs.ipv4Address().getPort()).isNotEqualTo(addrs.ipv6Address().get().getPort());
  }

  private static RlpxConfiguration ipv4OnlyConfig() {
    return RlpxConfiguration.create().setBindHost("127.0.0.1").setBindPort(0);
  }

  private static RlpxConfiguration dualStackConfig() {
    return RlpxConfiguration.create()
        .setBindHost("127.0.0.1")
        .setBindPort(0)
        .setBindHostIpv6(Optional.of("::1"))
        .setBindPortIpv6(Optional.of(0));
  }

  private static NettyConnectionInitializer createInitializer(final RlpxConfiguration config) {
    return new NettyConnectionInitializer(
        NodeKeyUtils.generate(),
        config,
        mock(LocalNode.class),
        mock(PeerConnectionEventDispatcher.class),
        new NoOpMetricsSystem(),
        new PeerLookup());
  }
}
