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
package org.hyperledger.besu.ethereum.p2p.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper.enodeBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Test;

public class MaintainedPeersTest {

  private final MaintainedPeers maintainedPeers = new MaintainedPeers();

  @Test
  public void add_newPeer() {
    final Peer peer = createPeer();
    final AtomicInteger callbackCount = new AtomicInteger(0);
    maintainedPeers.subscribeAdd(
        (addedPeer, wasAdded) -> {
          callbackCount.incrementAndGet();
          assertThat(addedPeer).isEqualTo(peer);
          assertThat(wasAdded).isTrue();
        });

    assertThat(maintainedPeers.size()).isEqualTo(0);
    assertThat(maintainedPeers.add(peer)).isTrue();
    assertThat(callbackCount).hasValue(1);
    assertThat(maintainedPeers.size()).isEqualTo(1);
    assertThat(maintainedPeers.contains(peer)).isTrue();
  }

  @Test
  public void add_invalidPeer() {
    final Peer peer = nonListeningPeer();
    assertThatThrownBy(() -> maintainedPeers.add(peer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid enode url.  Enode url must contain a non-zero listening port.");
    assertThat(maintainedPeers.contains(peer)).isFalse();
  }

  @Test
  public void add_newDuplicatePeer() {
    // Initial add
    assertThat(maintainedPeers.size()).isEqualTo(0);
    final Peer peer = createPeer();
    assertThat(maintainedPeers.add(peer)).isTrue();
    assertThat(maintainedPeers.size()).isEqualTo(1);

    // Test duplicate add
    final AtomicInteger callbackCount = new AtomicInteger(0);
    maintainedPeers.subscribeAdd(
        (addedPeer, wasAdded) -> {
          callbackCount.incrementAndGet();
          assertThat(addedPeer).isEqualTo(peer);
          assertThat(wasAdded).isFalse();
        });
    assertThat(maintainedPeers.add(peer)).isFalse();
    assertThat(callbackCount).hasValue(1);
    assertThat(maintainedPeers.size()).isEqualTo(1);
  }

  @Test
  public void remove_existingPeer() {
    // Initial add
    final Peer peer = createPeer();
    assertThat(maintainedPeers.add(peer)).isTrue();
    assertThat(maintainedPeers.size()).isEqualTo(1);
    assertThat(maintainedPeers.contains(peer)).isTrue();

    // Test remove
    final AtomicInteger callbackCount = new AtomicInteger(0);
    maintainedPeers.subscribeRemove(
        (addedPeer, wasRemoved) -> {
          callbackCount.incrementAndGet();
          assertThat(addedPeer).isEqualTo(peer);
          assertThat(wasRemoved).isTrue();
        });
    assertThat(maintainedPeers.remove(peer)).isTrue();
    assertThat(callbackCount).hasValue(1);
    assertThat(maintainedPeers.size()).isEqualTo(0);
    assertThat(maintainedPeers.contains(peer)).isFalse();
  }

  @Test
  public void remove_nonExistentPeer() {
    final Peer peer = createPeer();
    assertThat(maintainedPeers.size()).isEqualTo(0);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    maintainedPeers.subscribeRemove(
        (addedPeer, wasRemoved) -> {
          callbackCount.incrementAndGet();
          assertThat(addedPeer).isEqualTo(peer);
          assertThat(wasRemoved).isFalse();
        });
    assertThat(maintainedPeers.remove(peer)).isFalse();
    assertThat(callbackCount).hasValue(1);
    assertThat(maintainedPeers.size()).isEqualTo(0);
  }

  @Test
  public void stream_withPeers() {
    // Initial add
    final Peer peerA = createPeer();
    final Peer peerB = createPeer();
    assertThat(maintainedPeers.add(peerA)).isTrue();
    assertThat(maintainedPeers.add(peerB)).isTrue();

    final List<Peer> peers = maintainedPeers.streamPeers().collect(Collectors.toList());
    assertThat(peers).containsExactlyInAnyOrder(peerA, peerB);
  }

  @Test
  public void contains() {
    final Peer peerA = createPeer();
    final Peer peerAClone = DefaultPeer.fromEnodeURL(peerA.getEnodeURL());
    final Peer peerB = createPeer();

    maintainedPeers.add(peerA);
    assertThat(maintainedPeers.contains(peerA)).isTrue();
    assertThat(maintainedPeers.contains(peerAClone)).isTrue();
    assertThat(maintainedPeers.contains(peerB)).isFalse();
  }

  @Test
  public void stream_empty() {
    final List<Peer> peers = maintainedPeers.streamPeers().collect(Collectors.toList());
    assertThat(peers).isEmpty();
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(enodeBuilder().build());
  }

  private Peer nonListeningPeer() {
    return DefaultPeer.fromEnodeURL(enodeBuilder().disableListening().build());
  }
}
