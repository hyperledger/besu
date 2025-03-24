/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.possync;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

public class PosSyncSource implements Iterator<SyncTargetNumberRange> {
  private final long checkpointTarget;
  private final Supplier<Long> syncTarget;
  private final boolean backwards;
  private final int headerRequestSize;

  private Optional<SyncTargetNumberRange> maybeLastRange = Optional.empty();

  public PosSyncSource(
      final long checkpointTarget,
      final Supplier<Long> syncTarget,
      final int headerRequestSize,
      final boolean backwards) {
    this.checkpointTarget = checkpointTarget;
    this.syncTarget = syncTarget;
    this.backwards = backwards;
    this.headerRequestSize = headerRequestSize;
  }

  @Override
  public boolean hasNext() {
    return !hasReachedCheckpointTarget();
  }

  @Override
  public SyncTargetNumberRange next() {
    if (maybeLastRange.isEmpty()) {
      final SyncTargetNumberRange firstRange = createFirstRange();
      maybeLastRange = Optional.of(firstRange);
      return firstRange;
    } else if (hasReachedCheckpointTarget()) {
      return null;
    } else {
      final SyncTargetNumberRange lastRange = maybeLastRange.get();
      final SyncTargetNumberRange nextRange = createNextRange(lastRange);
      maybeLastRange = Optional.of(nextRange);
      return nextRange;
    }
  }

  private SyncTargetNumberRange createFirstRange() {
    if (backwards) {
      return new SyncTargetNumberRange(syncTarget.get() - headerRequestSize, syncTarget.get());
    } else {
      final long startBlockNumber = Math.max(checkpointTarget, 1);
      return new SyncTargetNumberRange(startBlockNumber, startBlockNumber + headerRequestSize);
    }
  }

  private SyncTargetNumberRange createNextRange(final SyncTargetNumberRange lastRange) {
    if (backwards) {
      final long lowerBlockNumber = Math.max(lastRange.lowerBlockNumber() - headerRequestSize, 0);
      return new SyncTargetNumberRange(lowerBlockNumber, lastRange.lowerBlockNumber());
    } else {
      return new SyncTargetNumberRange(
          lastRange.upperBlockNumber(), lastRange.upperBlockNumber() + headerRequestSize);
    }
  }

  private boolean hasReachedCheckpointTarget() {
    if (backwards) {
      return maybeLastRange.map(r -> r.lowerBlockNumber() <= checkpointTarget).orElse(false);
    } else {
      return maybeLastRange.map(r -> r.upperBlockNumber() >= syncTarget.get()).orElse(false);
    }
  }
}
