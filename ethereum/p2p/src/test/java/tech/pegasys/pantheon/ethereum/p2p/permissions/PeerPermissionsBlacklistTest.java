/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class PeerPermissionsBlacklistTest {

  @Test
  public void add_peer() {
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
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
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
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
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
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
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
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
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();

    Peer peer = createPeer();
    assertThat(blacklist.isPermitted(peer)).isTrue();

    blacklist.add(peer);
    assertThat(blacklist.isPermitted(peer)).isFalse();

    blacklist.remove(peer);
    assertThat(blacklist.isPermitted(peer)).isTrue();
  }

  @Test
  public void subscribeUpdate() {
    PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
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

    assertThat(blacklist.isPermitted(peer)).isTrue();
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
    final PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create(2);
    Peer peerA = createPeer();
    Peer peerB = createPeer();
    Peer peerC = createPeer();

    // All peers are initially permitted
    assertThat(blacklist.isPermitted(peerA)).isTrue();
    assertThat(blacklist.isPermitted(peerB)).isTrue();
    assertThat(blacklist.isPermitted(peerC)).isTrue();

    // Add peerA
    blacklist.add(peerA);
    assertThat(blacklist.isPermitted(peerA)).isFalse();
    assertThat(blacklist.isPermitted(peerB)).isTrue();
    assertThat(blacklist.isPermitted(peerC)).isTrue();

    // Add peerB
    blacklist.add(peerB);
    assertThat(blacklist.isPermitted(peerA)).isFalse();
    assertThat(blacklist.isPermitted(peerB)).isFalse();
    assertThat(blacklist.isPermitted(peerC)).isTrue();

    // Add peerC
    // Limit is exceeded and peerA should drop off of the list and be allowed
    blacklist.add(peerC);
    assertThat(blacklist.isPermitted(peerA)).isTrue();
    assertThat(blacklist.isPermitted(peerB)).isFalse();
    assertThat(blacklist.isPermitted(peerC)).isFalse();
  }

  @Test
  public void createWithUnlimitedCapacity() {
    final PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
    final int peerCount = 200;
    final List<Peer> peers =
        Stream.generate(this::createPeer).limit(peerCount).collect(Collectors.toList());

    peers.forEach(p -> assertThat(blacklist.isPermitted(p)).isTrue());
    peers.forEach(blacklist::add);
    peers.forEach(p -> assertThat(blacklist.isPermitted(p)).isFalse());

    peers.forEach(blacklist::remove);
    peers.forEach(p -> assertThat(blacklist.isPermitted(p)).isTrue());
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .nodeId(Peer.randomId())
            .ipAddress("127.0.0.1")
            .discoveryAndListeningPorts(EnodeURL.DEFAULT_LISTENING_PORT)
            .build());
  }
}
