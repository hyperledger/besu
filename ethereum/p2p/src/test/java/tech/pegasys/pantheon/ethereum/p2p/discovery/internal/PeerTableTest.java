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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable.AddResult.AddOutcome;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable.EvictResult;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable.EvictResult.EvictOutcome;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.List;

import org.junit.Test;

public class PeerTableTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void addPeer() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);

    for (final DiscoveryPeer peer : peers) {
      final PeerTable.AddResult result = table.tryAdd(peer);
      assertThat(result.getOutcome()).isEqualTo(AddOutcome.ADDED);
    }

    assertThat(table.getAllPeers()).hasSize(5);
  }

  @Test
  public void addSelf() {
    final DiscoveryPeer localPeer =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder()
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .listeningPort(12345)
                .build());
    final PeerTable table = new PeerTable(localPeer.getId(), 16);
    final PeerTable.AddResult result = table.tryAdd(localPeer);

    assertThat(result.getOutcome()).isEqualTo(AddOutcome.SELF);
    assertThat(table.getAllPeers()).hasSize(0);
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
  public void evictExistingPeerShouldEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();

    table.tryAdd(peer);

    EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.EVICTED);
  }

  @Test
  public void evictPeerFromEmptyTableShouldNotEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();

    EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.ABSENT);
  }

  @Test
  public void evictAbsentPeerShouldNotEvict() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer = helper.createDiscoveryPeer();
    final List<DiscoveryPeer> otherPeers = helper.createDiscoveryPeers(5);
    otherPeers.forEach(table::tryAdd);

    EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.ABSENT);
  }

  @Test
  public void evictSelfPeerShouldReturnSelfOutcome() {
    final DiscoveryPeer peer = helper.createDiscoveryPeer();
    final PeerTable table = new PeerTable(peer.getId(), 16);

    EvictResult evictResult = table.tryEvict(peer);
    assertThat(evictResult.getOutcome()).isEqualTo(EvictOutcome.SELF);
  }
}
