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

import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper.generateDiscoveryPeers;
import static tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper.generateKeyPairs;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Test;

public class BucketTest {

  @Test
  public void successfulAddAndGet() {
    final Bucket kBucket = new Bucket(16);
    final DiscoveryPeer[] peers = generateDiscoveryPeers(generateKeyPairs(10));
    for (int i = 0; i < peers.length - 1; i++) {
      kBucket.add(peers[i]);
    }
    final DiscoveryPeer testPeer = peers[peers.length - 1];
    kBucket.add(testPeer);
    assertThat(testPeer).isEqualTo(kBucket.getAndTouch(testPeer.getId()).get());
  }

  @Test
  public void unsuccessfulAdd() {
    final Bucket kBucket = new Bucket(16);
    final DiscoveryPeer[] peers = generateDiscoveryPeers(generateKeyPairs(17));
    for (int i = 0; i < peers.length - 1; i++) {
      kBucket.add(peers[i]);
    }
    final DiscoveryPeer testPeer = peers[peers.length - 1];
    final Optional<DiscoveryPeer> evictionCandidate = kBucket.add(testPeer);
    assertThat(evictionCandidate.get()).isEqualTo(kBucket.getAndTouch(peers[0].getId()).get());
  }

  @Test
  public void movedToHead() {
    final Bucket kBucket = new Bucket(16);
    final DiscoveryPeer[] peers = generateDiscoveryPeers(generateKeyPairs(5));
    for (final DiscoveryPeer peer : peers) {
      kBucket.add(peer);
    }
    kBucket.getAndTouch(peers[0].getId());
    assertThat(kBucket.peers().indexOf(peers[0])).isEqualTo(0);
  }

  @Test
  public void evictPeer() {
    final Bucket kBucket = new Bucket(16);
    final DiscoveryPeer[] peers = generateDiscoveryPeers(generateKeyPairs(5));
    for (final DiscoveryPeer p : peers) {
      kBucket.add(p);
    }
    kBucket.evict(peers[4]);
    assertFalse(kBucket.peers().contains(peers[4]));
  }

  @Test
  public void allActionsOnBucket() {
    final Bucket kBucket = new Bucket(16);
    final DiscoveryPeer[] peers = generateDiscoveryPeers(generateKeyPairs(30));

    // Try to evict a peer on an empty bucket.
    assertThat(kBucket.evict(peers[29])).isFalse();

    // Add the first 16 peers to the bucket.
    Stream.of(peers)
        .limit(16)
        .forEach(
            p -> {
              assertThat(kBucket.getAndTouch(p.getId())).isNotPresent();
              assertThat(kBucket.add(p)).isNotPresent();
              assertThat(kBucket.getAndTouch(p.getId())).isPresent().get().isEqualTo(p);
              assertThatThrownBy(() -> kBucket.add(p)).isInstanceOf(IllegalArgumentException.class);
            });

    // Ensure the peer is not there already.
    assertThat(kBucket.getAndTouch(peers[16].getId())).isNotPresent();

    // Try to add a 17th peer and check that the eviction candidate matches the first peer.
    final Optional<DiscoveryPeer> evictionCandidate = kBucket.add(peers[16]);
    assertThat(evictionCandidate).isPresent().get().isEqualTo(peers[0]);

    // Try to add a peer that already exists, and check that the bucket size still remains capped at
    // 16.
    assertThatThrownBy(() -> kBucket.add(peers[0])).isInstanceOf(IllegalArgumentException.class);
    assertThat(kBucket.peers()).hasSize(16);

    // Try to evict a peer that doesn't exist, and check the result is false.
    assertThat(kBucket.evict(peers[17])).isFalse();
    assertThat(kBucket.peers()).hasSize(16);

    // Evict a peer from head, another from the middle, and the tail.
    assertThat(kBucket.evict(peers[0])).isTrue();
    assertThat(kBucket.peers()).hasSize(15);
    assertThat(kBucket.evict(peers[7])).isTrue();
    assertThat(kBucket.peers()).hasSize(14);
    assertThat(kBucket.evict(peers[15])).isTrue();
    assertThat(kBucket.peers()).hasSize(13);

    // Check that we can now add peers again.
    assertThat(kBucket.add(peers[0])).isNotPresent();
    assertThat(kBucket.add(peers[7])).isNotPresent();
    assertThat(kBucket.add(peers[15])).isNotPresent();
    assertThat(kBucket.add(peers[17])).isPresent().get().isEqualTo(peers[1]);

    // Test the touch behaviour.
    assertThat(kBucket.getAndTouch(peers[6].getId())).isPresent().get().isEqualTo(peers[6]);
    assertThat(kBucket.getAndTouch(peers[9].getId())).isPresent().get().isEqualTo(peers[9]);

    assertThat(kBucket.peers())
        .containsSequence(
            peers[9], peers[6], peers[15], peers[7], peers[0], peers[14], peers[13], peers[12],
            peers[11], peers[10], peers[8], peers[5], peers[4], peers[3], peers[2], peers[1]);
  }
}
