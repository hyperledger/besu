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
package org.hyperledger.besu.ethereum.p2p.discovery;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController;

import com.google.common.base.MoreObjects;

/** An abstract event emitted from the peer discovery layer. */
public abstract class PeerDiscoveryEvent {
  private final DiscoveryPeer peer;
  private final long timestamp;

  private PeerDiscoveryEvent(final DiscoveryPeer peer, final long timestamp) {
    this.peer = peer;
    this.timestamp = timestamp;
  }

  public DiscoveryPeer getPeer() {
    return peer;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("peer", peer)
        .add("timestamp", timestamp)
        .toString();
  }

  /**
   * An event that is dispatched whenever we bond with a new peer. See Javadoc on <code>
   * PeerDiscoveryController</code> to understand when this happens.
   *
   * <p>{@link PeerDiscoveryController}
   */
  public static class PeerBondedEvent extends PeerDiscoveryEvent {
    public PeerBondedEvent(final DiscoveryPeer peer, final long timestamp) {
      super(peer, timestamp);
    }
  }

  /**
   * An event that is dispatched whenever we drop a peer from the peer table. See Javadoc on <code>
   * PeerDiscoveryController</code> to understand when this happens.
   *
   * <p>{@link PeerDiscoveryController}
   */
  public static class PeerDroppedEvent extends PeerDiscoveryEvent {
    public PeerDroppedEvent(final DiscoveryPeer peer, final long timestamp) {
      super(peer, timestamp);
    }
  }
}
