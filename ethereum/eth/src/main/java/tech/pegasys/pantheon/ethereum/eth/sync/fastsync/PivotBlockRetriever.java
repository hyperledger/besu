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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByNumberTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PivotBlockRetriever<C> {

  private static final Logger LOG = LogManager.getLogger();
  static final int MAX_PIVOT_BLOCK_RETRIES = 3;
  private final long pivotBlockNumber;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final ProtocolSchedule<C> protocolSchedule;
  private final Map<BlockHeader, AtomicInteger> confirmationsByBlockNumber =
      new ConcurrentHashMap<>();
  private final CompletableFuture<FastSyncState> result = new CompletableFuture<>();
  private final Collection<RetryingGetHeaderFromPeerByNumberTask> getHeaderTasks =
      new ConcurrentLinkedQueue<>();

  public PivotBlockRetriever(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final long pivotBlockNumber) {
    this.pivotBlockNumber = pivotBlockNumber;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.protocolSchedule = protocolSchedule;
  }

  @SuppressWarnings("rawtypes")
  public CompletableFuture<FastSyncState> downloadPivotBlockHeader() {
    final CompletableFuture[] requestFutures = requestHeaderFromAllPeers();

    CompletableFuture.allOf(requestFutures)
        .thenRun(
            () -> {
              // All requests have completed but we still haven't reached agreement on a header.
              result.completeExceptionally(
                  new FastSyncException(FastSyncError.PIVOT_BLOCK_HEADER_MISMATCH));
            });
    return result;
  }

  @SuppressWarnings("rawtypes")
  private CompletableFuture[] requestHeaderFromAllPeers() {
    final List<EthPeer> peersToQuery =
        ethContext
            .getEthPeers()
            .streamAvailablePeers()
            .filter(peer -> peer.chainState().getEstimatedHeight() >= pivotBlockNumber)
            .collect(Collectors.toList());

    final int confirmationsRequired = peersToQuery.size() / 2 + 1;
    return peersToQuery.stream()
        .map(
            peer -> {
              final RetryingGetHeaderFromPeerByNumberTask getHeaderTask = createGetHeaderTask(peer);
              getHeaderTasks.add(getHeaderTask);
              return ethContext
                  .getScheduler()
                  .scheduleSyncWorkerTask(getHeaderTask::getHeader)
                  .thenAccept(header -> countHeader(header, confirmationsRequired));
            })
        .toArray(CompletableFuture[]::new);
  }

  private RetryingGetHeaderFromPeerByNumberTask createGetHeaderTask(final EthPeer peer) {
    final RetryingGetHeaderFromPeerByNumberTask task =
        RetryingGetHeaderFromPeerByNumberTask.forSingleNumber(
            protocolSchedule, ethContext, metricsSystem, pivotBlockNumber, MAX_PIVOT_BLOCK_RETRIES);
    task.assignPeer(peer);
    return task;
  }

  private void countHeader(final BlockHeader header, final int confirmationsRequired) {
    final int confirmations =
        confirmationsByBlockNumber
            .computeIfAbsent(header, key -> new AtomicInteger(0))
            .incrementAndGet();
    LOG.debug(
        "Received header {} which now has {} confirmations out of {} required.",
        header.getHash(),
        confirmations,
        confirmationsRequired);
    if (confirmations >= confirmationsRequired) {
      LOG.info(
          "Confirmed pivot block hash {} with {} confirmations", header.getHash(), confirmations);
      result.complete(new FastSyncState(header));
      getHeaderTasks.forEach(RetryingGetHeaderFromPeerByNumberTask::cancel);
    }
  }
}
