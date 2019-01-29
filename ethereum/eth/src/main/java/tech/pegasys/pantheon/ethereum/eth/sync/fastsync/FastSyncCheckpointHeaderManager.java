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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.CheckpointHeaderManager;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FastSyncCheckpointHeaderManager<C> extends CheckpointHeaderManager<C> {
  private final SynchronizerConfiguration config;
  private final BlockHeader pivotBlockHeader;

  public FastSyncCheckpointHeaderManager(
      final SynchronizerConfiguration config,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final ProtocolSchedule<C> protocolSchedule,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final BlockHeader pivotBlockHeader) {
    super(config, protocolContext, ethContext, syncState, protocolSchedule, ethTasksTimer);
    this.config = config;
    this.pivotBlockHeader = pivotBlockHeader;
  }

  @Override
  protected CompletableFuture<List<BlockHeader>> getAdditionalCheckpointHeaders(
      final SyncTarget syncTarget, final BlockHeader lastHeader) {
    return super.getAdditionalCheckpointHeaders(syncTarget, lastHeader)
        .thenApply(
            checkpointHeaders -> {
              final long lastSegmentEnd =
                  checkpointHeaders.isEmpty()
                      ? lastHeader.getNumber()
                      : checkpointHeaders.get(checkpointHeaders.size() - 1).getNumber();
              if (shouldDownloadMoreCheckpoints()
                  && nextChainSegmentIncludesPivotBlock(lastSegmentEnd)
                  && pivotBlockNotAlreadyIncluded(lastSegmentEnd)) {
                return concat(checkpointHeaders, pivotBlockHeader);
              }
              return checkpointHeaders;
            });
  }

  private List<BlockHeader> concat(
      final List<BlockHeader> checkpointHeaders, final BlockHeader value) {
    return Stream.concat(checkpointHeaders.stream(), Stream.of(value)).collect(Collectors.toList());
  }

  private boolean pivotBlockNotAlreadyIncluded(final long lastSegmentEnd) {
    return lastSegmentEnd < pivotBlockHeader.getNumber();
  }

  @Override
  protected int calculateAdditionalCheckpointHeadersToRequest(
      final BlockHeader lastHeader, final int skip) {
    final long startingBlockNumber = lastHeader.getNumber();
    final long blocksUntilPivotBlock = pivotBlockHeader.getNumber() - startingBlockNumber;

    final long toRequest = blocksUntilPivotBlock / (skip + 1);
    final int maximumAdditionalCheckpoints =
        super.calculateAdditionalCheckpointHeadersToRequest(lastHeader, skip);
    return (int) Math.min(maximumAdditionalCheckpoints, toRequest);
  }

  private boolean nextChainSegmentIncludesPivotBlock(final long lastSegmentEnd) {
    final long nextSegmentEnd = lastSegmentEnd + config.downloaderChainSegmentSize();
    return nextSegmentEnd >= pivotBlockHeader.getNumber();
  }
}
