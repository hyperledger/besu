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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class PeerPermissionsTest {
  final Peer localPeer = createPeer();

  @Test
  public void subscribeUpdate() {
    TestPeerPermissions peerPermissions = new TestPeerPermissions(false);
    final AtomicInteger callbackCount = new AtomicInteger(0);

    peerPermissions.subscribeUpdate(
        (permissionsRestricted, affectedPeers) -> callbackCount.incrementAndGet());

    peerPermissions.allowPeers(true);
    assertThat(callbackCount).hasValue(1);

    peerPermissions.allowPeers(false);
    assertThat(callbackCount).hasValue(2);
  }

  @Test
  public void subscribeUpdate_forCombinedPermissions() {
    TestPeerPermissions peerPermissionsA = new TestPeerPermissions(false);
    TestPeerPermissions peerPermissionsB = new TestPeerPermissions(false);
    PeerPermissions combined = PeerPermissions.combine(peerPermissionsA, peerPermissionsB);

    final AtomicInteger callbackCount = new AtomicInteger(0);
    final AtomicInteger restrictedCallbackCount = new AtomicInteger(0);

    combined.subscribeUpdate(
        (permissionsRestricted, affectedPeers) -> {
          callbackCount.incrementAndGet();
          if (permissionsRestricted) {
            restrictedCallbackCount.incrementAndGet();
          }
        });

    peerPermissionsA.allowPeers(true);
    assertThat(callbackCount).hasValue(1);
    assertThat(restrictedCallbackCount).hasValue(0);

    peerPermissionsB.allowPeers(true);
    assertThat(callbackCount).hasValue(2);
    assertThat(restrictedCallbackCount).hasValue(0);

    peerPermissionsA.allowPeers(false);
    assertThat(callbackCount).hasValue(3);
    assertThat(restrictedCallbackCount).hasValue(1);

    peerPermissionsB.allowPeers(false);
    assertThat(callbackCount).hasValue(4);
    assertThat(restrictedCallbackCount).hasValue(2);
  }

  @Test
  public void isPermitted_forCombinedPermissions() {
    final PeerPermissions allowAll = new TestPeerPermissions(true);
    final PeerPermissions disallowAll = new TestPeerPermissions(false);
    final PeerPermissions noop = PeerPermissions.NOOP;
    final PeerPermissions combinedPermissive = PeerPermissions.combine(noop, allowAll);
    final PeerPermissions combinedRestrictive = PeerPermissions.combine(disallowAll, allowAll);

    Peer remotePeer = createPeer();
    Action action = Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION;

    assertThat(
            PeerPermissions.combine(allowAll, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(disallowAll, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(disallowAll, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(allowAll, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(allowAll, allowAll).isPermitted(localPeer, remotePeer, action))
        .isTrue();

    assertThat(
            PeerPermissions.combine(combinedPermissive, allowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isTrue();
    assertThat(
            PeerPermissions.combine(combinedPermissive, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(combinedRestrictive, allowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(combinedRestrictive, disallowAll)
                .isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(combinedRestrictive).isPermitted(localPeer, remotePeer, action))
        .isFalse();
    assertThat(
            PeerPermissions.combine(combinedPermissive).isPermitted(localPeer, remotePeer, action))
        .isTrue();

    assertThat(PeerPermissions.combine(noop).isPermitted(localPeer, remotePeer, action)).isTrue();
    assertThat(PeerPermissions.combine().isPermitted(localPeer, remotePeer, action)).isTrue();
  }

  @Test
  public void close_forCombinedPermissions() {
    TestPeerPermissions peerPermissionsA = spy(new TestPeerPermissions(false));
    TestPeerPermissions peerPermissionsB = spy(new TestPeerPermissions(false));
    PeerPermissions combined = PeerPermissions.combine(peerPermissionsA, peerPermissionsB);

    verify(peerPermissionsA, never()).close();
    verify(peerPermissionsB, never()).close();

    combined.close();

    verify(peerPermissionsA, times(1)).close();
    verify(peerPermissionsB, times(1)).close();
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURLImpl.builder()
            .listeningPort(30303)
            .discoveryPort(30303)
            .nodeId(Peer.randomId())
            .ipAddress("127.0.0.1")
            .build());
  }

  private static class TestPeerPermissions extends PeerPermissions {

    private boolean allowAll;

    public TestPeerPermissions(final boolean allowAll) {
      this.allowAll = allowAll;
    }

    public void allowPeers(final boolean doAllowPeers) {
      this.allowAll = doAllowPeers;
      dispatchUpdate(!doAllowPeers, Optional.empty());
    }

    @Override
    public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
      return allowAll;
    }
  }
}
