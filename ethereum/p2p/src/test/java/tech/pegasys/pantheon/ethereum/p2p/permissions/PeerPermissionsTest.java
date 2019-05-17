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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class PeerPermissionsTest {
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
    final PeerPermissions allowPeers = new TestPeerPermissions(true);
    final PeerPermissions disallowPeers = new TestPeerPermissions(false);
    final PeerPermissions noop = PeerPermissions.NOOP;
    final PeerPermissions combinedPermissive = PeerPermissions.combine(noop, allowPeers);
    final PeerPermissions combinedRestrictive = PeerPermissions.combine(disallowPeers, allowPeers);

    Peer peer =
        DefaultPeer.fromEnodeURL(
            EnodeURL.builder()
                .listeningPort(30303)
                .discoveryPort(30303)
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .build());

    assertThat(PeerPermissions.combine(allowPeers, disallowPeers).isPermitted(peer)).isFalse();
    assertThat(PeerPermissions.combine(disallowPeers, disallowPeers).isPermitted(peer)).isFalse();
    assertThat(PeerPermissions.combine(disallowPeers, disallowPeers).isPermitted(peer)).isFalse();
    assertThat(PeerPermissions.combine(allowPeers, disallowPeers).isPermitted(peer)).isFalse();
    assertThat(PeerPermissions.combine(allowPeers, allowPeers).isPermitted(peer)).isTrue();

    assertThat(PeerPermissions.combine(combinedPermissive, allowPeers).isPermitted(peer)).isTrue();
    assertThat(PeerPermissions.combine(combinedPermissive, disallowPeers).isPermitted(peer))
        .isFalse();
    assertThat(PeerPermissions.combine(combinedRestrictive, allowPeers).isPermitted(peer))
        .isFalse();
    assertThat(PeerPermissions.combine(combinedRestrictive, disallowPeers).isPermitted(peer))
        .isFalse();
    assertThat(PeerPermissions.combine(combinedRestrictive).isPermitted(peer)).isFalse();
    assertThat(PeerPermissions.combine(combinedPermissive).isPermitted(peer)).isTrue();

    assertThat(PeerPermissions.combine(noop).isPermitted(peer)).isTrue();
    assertThat(PeerPermissions.combine().isPermitted(peer)).isTrue();
  }

  private static class TestPeerPermissions extends PeerPermissions {

    private boolean allowPeers;

    public TestPeerPermissions(final boolean allowPeers) {
      this.allowPeers = allowPeers;
    }

    public void allowPeers(final boolean doAllowPeers) {
      this.allowPeers = doAllowPeers;
      dispatchUpdate(!doAllowPeers, Optional.empty());
    }

    @Override
    public boolean isPermitted(final Peer peer) {
      return allowPeers;
    }
  }
}
