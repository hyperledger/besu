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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByNumberTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.FutureUtils;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PivotBlockRetriever<C> {

  private static final Logger LOG = LogManager.getLogger();
  static final int MAX_QUERY_RETRIES_PER_PEER = 3;
  static final int MAX_PIVOT_BLOCK_RESETS = 5;

  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final ProtocolSchedule<C> protocolSchedule;

  // The number of peers we need to query to confirm our pivot block
  private final int peersToQuery;
  // The max times to push the pivot block number back when peers can't agree on a pivot
  private final int maxPivotBlockResets;
  // How far to push back the pivot block when we retry on pivot disagreement
  private final long pivotBlockNumberResetDelta;
  // The number of times we've retried with a pushed back pivot block number
  private int pivotBlockResets = 0;
  // The current pivot block number, gets pushed back if peers disagree on the pivot block
  long pivotBlockNumber;

  private final CompletableFuture<FastSyncState> result = new CompletableFuture<>();
  private final Collection<CompletableFuture<?>> runningQueries = new ConcurrentLinkedQueue<>();
  private final Map<BytesValue, RetryingGetHeaderFromPeerByNumberTask> pivotBlockQueriesByPeerId =
      new ConcurrentHashMap<>();
  private final Map<BlockHeader, AtomicInteger> pivotBlockVotes = new ConcurrentHashMap<>();

  PivotBlockRetriever(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final long pivotBlockNumber,
      final int peersToQuery,
      final long pivotBlockNumberResetDelta,
      final int maxPivotBlockResets) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;

    this.pivotBlockNumber = pivotBlockNumber;
    this.peersToQuery = peersToQuery;
    this.pivotBlockNumberResetDelta = pivotBlockNumberResetDelta;
    this.maxPivotBlockResets = maxPivotBlockResets;
  }

  public PivotBlockRetriever(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final long pivotBlockNumber,
      final int peersToQuery,
      final long pivotBlockNumberResetDelta) {
    this(
        protocolSchedule,
        ethContext,
        metricsSystem,
        pivotBlockNumber,
        peersToQuery,
        pivotBlockNumberResetDelta,
        MAX_PIVOT_BLOCK_RESETS);
  }

  @SuppressWarnings("rawtypes")
  public CompletableFuture<FastSyncState> downloadPivotBlockHeader() {
    LOG.info(
        "Retrieve pivot block {} and confirm with at least {} peers.",
        pivotBlockNumber,
        peersToQuery);
    queryPeers(pivotBlockNumber);

    return result;
  }

  @SuppressWarnings("rawtypes")
  private void queryPeers(final long pivotBlockNumber) {
    for (int i = 0; i < peersToQuery; i++) {
      final CompletableFuture<?> query =
          executePivotQuery(pivotBlockNumber).whenComplete(this::processReceivedHeader);
      runningQueries.add(query);
    }
  }

  private void processReceivedHeader(final BlockHeader blockHeader, final Throwable throwable) {
    if (throwable != null) {
      LOG.error("Encountered error while requesting pivot block header", throwable);
      cancelQueries();
      result.completeExceptionally(throwable);
      return;
    }

    pivotBlockVotes.putIfAbsent(blockHeader, new AtomicInteger(0));
    final int votes = pivotBlockVotes.get(blockHeader).incrementAndGet();

    if (pivotBlockVotes.keySet().size() > 1) {
      handleContestedPivotBlock();
    } else if (votes >= peersToQuery) {
      // We've received the required number of votes and have selected our pivot block
      LOG.info("Confirmed pivot block at {}: {}", pivotBlockNumber, blockHeader.getHash());
      result.complete(new FastSyncState(blockHeader));
    } else {
      LOG.info(
          "Received {} confirmation(s) for pivot block header {}: {}",
          votes,
          pivotBlockNumber,
          blockHeader.getHash());
    }
  }

  private void handleContestedPivotBlock() {
    LOG.info("Received conflicting pivot blocks for {}: {}", pivotBlockNumber, votesToString());

    cancelQueries();
    pivotBlockResets += 1;
    pivotBlockNumber -= pivotBlockNumberResetDelta;

    if (pivotBlockResets > maxPivotBlockResets
        || pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
      LOG.info("Max retries reached, cancel pivot block download.");
      // Pivot block selection has failed
      result.completeExceptionally(
          new FastSyncException(FastSyncError.PIVOT_BLOCK_HEADER_MISMATCH));
      return;
    } else {
      LOG.info("Move pivot block back to {} and retry.", pivotBlockNumber);
    }

    queryPeers(pivotBlockNumber);
  }

  private String votesToString() {
    return pivotBlockVotes.entrySet().stream()
        .map(e -> e.getKey().getHash() + " (" + e.getValue().get() + ")")
        .collect(Collectors.joining(","));
  }

  private void cancelQueries() {
    runningQueries.forEach(f -> f.cancel(true));
    pivotBlockQueriesByPeerId.values().forEach(EthTask::cancel);

    runningQueries.clear();
    pivotBlockQueriesByPeerId.clear();
    pivotBlockVotes.clear();
  }

  private CompletableFuture<BlockHeader> executePivotQuery(final long pivotBlockNumber) {
    Optional<RetryingGetHeaderFromPeerByNumberTask> query = createPivotQuery(pivotBlockNumber);
    final CompletableFuture<BlockHeader> pivotHeaderFuture;
    if (query.isPresent()) {
      final CompletableFuture<BlockHeader> headerQuery = query.get().getHeader();
      pivotHeaderFuture =
          FutureUtils.exceptionallyCompose(
              headerQuery, (error) -> executePivotQuery(pivotBlockNumber));
    } else {
      // We were unable to find a peer to query, wait and try again
      LOG.debug("No peer currently available to query for block {}.", pivotBlockNumber);
      pivotHeaderFuture =
          ethContext
              .getScheduler()
              .timeout(WaitForPeerTask.create(ethContext, metricsSystem), Duration.ofSeconds(5))
              .handle((err, res) -> null) // Ignore result
              .thenCompose(res -> executePivotQuery(pivotBlockNumber));
    }

    return pivotHeaderFuture;
  }

  private Optional<RetryingGetHeaderFromPeerByNumberTask> createPivotQuery(
      final long pivotBlockNumber) {
    return ethContext
        .getEthPeers()
        .streamBestPeers()
        .filter(p -> p.chainState().getEstimatedHeight() >= pivotBlockNumber)
        .filter(EthPeer::isFullyValidated)
        .filter(p -> !pivotBlockQueriesByPeerId.keySet().contains(p.nodeId()))
        .findFirst()
        .flatMap(this::createGetHeaderTask);
  }

  Optional<RetryingGetHeaderFromPeerByNumberTask> createGetHeaderTask(final EthPeer peer) {
    final RetryingGetHeaderFromPeerByNumberTask task =
        RetryingGetHeaderFromPeerByNumberTask.forSingleNumber(
            protocolSchedule,
            ethContext,
            metricsSystem,
            pivotBlockNumber,
            MAX_QUERY_RETRIES_PER_PEER);
    task.assignPeer(peer);
    final RetryingGetHeaderFromPeerByNumberTask preexistingTask =
        pivotBlockQueriesByPeerId.putIfAbsent(peer.nodeId(), task);

    if (preexistingTask == null) {
      // If we haven't already queried this peer, return the newly created task
      LOG.debug(
          "Query peer {}... for block {}.",
          peer.nodeId().toString().substring(0, 8),
          pivotBlockNumber);
      return Optional.of(task);
    } else {
      // Otherwise, we'll have to try again later
      return Optional.empty();
    }
  }
}
