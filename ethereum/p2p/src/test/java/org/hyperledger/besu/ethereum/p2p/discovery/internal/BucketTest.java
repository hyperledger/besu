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

import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryTestHelper;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class BucketTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void successfulAddAndGet() {
    final Bucket kBucket = new Bucket(16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(10);
    for (int i = 0; i < peers.size() - 1; i++) {
      kBucket.add(peers.get(i));
    }
    final DiscoveryPeer testPeer = peers.get(peers.size() - 1);
    kBucket.add(testPeer);
    assertThat(testPeer).isEqualTo(kBucket.getAndTouch(testPeer.getId()).get());
  }

  @Test
  public void unsuccessfulAdd() {
    final Bucket kBucket = new Bucket(16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(17);
    for (int i = 0; i < peers.size() - 1; i++) {
      kBucket.add(peers.get(i));
    }
    final DiscoveryPeer testPeer = peers.get(peers.size() - 1);
    final Optional<DiscoveryPeer> evictionCandidate = kBucket.add(testPeer);
    assertThat(evictionCandidate.get()).isEqualTo(kBucket.getAndTouch(peers.get(0).getId()).get());
  }

  @Test
  public void movedToHead() {
    final Bucket kBucket = new Bucket(16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);
    for (final DiscoveryPeer peer : peers) {
      kBucket.add(peer);
    }
    kBucket.getAndTouch(peers.get(0).getId());
    assertThat(kBucket.getPeers().indexOf(peers.get(0))).isEqualTo(0);
  }

  @Test
  public void evictPeer() {
    final Bucket kBucket = new Bucket(16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);
    for (final DiscoveryPeer p : peers) {
      kBucket.add(p);
    }
    kBucket.evict(peers.get(4));
    assertFalse(kBucket.getPeers().contains(peers.get(4)));
  }

  @Test
  public void allActionsOnBucket() {
    final Bucket kBucket = new Bucket(16);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(30);

    // Try to evict a peer on an empty bucket.
    assertThat(kBucket.evict(peers.get(29))).isFalse();

    // Add the first 16 peers to the bucket.
    peers
        .subList(0, 16)
        .forEach(
            p -> {
              assertThat(kBucket.getAndTouch(p.getId())).isNotPresent();
              assertThat(kBucket.add(p)).isNotPresent();
              assertThat(kBucket.getAndTouch(p.getId())).isPresent().get().isEqualTo(p);
              assertThatThrownBy(() -> kBucket.add(p)).isInstanceOf(IllegalArgumentException.class);
            });

    // Ensure the peer is not there already.
    assertThat(kBucket.getAndTouch(peers.get(16).getId())).isNotPresent();

    // Try to add a 17th peer and check that the eviction candidate matches the first peer.
    final Optional<DiscoveryPeer> evictionCandidate = kBucket.add(peers.get(16));
    assertThat(evictionCandidate).isPresent().get().isEqualTo(peers.get(0));

    // Try to add a peer that already exists, and check that the bucket size still remains capped at
    // 16.
    assertThatThrownBy(() -> kBucket.add(peers.get(0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(kBucket.getPeers()).hasSize(16);

    // Try to evict a peer that doesn't exist, and check the result is false.
    assertThat(kBucket.evict(peers.get(17))).isFalse();
    assertThat(kBucket.getPeers()).hasSize(16);

    // Evict a peer from head, another from the middle, and the tail.
    assertThat(kBucket.evict(peers.get(0))).isTrue();
    assertThat(kBucket.getPeers()).hasSize(15);
    assertThat(kBucket.evict(peers.get(7))).isTrue();
    assertThat(kBucket.getPeers()).hasSize(14);
    assertThat(kBucket.evict(peers.get(15))).isTrue();
    assertThat(kBucket.getPeers()).hasSize(13);

    // Check that we can now add peers again.
    assertThat(kBucket.add(peers.get(0))).isNotPresent();
    assertThat(kBucket.add(peers.get(7))).isNotPresent();
    assertThat(kBucket.add(peers.get(15))).isNotPresent();
    assertThat(kBucket.add(peers.get(17))).isPresent().get().isEqualTo(peers.get(1));

    // Test the touch behaviour.
    assertThat(kBucket.getAndTouch(peers.get(6).getId())).isPresent().get().isEqualTo(peers.get(6));
    assertThat(kBucket.getAndTouch(peers.get(9).getId())).isPresent().get().isEqualTo(peers.get(9));

    assertThat(kBucket.getPeers())
        .containsSequence(
            peers.get(9),
            peers.get(6),
            peers.get(15),
            peers.get(7),
            peers.get(0),
            peers.get(14),
            peers.get(13),
            peers.get(12),
            peers.get(11),
            peers.get(10),
            peers.get(8),
            peers.get(5),
            peers.get(4),
            peers.get(3),
            peers.get(2),
            peers.get(1));
  }
}
