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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CheckpointRangeSource implements Iterator<CheckpointRange> {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration RETRY_DELAY_DURATION = Duration.ofSeconds(2);

  private final CheckpointHeaderFetcher checkpointFetcher;
  private final SyncTargetChecker syncTargetChecker;
  private final EthPeer peer;
  private final EthScheduler ethScheduler;
  private final int checkpointTimeoutsPermitted;
  private final Duration newHeaderWaitDuration;

  private final Queue<CheckpointRange> retrievedRanges = new ArrayDeque<>();
  private BlockHeader lastRangeEnd;
  private boolean reachedEndOfCheckpoints = false;
  private Optional<CompletableFuture<List<BlockHeader>>> pendingCheckpointsRequest =
      Optional.empty();
  private int requestFailureCount = 0;

  public CheckpointRangeSource(
      final CheckpointHeaderFetcher checkpointFetcher,
      final SyncTargetChecker syncTargetChecker,
      final EthScheduler ethScheduler,
      final EthPeer peer,
      final BlockHeader commonAncestor,
      final int checkpointTimeoutsPermitted) {
    this(
        checkpointFetcher,
        syncTargetChecker,
        ethScheduler,
        peer,
        commonAncestor,
        checkpointTimeoutsPermitted,
        Duration.ofSeconds(5));
  }

  CheckpointRangeSource(
      final CheckpointHeaderFetcher checkpointFetcher,
      final SyncTargetChecker syncTargetChecker,
      final EthScheduler ethScheduler,
      final EthPeer peer,
      final BlockHeader commonAncestor,
      final int checkpointTimeoutsPermitted,
      final Duration newHeaderWaitDuration) {
    this.checkpointFetcher = checkpointFetcher;
    this.syncTargetChecker = syncTargetChecker;
    this.ethScheduler = ethScheduler;
    this.peer = peer;
    this.lastRangeEnd = commonAncestor;
    this.checkpointTimeoutsPermitted = checkpointTimeoutsPermitted;
    this.newHeaderWaitDuration = newHeaderWaitDuration;
  }

  @Override
  public boolean hasNext() {
    return !retrievedRanges.isEmpty()
        || (requestFailureCount < checkpointTimeoutsPermitted
            && syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, lastRangeEnd)
            && !reachedEndOfCheckpoints);
  }

  @Override
  public CheckpointRange next() {
    if (!retrievedRanges.isEmpty()) {
      return retrievedRanges.poll();
    }
    if (pendingCheckpointsRequest.isPresent()) {
      return getCheckpointRangeFromPendingRequest();
    }
    if (reachedEndOfCheckpoints) {
      return null;
    }
    if (checkpointFetcher.nextCheckpointEndsAtChainHead(peer, lastRangeEnd)) {
      reachedEndOfCheckpoints = true;
      return new CheckpointRange(peer, lastRangeEnd);
    }
    pendingCheckpointsRequest = Optional.of(getNextCheckpointHeaders());
    return getCheckpointRangeFromPendingRequest();
  }

  private CompletableFuture<List<BlockHeader>> getNextCheckpointHeaders() {
    return checkpointFetcher
        .getNextCheckpointHeaders(peer, lastRangeEnd)
        .exceptionally(
            error -> {
              LOG.debug("Failed to retrieve checkpoint headers", error);
              return emptyList();
            })
        .thenCompose(
            checkpoints -> checkpoints.isEmpty() ? pauseBriefly() : completedFuture(checkpoints));
  }

  /**
   * Pause after failing to get new checkpoints to prevent requesting new checkpoint headers in a
   * tight loop.
   *
   * @return a future that after the pause completes with an empty list.
   */
  private CompletableFuture<List<BlockHeader>> pauseBriefly() {
    return ethScheduler.scheduleFutureTask(
        () -> completedFuture(emptyList()), RETRY_DELAY_DURATION);
  }

  private CheckpointRange getCheckpointRangeFromPendingRequest() {
    final CompletableFuture<List<BlockHeader>> pendingRequest = pendingCheckpointsRequest.get();
    try {
      final List<BlockHeader> newCheckpointHeaders =
          pendingRequest.get(newHeaderWaitDuration.toMillis(), MILLISECONDS);
      pendingCheckpointsRequest = Optional.empty();
      if (newCheckpointHeaders.isEmpty()) {
        requestFailureCount++;
      } else {
        requestFailureCount = 0;
      }
      for (final BlockHeader checkpointHeader : newCheckpointHeaders) {
        retrievedRanges.add(new CheckpointRange(peer, lastRangeEnd, checkpointHeader));
        lastRangeEnd = checkpointHeader;
      }
      return retrievedRanges.poll();
    } catch (final InterruptedException e) {
      LOG.trace("Interrupted while waiting for new checkpoint headers", e);
      return null;
    } catch (final ExecutionException e) {
      LOG.debug("Failed to retrieve new checkpoint headers", e);
      pendingCheckpointsRequest = Optional.empty();
      requestFailureCount++;
      return null;
    } catch (final TimeoutException e) {
      return null;
    }
  }

  public interface SyncTargetChecker {
    boolean shouldContinueDownloadingFromSyncTarget(EthPeer peer, BlockHeader lastCheckpointHeader);
  }
}
