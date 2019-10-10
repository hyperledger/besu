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

package org.hyperledger.besu.ethereum.eth.sync.state;

import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks the sync status of a node within the specific {@code syncTolerance}. The first event
 * emitted to any listener will be an out-of-sync event. If the node is in sync and remains in sync,
 * no events will be emitted to any listeners.
 */
class InSyncTracker {
  // Assume we're in sync to start, so that we'll broadcast our first event when we detect that
  // we're out of sync
  private final AtomicBoolean inSync = new AtomicBoolean(true);
  // If the local chain is no more than {@code syncTolerance} behind the estimated highest chain,
  // then the tracker considers this local node to be in sync
  private final long syncTolerance;

  private final Subscribers<InSyncListener> inSyncSubscribers = Subscribers.create();

  private InSyncTracker(final long syncTolerance) {
    this.syncTolerance = syncTolerance;
  }

  public static InSyncTracker create(final long syncTolerance) {
    return new InSyncTracker(syncTolerance);
  }

  public static boolean isInSync(
      final long localChainHeight,
      final Optional<Long> otherChainHeight,
      final long syncTolerance) {
    return otherChainHeight
        .map(peerHeight -> (peerHeight - localChainHeight) <= syncTolerance)
        .orElse(true);
  }

  synchronized void checkState(
      final long localChainHeight,
      final Optional<Long> syncTargetHeight,
      final Optional<Long> bestPeerHeight) {
    final boolean currentlyInSync =
        isInSync(localChainHeight, syncTargetHeight) && isInSync(localChainHeight, bestPeerHeight);
    if (inSync.compareAndSet(!currentlyInSync, currentlyInSync)) {
      // Sync status has changed, notify subscribers
      inSyncSubscribers.forEach(c -> c.onSyncStatusChanged(currentlyInSync));
    }
  }

  public synchronized long addInSyncListener(final InSyncListener subscriber) {
    if (!inSync.get()) {
      // If we're already out of sync, dispatch an event to the subscriber
      subscriber.onSyncStatusChanged(false);
    }
    return inSyncSubscribers.subscribe(subscriber);
  }

  public boolean removeInSyncListener(final long subscriptionId) {
    return inSyncSubscribers.unsubscribe(subscriptionId);
  }

  public int getListenerCount() {
    return inSyncSubscribers.getSubscriberCount();
  }

  private boolean isInSync(final long localChainHeight, final Optional<Long> otherChainHeight) {
    return isInSync(localChainHeight, otherChainHeight, syncTolerance);
  }
}
