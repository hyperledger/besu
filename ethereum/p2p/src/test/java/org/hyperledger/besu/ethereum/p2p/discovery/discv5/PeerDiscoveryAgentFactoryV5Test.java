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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.ImmutableNetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.discv4.internal.DiscoveryPeerV4;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeerDiscoveryAgentFactoryV5Test {

  // ENR with IP 15.204.180.57, TCP port 30303, UDP port 30303
  private static final String TEST_ENR =
      "enr:-KO4QK1ecw-CGrDDZ4YwFrhgqctD0tWMHKJhUVxsS4um3aUFe3yBHRtVL9uYKk16DurN1IdSKTOB1zNCvjBybjZ_KAq"
          + "GAYtJ5U8wg2V0aMfGhJsZKtCAgmlkgnY0gmlwhA_MtDmJc2VjcDI1NmsxoQNXD7fj3sscyOKBiHYy14igj1vJYWdKYZH7n3T8qRpIcYRzb"
          + "mFwwIN0Y3CCdl-DdWRwgnZf";

  @Mock private NodeKey nodeKey;
  @Mock private ForkIdManager forkIdManager;
  @Mock private NodeRecordManager nodeRecordManager;
  @Mock private DiscoveryPeerV4 localPeer;

  private NetworkingConfiguration config;
  private NodeRecord testNodeRecord;

  @BeforeEach
  void setUp() {
    config =
        ImmutableNetworkingConfiguration.builder()
            .discoveryConfiguration(
                DiscoveryConfiguration.create()
                    .setEnabled(true)
                    .setAdvertisedHost("127.0.0.1")
                    .setBindHost("0.0.0.0")
                    .setBindPort(0))
            .build();

    testNodeRecord = NodeRecordFactory.DEFAULT.fromEnr(TEST_ENR);
  }

  @Test
  void allowAllWhenNoRestrictionsConfigured() {
    final AddressAccessPolicy policy =
        createFactory(PeerPermissions.NOOP, List.of()).createAddressAccessPolicy();

    assertThat(policy).isSameAs(AddressAccessPolicy.ALLOW_ALL);
  }

  @Test
  void allowNodeRecordWithNoopPermissionsAndMatchingSubnet() {
    final IPAddress subnet = subnet("15.204.180.0/24");
    final AddressAccessPolicy policy =
        createFactory(PeerPermissions.NOOP, List.of(subnet)).createAddressAccessPolicy();

    // 15.204.180.57 is within 15.204.180.0/24
    assertThat(policy.allow(testNodeRecord)).isTrue();
  }

  @Test
  void rejectNodeRecordWhenPermissionsDeny() {
    when(nodeRecordManager.getLocalNode()).thenReturn(Optional.of(localPeer));

    final PeerPermissions rejectAll = rejectAllPermissions();
    final AddressAccessPolicy policy =
        createFactory(rejectAll, List.of()).createAddressAccessPolicy();

    assertThat(policy.allow(testNodeRecord)).isFalse();
  }

  @Test
  void allowNodeRecordWhenPermissionsAllow() {
    when(nodeRecordManager.getLocalNode()).thenReturn(Optional.of(localPeer));

    final PeerPermissions allowAll = allowAllPermissions();
    final AddressAccessPolicy policy =
        createFactory(allowAll, List.of()).createAddressAccessPolicy();

    assertThat(policy.allow(testNodeRecord)).isTrue();
  }

  @Test
  void allowNodeRecordWhenLocalNodeNotYetInitialized() {
    when(nodeRecordManager.getLocalNode()).thenReturn(Optional.empty());

    final PeerPermissions rejectAll = rejectAllPermissions();
    final AddressAccessPolicy policy =
        createFactory(rejectAll, List.of()).createAddressAccessPolicy();

    // Should allow through during startup even with rejecting permissions
    assertThat(policy.allow(testNodeRecord)).isTrue();
  }

  @Test
  void rejectNodeRecordOutsideAllowedSubnet() {
    // 10.0.0.0/8 does not include 15.204.180.57
    final IPAddress subnet = subnet("10.0.0.0/8");
    final AddressAccessPolicy policy =
        createFactory(PeerPermissions.NOOP, List.of(subnet)).createAddressAccessPolicy();

    assertThat(policy.allow(testNodeRecord)).isFalse();
  }

  @Test
  void rejectNodeRecordWithNoAddressWhenSubnetsConfigured() {
    final IPAddress subnet = subnet("10.0.0.0/8");
    final AddressAccessPolicy policy =
        createFactory(PeerPermissions.NOOP, List.of(subnet)).createAddressAccessPolicy();

    // A NodeRecord with no UDP or TCP address cannot be verified against subnets
    final NodeRecord noAddressRecord = mock(NodeRecord.class);
    when(noAddressRecord.getUdpAddress()).thenReturn(Optional.empty());
    when(noAddressRecord.getTcpAddress()).thenReturn(Optional.empty());

    assertThat(policy.allow(noAddressRecord)).isFalse();
  }

  @Test
  void allowInetSocketAddressInSubnet() {
    final IPAddress subnet = subnet("192.168.1.0/24");
    final AddressAccessPolicy policy =
        createFactory(PeerPermissions.NOOP, List.of(subnet)).createAddressAccessPolicy();

    assertThat(policy.allow(new InetSocketAddress("192.168.1.50", 30303))).isTrue();
    assertThat(policy.allow(new InetSocketAddress("10.0.0.1", 30303))).isFalse();
  }

  @Test
  void rejectNodeRecordBySubnetBeforeCheckingPermissions() {
    // Subnet check should reject first — permissions must not be consulted.
    final IPAddress subnet = subnet("10.0.0.0/8");
    final PeerPermissions mockPermissions = mock(PeerPermissions.class);

    final AddressAccessPolicy policy =
        createFactory(mockPermissions, List.of(subnet)).createAddressAccessPolicy();

    // 15.204.180.57 is outside 10.0.0.0/8 — rejected by subnet before permissions
    assertThat(policy.allow(testNodeRecord)).isFalse();
    verify(mockPermissions, never()).isPermitted(any(), any(), any());
  }

  @Test
  void allowNodeRecordWhenSubnetAndPermissionsBothPass() {
    when(nodeRecordManager.getLocalNode()).thenReturn(Optional.of(localPeer));

    // 15.204.0.0/16 includes 15.204.180.57
    final IPAddress subnet = subnet("15.204.0.0/16");
    final PeerPermissions allowAll = allowAllPermissions();

    final AddressAccessPolicy policy =
        createFactory(allowAll, List.of(subnet)).createAddressAccessPolicy();

    assertThat(policy.allow(testNodeRecord)).isTrue();
  }

  private static IPAddress subnet(final String cidr) {
    return new IPAddressString(cidr).getAddress().toPrefixBlock();
  }

  private PeerDiscoveryAgentFactoryV5 createFactory(
      final PeerPermissions peerPermissions, final List<IPAddress> allowedSubnets) {
    return new PeerDiscoveryAgentFactoryV5(
        config, nodeKey, peerPermissions, allowedSubnets, forkIdManager, nodeRecordManager);
  }

  private static PeerPermissions rejectAllPermissions() {
    return new PeerPermissions() {
      @Override
      public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
        return false;
      }
    };
  }

  private static PeerPermissions allowAllPermissions() {
    return new PeerPermissions() {
      @Override
      public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
        return true;
      }
    };
  }
}
