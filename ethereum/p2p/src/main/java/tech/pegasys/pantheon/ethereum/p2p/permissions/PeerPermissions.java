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

import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

public abstract class PeerPermissions {
  private final Subscribers<PermissionsUpdateCallback> updateSubscribers = new Subscribers<>();

  public static final PeerPermissions NOOP = new NoopPeerPermissions();

  public static PeerPermissions noop() {
    return NOOP;
  }

  public static PeerPermissions combine(final PeerPermissions... permissions) {
    return combine(Arrays.asList(permissions));
  }

  public static PeerPermissions combine(final List<PeerPermissions> permissions) {
    return CombinedPeerPermissions.create(permissions);
  }

  /**
   * @param peer The {@link Peer} object representing the remote node
   * @return True if we are allowed to communicate with this peer.
   */
  public abstract boolean isPermitted(final Peer peer);

  public void subscribeUpdate(final PermissionsUpdateCallback callback) {
    updateSubscribers.subscribe(callback);
  }

  protected void dispatchUpdate(
      final boolean permissionsRestricted, final Optional<List<Peer>> affectedPeers) {
    updateSubscribers.forEach(s -> s.onUpdate(permissionsRestricted, affectedPeers));
  }

  private static class NoopPeerPermissions extends PeerPermissions {
    @Override
    public boolean isPermitted(final Peer peer) {
      return true;
    }
  }

  private static class CombinedPeerPermissions extends PeerPermissions {
    private final ImmutableList<PeerPermissions> permissions;

    private CombinedPeerPermissions(final ImmutableList<PeerPermissions> permissions) {
      this.permissions = permissions;
    }

    public static PeerPermissions create(final List<PeerPermissions> permissions) {
      final ImmutableList<PeerPermissions> filteredPermissions =
          permissions.stream()
              .flatMap(
                  p -> {
                    if (p instanceof CombinedPeerPermissions) {
                      return ((CombinedPeerPermissions) p).permissions.stream();
                    } else {
                      return Stream.of(p);
                    }
                  })
              .filter(p -> !(p instanceof NoopPeerPermissions))
              .collect(ImmutableList.toImmutableList());

      if (filteredPermissions.size() == 0) {
        return PeerPermissions.NOOP;
      } else if (filteredPermissions.size() == 1) {
        return filteredPermissions.get(0);
      } else {
        return new CombinedPeerPermissions(filteredPermissions);
      }
    }

    @Override
    public void subscribeUpdate(final PermissionsUpdateCallback callback) {
      for (final PeerPermissions permission : permissions) {
        permission.subscribeUpdate(callback);
      }
    }

    @Override
    public boolean isPermitted(final Peer peer) {
      for (PeerPermissions permission : permissions) {
        if (!permission.isPermitted(peer)) {
          return false;
        }
      }
      return true;
    }
  }
}
