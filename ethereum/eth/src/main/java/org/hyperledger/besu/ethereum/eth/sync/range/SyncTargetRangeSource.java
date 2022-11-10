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
package org.hyperledger.besu.ethereum.eth.sync.range;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncTargetRangeSource implements Iterator<SyncTargetRange> {
  private static final Logger LOG = LoggerFactory.getLogger(SyncTargetRangeSource.class);

  private final RangeHeadersFetcher fetcher;
  private final SyncTargetChecker syncTargetChecker;
  private final EthPeer peer;
  private final Duration newHeaderWaitDuration;
  private final SyncTerminationCondition terminationCondition;

  private final Queue<SyncTargetRange> retrievedRanges = new ArrayDeque<>();
  private BlockHeader lastRangeEnd;
  private boolean reachedEndOfRanges = false;
  private Optional<CompletableFuture<List<BlockHeader>>> pendingRequests = Optional.empty();

  public SyncTargetRangeSource(
      final RangeHeadersFetcher fetcher,
      final SyncTargetChecker syncTargetChecker,
      final EthPeer peer,
      final BlockHeader commonAncestor,
      final SyncTerminationCondition terminationCondition) {
    this(
        fetcher,
        syncTargetChecker,
        peer,
        commonAncestor,
        Duration.ofSeconds(5),
        terminationCondition);
  }

  public SyncTargetRangeSource(
      final RangeHeadersFetcher fetcher,
      final SyncTargetChecker syncTargetChecker,
      final EthPeer peer,
      final BlockHeader commonAncestor,
      final Duration newHeaderWaitDuration,
      final SyncTerminationCondition terminationCondition) {
    this.fetcher = fetcher;
    this.syncTargetChecker = syncTargetChecker;
    this.peer = peer;
    this.lastRangeEnd = commonAncestor;
    this.newHeaderWaitDuration = newHeaderWaitDuration;
    this.terminationCondition = terminationCondition;
  }

  @Override
  public boolean hasNext() {
    return terminationCondition.shouldContinueDownload()
        && (!retrievedRanges.isEmpty()
            || (syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, lastRangeEnd)
                && !reachedEndOfRanges));
  }

  @Override
  public SyncTargetRange next() {
    if (!retrievedRanges.isEmpty()) {
      return retrievedRanges.poll();
    }
    if (pendingRequests.isPresent()) {
      return getRangeFromPendingRequest();
    }
    if (reachedEndOfRanges) {
      return null;
    }
    if (fetcher.nextRangeEndsAtChainHead(peer, lastRangeEnd)) {
      reachedEndOfRanges = true;
      return new SyncTargetRange(peer, lastRangeEnd);
    }
    pendingRequests = Optional.of(getNextRangeHeaders());
    return getRangeFromPendingRequest();
  }

  private CompletableFuture<List<BlockHeader>> getNextRangeHeaders() {
    return fetcher
        .getNextRangeHeaders(peer, lastRangeEnd)
        .exceptionally(
            error -> {
              LOG.debug("Failed to retrieve range headers", error);
              return emptyList();
            });
  }

  private SyncTargetRange getRangeFromPendingRequest() {
    final CompletableFuture<List<BlockHeader>> pendingRequest = this.pendingRequests.get();
    try {
      final List<BlockHeader> newHeaders =
          pendingRequest.get(newHeaderWaitDuration.toMillis(), MILLISECONDS);
      this.pendingRequests = Optional.empty();
      for (final BlockHeader header : newHeaders) {
        retrievedRanges.add(new SyncTargetRange(peer, lastRangeEnd, header));
        lastRangeEnd = header;
      }
      return retrievedRanges.poll();
    } catch (final InterruptedException e) {
      LOG.trace("Interrupted while waiting for new range headers", e);
      return null;
    } catch (final ExecutionException e) {
      LOG.debug("Failed to retrieve new range headers", e);
      this.pendingRequests = Optional.empty();
      return null;
    } catch (final TimeoutException e) {
      return null;
    }
  }

  public interface SyncTargetChecker {
    boolean shouldContinueDownloadingFromSyncTarget(EthPeer peer, BlockHeader lastRangeHeader);
  }
}
