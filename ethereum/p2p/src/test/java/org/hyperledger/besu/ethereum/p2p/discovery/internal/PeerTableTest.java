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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable.AddResult.AddOutcome;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable.EvictResult;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable.EvictResult.EvictOutcome;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PeerTableTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void addPeer() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);

    for (final DiscoveryPeer peer : peers) {
      final PeerTable.AddResult result = table.tryAdd(peer);
      assertThat(result.getOutcome()).isEqualTo(AddOutcome.ADDED);
    }

    assertThat(table.streamAllPeers()).hasSize(5);
  }

  @Test
  public void addSelf() {
    final DiscoveryPeer localPeer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(12345)
                .build());
    final PeerTable table = new PeerTable(localPeer.getId(), 16);
    final PeerTable.AddResult result = table.tryAdd(localPeer);

    assertThat(result.getOutcome()).isEqualTo(AddOutcome.SELF);
    assertThat(table.streamAllPeers()).hasSize(0);
  }

  @Test
  public void peerExists() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();

    assertThat(table.tryAdd(peer).getOutcome()).isEqualTo(AddOutcome.ADDED);

    assertThat(table.tryAdd(peer))
        .satisfies(
            result -> {
              assertThat(result.getOutcome()).isEqualTo(AddOutcome.ALREADY_EXISTED);
              assertThat(result.getEvictionCandidate()).isNull();
            });
  }

  @Test
  public void peerExists_withDifferentIp() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final Bytes peerId =
        SIGNATURE_ALGORITHM.get().generateKeyPair().getPublicKey().getEncodedBytes();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.1", 30303, Optional.empty()));

    assertThat(table.tryAdd(peer).getOutcome()).isEqualTo(AddOutcome.ADDED);

    final DiscoveryPeer duplicatePeer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.2", 30303, Optional.empty()));
    assertThat(table.tryAdd(duplicatePeer))
        .satisfies(
            result -> {
              assertThat(result.getOutcome()).isEqualTo(AddOutcome.ALREADY_EXISTED);
              assertThat(result.getEvictionCandidate()).isNull();
            });
  }

  @Test
  public void peerExists_withDifferentUdpPort() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final Bytes peerId =
        SIGNATURE_ALGORITHM.get().generateKeyPair().getPublicKey().getEncodedBytes();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.1", 30303, Optional.empty()));

    assertThat(table.tryAdd(peer).getOutcome()).isEqualTo(AddOutcome.ADDED);

    final DiscoveryPeer duplicatePeer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.1", 30301, Optional.empty()));
    assertThat(table.tryAdd(duplicatePeer))
        .satisfies(
            result -> {
              assertThat(result.getOutcome()).isEqualTo(AddOutcome.ALREADY_EXISTED);
              assertThat(result.getEvictionCandidate()).isNull();
            });
  }

  @Test
  public void peerExists_withDifferentIdAndUdpPort() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final Bytes peerId =
        SIGNATURE_ALGORITHM.get().generateKeyPair().getPublicKey().getEncodedBytes();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.1", 30303, Optional.empty()));

    assertThat(table.tryAdd(peer).getOutcome()).isEqualTo(AddOutcome.ADDED);

    final DiscoveryPeer duplicatePeer =
        DiscoveryPeer.fromIdAndEndpoint(peerId, new Endpoint("1.1.1.2", 30301, Optional.empty()));
    assertThat(table.tryAdd(duplicatePeer))
        .satisfies(
            result -> {
              assertThat(result.getOutcome()).isEqualTo(AddOutcome.ALREADY_EXISTED);
              assertThat(result.getEvictionCandidate()).isNull();
            });
  }

  @Test
  public void evictExistingPeerShouldEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();

    table.tryAdd(peer);

    final EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.EVICTED);
  }

  @Test
  public void evictPeerFromEmptyTableShouldNotEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();

    final EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.ABSENT);
  }

  @Test
  public void evictAbsentPeerShouldNotEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();
    final List<DiscoveryPeer> otherPeers = helper.createDiscoveryPeers(5);
    otherPeers.forEach(table::tryAdd);

    final EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.ABSENT);
  }

  @Test
  public void evictSelfPeerShouldReturnSelfOutcome() {
    final DiscoveryPeer peer = helper.createDiscoveryPeer();
    final PeerTable table = new PeerTable(peer.getId(), 16);

    final EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.SELF);
  }
}
