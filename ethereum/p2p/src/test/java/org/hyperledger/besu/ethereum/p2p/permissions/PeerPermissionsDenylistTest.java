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
package org.hyperledger.besu.ethereum.p2p.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class PeerPermissionsDenylistTest {

  private final Peer localNode = createPeer();

  @Test
  public void add_peer() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    Peer peer = createPeer();

    final AtomicInteger callbackCount = new AtomicInteger(0);
    blacklist.subscribeUpdate(
        (restricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          assertThat(restricted).isTrue();
          assertThat(affectedPeers).contains(Collections.singletonList(peer));
        });

    assertThat(callbackCount).hasValue(0);

    blacklist.add(peer);
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  public void remove_peer() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    Peer peer = createPeer();
    blacklist.add(peer);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    blacklist.subscribeUpdate(
        (restricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          assertThat(restricted).isFalse();
          assertThat(affectedPeers).contains(Collections.singletonList(peer));
        });

    assertThat(callbackCount).hasValue(0);

    blacklist.remove(peer);
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  public void add_id() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    Peer peer = createPeer();

    final AtomicInteger callbackCount = new AtomicInteger(0);
    blacklist.subscribeUpdate(
        (restricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          assertThat(restricted).isTrue();
          assertThat(affectedPeers).isEmpty();
        });

    assertThat(callbackCount).hasValue(0);

    blacklist.add(peer.getId());
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  public void remove_id() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    Peer peer = createPeer();
    blacklist.add(peer);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    blacklist.subscribeUpdate(
        (restricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          assertThat(restricted).isFalse();
          assertThat(affectedPeers).isEmpty();
        });

    assertThat(callbackCount).hasValue(0);

    blacklist.remove(peer.getId());
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  public void trackedPeerIsNotPermitted() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();

    Peer peer = createPeer();
    checkPermissions(blacklist, peer, true);

    blacklist.add(peer);
    checkPermissions(blacklist, peer, false);

    blacklist.remove(peer);
    checkPermissions(blacklist, peer, true);
  }

  @Test
  public void subscribeUpdate() {
    PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    final AtomicInteger callbackCount = new AtomicInteger(0);
    final AtomicInteger restrictedCallbackCount = new AtomicInteger(0);
    Peer peer = createPeer();

    blacklist.subscribeUpdate(
        (permissionsRestricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          if (permissionsRestricted) {
            restrictedCallbackCount.incrementAndGet();
          }
        });

    checkPermissions(blacklist, peer, true);
    assertThat(callbackCount).hasValue(0);
    assertThat(restrictedCallbackCount).hasValue(0);

    blacklist.add(peer);
    assertThat(callbackCount).hasValue(1);
    assertThat(restrictedCallbackCount).hasValue(1);

    blacklist.add(peer);
    assertThat(callbackCount).hasValue(1);
    assertThat(restrictedCallbackCount).hasValue(1);

    blacklist.remove(peer);
    assertThat(callbackCount).hasValue(2);
    assertThat(restrictedCallbackCount).hasValue(1);

    blacklist.remove(peer);
    assertThat(callbackCount).hasValue(2);
    assertThat(restrictedCallbackCount).hasValue(1);

    blacklist.add(peer);
    assertThat(callbackCount).hasValue(3);
    assertThat(restrictedCallbackCount).hasValue(2);
  }

  @Test
  public void createWithLimitedCapacity() {
    final PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create(2);
    Peer peerA = createPeer();
    Peer peerB = createPeer();
    Peer peerC = createPeer();

    // All peers are initially permitted
    checkPermissions(blacklist, peerA, true);
    checkPermissions(blacklist, peerB, true);
    checkPermissions(blacklist, peerC, true);

    // Add peerA
    blacklist.add(peerA);
    checkPermissions(blacklist, peerA, false);
    checkPermissions(blacklist, peerB, true);
    checkPermissions(blacklist, peerC, true);

    // Add peerB
    blacklist.add(peerB);
    checkPermissions(blacklist, peerA, false);
    checkPermissions(blacklist, peerB, false);
    checkPermissions(blacklist, peerC, true);

    // Add peerC
    // Limit is exceeded and peerA should drop off of the list and be allowed
    blacklist.add(peerC);
    checkPermissions(blacklist, peerA, true);
    checkPermissions(blacklist, peerB, false);
    checkPermissions(blacklist, peerC, false);
  }

  private void checkPermissions(
      final PeerPermissionsDenylist blacklist,
      final Peer remotePeer,
      final boolean expectedResult) {
    for (Action action : Action.values()) {
      assertThat(blacklist.isPermitted(localNode, remotePeer, action)).isEqualTo(expectedResult);
    }
  }

  @Test
  public void createWithUnlimitedCapacity() {
    final PeerPermissionsDenylist blacklist = PeerPermissionsDenylist.create();
    final int peerCount = 200;
    final List<Peer> peers =
        Stream.generate(this::createPeer).limit(peerCount).collect(Collectors.toList());

    peers.forEach(p -> checkPermissions(blacklist, p, true));
    peers.forEach(blacklist::add);
    peers.forEach(p -> checkPermissions(blacklist, p, false));

    peers.forEach(blacklist::remove);
    peers.forEach(p -> checkPermissions(blacklist, p, true));
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURLImpl.builder()
            .nodeId(Peer.randomId())
            .ipAddress("127.0.0.1")
            .discoveryAndListeningPorts(EnodeURLImpl.DEFAULT_LISTENING_PORT)
            .build());
  }
}
