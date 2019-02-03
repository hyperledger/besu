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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetHeadersFromPeerByHashTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CheckpointHeaderManager<C> {

  private static final Logger LOG = LogManager.getLogger();

  private final Deque<BlockHeader> checkpointHeaders = new ConcurrentLinkedDeque<>();
  private final SynchronizerConfiguration config;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final ProtocolSchedule<C> protocolSchedule;
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  private int checkpointTimeouts = 0;

  public CheckpointHeaderManager(
      final SynchronizerConfiguration config,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final ProtocolSchedule<C> protocolSchedule,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.config = config;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.protocolSchedule = protocolSchedule;
    this.ethTasksTimer = ethTasksTimer;
  }

  public CompletableFuture<List<BlockHeader>> pullCheckpointHeaders(final SyncTarget syncTarget) {
    if (!shouldDownloadMoreCheckpoints()) {
      return CompletableFuture.completedFuture(getCheckpointsAwaitingImport());
    }

    final BlockHeader lastHeader =
        checkpointHeaders.size() > 0 ? checkpointHeaders.getLast() : syncTarget.commonAncestor();
    // Try to pull more checkpoint headers
    return getAdditionalCheckpointHeaders(syncTarget, lastHeader)
        .thenApply(
            additionalCheckpoints -> {
              if (!additionalCheckpoints.isEmpty()) {
                checkpointTimeouts = 0;
                checkpointHeaders.addAll(additionalCheckpoints);
                LOG.debug("Tracking {} checkpoint headers", checkpointHeaders.size());
              }
              return getCheckpointsAwaitingImport();
            });
  }

  protected CompletableFuture<List<BlockHeader>> getAdditionalCheckpointHeaders(
      final SyncTarget syncTarget, final BlockHeader lastHeader) {
    return requestAdditionalCheckpointHeaders(lastHeader, syncTarget)
        .handle(
            (headers, t) -> {
              t = ExceptionUtils.rootCause(t);
              if (t instanceof TimeoutException) {
                checkpointTimeouts++;
                return emptyList();
              } else if (t != null) {
                // An error occurred, so no new checkpoints to add.
                return emptyList();
              }
              if (headers.size() > 0
                  && checkpointHeaders.size() > 0
                  && checkpointHeaders.getLast().equals(headers.get(0))) {
                // Don't push header that is already tracked
                headers.remove(0);
              }
              if (headers.isEmpty()) {
                checkpointTimeouts++;
              }
              return headers;
            });
  }

  private CompletableFuture<List<BlockHeader>> requestAdditionalCheckpointHeaders(
      final BlockHeader lastHeader, final SyncTarget syncTarget) {
    LOG.debug("Requesting checkpoint headers from {}", lastHeader.getNumber());
    final int skip = config.downloaderChainSegmentSize() - 1;
    final int additionalHeaderCount =
        calculateAdditionalCheckpointHeadersToRequest(lastHeader, skip);
    if (additionalHeaderCount == 0) {
      return CompletableFuture.completedFuture(emptyList());
    }
    return GetHeadersFromPeerByHashTask.startingAtHash(
            protocolSchedule,
            ethContext,
            lastHeader.getHash(),
            lastHeader.getNumber(),
            // + 1 because lastHeader will be returned as well.
            additionalHeaderCount + 1,
            skip,
            ethTasksTimer)
        .assignPeer(syncTarget.peer())
        .run()
        .thenApply(PeerTaskResult::getResult);
  }

  protected int calculateAdditionalCheckpointHeadersToRequest(
      final BlockHeader lastHeader, final int skip) {
    return config.downloaderHeaderRequestSize();
  }

  protected boolean shouldDownloadMoreCheckpoints() {
    return checkpointHeaders.size() < config.downloaderHeaderRequestSize()
        && checkpointTimeouts < config.downloaderCheckpointTimeoutsPermitted();
  }

  public boolean checkpointsHaveTimedOut() {
    // We have no more checkpoints, and have been unable to pull any new checkpoints for
    // several cycles.
    return checkpointHeaders.size() == 0
        && checkpointTimeouts >= config.downloaderCheckpointTimeoutsPermitted();
  }

  public void clearSyncTarget() {
    checkpointTimeouts = 0;
    checkpointHeaders.clear();
  }

  public boolean clearImportedCheckpointHeaders() {
    final Blockchain blockchain = protocolContext.getBlockchain();
    // Update checkpoint headers to reflect if any checkpoints were imported.
    final List<BlockHeader> imported = new ArrayList<>();
    while (!checkpointHeaders.isEmpty()
        && blockchain.contains(checkpointHeaders.peekFirst().getHash())) {
      imported.add(checkpointHeaders.removeFirst());
    }
    final BlockHeader lastImportedCheckpointHeader = imported.get(imported.size() - 1);
    // The first checkpoint header is always present in the blockchain.
    checkpointHeaders.addFirst(lastImportedCheckpointHeader);
    syncState.setCommonAncestor(lastImportedCheckpointHeader);
    return imported.size() > 1;
  }

  public BlockHeader allCheckpointsImported() {
    final BlockHeader lastImportedCheckpoint = checkpointHeaders.getLast();
    checkpointHeaders.clear();
    return lastImportedCheckpoint;
  }

  private List<BlockHeader> getCheckpointsAwaitingImport() {
    return Lists.newArrayList(checkpointHeaders);
  }
}
