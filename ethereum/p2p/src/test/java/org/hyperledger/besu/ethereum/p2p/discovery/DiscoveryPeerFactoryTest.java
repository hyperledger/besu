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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.p2p.discovery.discv5.DiscV5TestHelper;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.EthereumNodeRecord;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveryPeerFactoryTest {

  private NodeKey nodeKey;

  @BeforeEach
  void setUp() {
    nodeKey = NodeKeyUtils.generate();
  }

  @Test
  void fromNodeRecordCreatesCorrectPeer() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecord(nodeKey, "10.0.0.1", 30303, 30303);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("10.0.0.1");
    assertThat(peer.getEnodeURL().getListeningPort()).hasValue(30303);
    assertThat(peer.getEnodeURL().getDiscoveryPort()).hasValue(30303);
  }

  @Test
  void fromNodeRecordSetsNodeRecordOnPeer() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecord(nodeKey, "10.0.0.1", 30303, 30303);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.getNodeRecord()).isPresent();
    assertThat(peer.getNodeRecord().get()).isSameAs(record);
  }

  @Test
  void fromNodeRecordPreferIpv4WhenBothAvailable() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecord(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304, 30304);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("10.0.0.1");
    assertThat(peer.getEnodeURL().getListeningPort()).hasValue(30303);
  }

  @Test
  void fromNodeRecordPreferIpv6WhenBothAvailable() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecord(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304, 30304);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, true);

    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("fd00:0:0:0:0:0:0:1");
    assertThat(peer.getEnodeURL().getListeningPort()).hasValue(30304);
  }

  @Test
  void fromNodeRecordIpv6OnlyUsesIpv6() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordIpv6(nodeKey, "fd00::2", 30305, 30305);
    // Even with preferIpv6=false, IPv6-only record must use IPv6
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("fd00:0:0:0:0:0:0:2");
    assertThat(peer.getEnodeURL().getListeningPort()).hasValue(30305);
  }

  @Test
  void fromNodeRecordNoTcpPortPeerNotListening() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordNoTcp(nodeKey, "10.0.0.1", 30303);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.isListening()).isFalse();
  }

  @Test
  void fromNodeRecordExtractsForkId() {
    final List<Bytes> forkId = List.of(Bytes.fromHexString("0xfc64ec04"), Bytes.EMPTY);
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordWithForkId(
            nodeKey, "10.0.0.1", 30303, 30303, forkId);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    assertThat(peer.getForkId()).isPresent();
    assertThat(peer.getForkId().get().getHash()).isEqualTo(Bytes.fromHexString("0xfc64ec04"));
  }

  @Test
  void fromEthereumNodeRecordDefaultDoesNotPreferIpv6() {
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecord(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304, 30304);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    // Single-arg overload defaults to preferIpv6=false
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromEthereumNodeRecord(enr);

    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("10.0.0.1");
  }

  @Test
  void fromNodeRecordDualStackIpv6FallbackUdp() {
    // Dual-stack with no udp6 but udp present — IPv6 path falls back.
    // EthereumNodeRecord.initUDPV6 falls back to tcp6 when udp6 is absent.
    // Then buildEnodeUrl prefers udpV6 (which resolved to tcp6=30304), falling back to udp.
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecordNoUdp6(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, true);

    // Should use IPv6 address
    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("fd00:0:0:0:0:0:0:1");
    // udpV6 falls back to tcp6 (30304) per EthereumNodeRecord.initUDPV6
    assertThat(peer.getEnodeURL().getDiscoveryPort()).hasValue(30304);
  }

  @Test
  void fromNodeRecordUdpFallsBackToTcp() {
    // No explicit UDP field — ENR spec says fallback to TCP port for discovery
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordTcpOnly(nodeKey, "10.0.0.1", 30303);
    final DiscoveryPeer peer = DiscoveryPeerFactory.fromNodeRecord(record, false);

    // UDP falls back to TCP
    assertThat(peer.getEnodeURL().getDiscoveryPort()).hasValue(30303);
  }
}
