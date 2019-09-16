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
package org.hyperledger.besu.ethereum.p2p.permissions;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.util.LimitedSet;
import org.hyperledger.besu.util.LimitedSet.Mode;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import io.vertx.core.impl.ConcurrentHashSet;

public class PeerPermissionsBlacklist extends PeerPermissions {
  private static int DEFAULT_INITIAL_CAPACITY = 20;

  private final Set<BytesValue> blacklist;

  private PeerPermissionsBlacklist(final int initialCapacity, final OptionalInt maxSize) {
    if (maxSize.isPresent()) {
      blacklist =
          LimitedSet.create(initialCapacity, maxSize.getAsInt(), Mode.DROP_LEAST_RECENTLY_ACCESSED);
    } else {
      blacklist = new ConcurrentHashSet<>(initialCapacity);
    }
  }

  private PeerPermissionsBlacklist(final OptionalInt maxSize) {
    this(DEFAULT_INITIAL_CAPACITY, maxSize);
  }

  public static PeerPermissionsBlacklist create() {
    return new PeerPermissionsBlacklist(OptionalInt.empty());
  }

  public static PeerPermissionsBlacklist create(final int maxSize) {
    return new PeerPermissionsBlacklist(OptionalInt.of(maxSize));
  }

  @Override
  public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
    return !blacklist.contains(remotePeer.getId());
  }

  public void add(final Peer peer) {
    if (blacklist.add(peer.getId())) {
      dispatchUpdate(true, Optional.of(Collections.singletonList(peer)));
    }
  }

  public void remove(final Peer peer) {
    if (blacklist.remove(peer.getId())) {
      dispatchUpdate(false, Optional.of(Collections.singletonList(peer)));
    }
  }

  public void add(final BytesValue peerId) {
    if (blacklist.add(peerId)) {
      dispatchUpdate(true, Optional.empty());
    }
  }

  public void remove(final BytesValue peerId) {
    if (blacklist.remove(peerId)) {
      dispatchUpdate(false, Optional.empty());
    }
  }

  @Override
  public void close() {
    blacklist.clear();
  }
}
