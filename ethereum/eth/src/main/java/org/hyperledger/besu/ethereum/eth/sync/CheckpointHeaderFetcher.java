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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotProvider;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointHeaderFetcher {
  private static final Logger LOG = LoggerFactory.getLogger(CheckpointHeaderFetcher.class);

  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  // The checkpoint we're aiming to reach at the end of this sync.
  private final PivotProvider pivotProvider;
  private final MetricsSystem metricsSystem;

  public CheckpointHeaderFetcher(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this(
        syncConfig,
        protocolSchedule,
        ethContext,
        metricsSystem,
        () -> new FastSyncState().getPivotBlockHeader());
  }

  public CheckpointHeaderFetcher(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final PivotProvider pivotProvider) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.pivotProvider = pivotProvider;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<List<BlockHeader>> getNextCheckpointHeaders(
      final EthPeer peer, final BlockHeader previousCheckpointHeader) {
    final int skip = syncConfig.getDownloaderChainSegmentSize() - 1;
    final int maximumHeaderRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final long previousCheckpointNumber = previousCheckpointHeader.getNumber();

    final int additionalHeaderCount;
    final Optional<BlockHeader> finalCheckpointHeader = pivotProvider.maybeProvidePivot();
    if (finalCheckpointHeader.isPresent()) {
      final BlockHeader targetHeader = finalCheckpointHeader.get();
      final long blocksUntilTarget = targetHeader.getNumber() - previousCheckpointNumber;
      if (blocksUntilTarget <= 0) {
        return completedFuture(emptyList());
      }
      final long maxHeadersToRequest = blocksUntilTarget / (skip + 1);
      additionalHeaderCount = (int) Math.min(maxHeadersToRequest, maximumHeaderRequestSize);
      if (additionalHeaderCount == 0) {
        return completedFuture(singletonList(targetHeader));
      }
    } else {
      additionalHeaderCount = maximumHeaderRequestSize;
    }

    return requestHeaders(peer, previousCheckpointHeader, additionalHeaderCount, skip);
  }

  private CompletableFuture<List<BlockHeader>> requestHeaders(
      final EthPeer peer,
      final BlockHeader referenceHeader,
      final int headerCount,
      final int skip) {
    LOG.debug(
        "Requesting {} checkpoint headers, starting from {}, {} blocks apart",
        headerCount,
        referenceHeader.getNumber(),
        skip);
    return GetHeadersFromPeerByHashTask.startingAtHash(
            protocolSchedule,
            ethContext,
            referenceHeader.getHash(),
            referenceHeader.getNumber(),
            // + 1 because lastHeader will be returned as well.
            headerCount + 1,
            skip,
            metricsSystem)
        .assignPeer(peer)
        .run()
        .thenApply(PeerTaskResult::getResult)
        .thenApply(headers -> stripExistingCheckpointHeader(referenceHeader, headers));
  }

  private List<BlockHeader> stripExistingCheckpointHeader(
      final BlockHeader lastHeader, final List<BlockHeader> headers) {
    if (!headers.isEmpty() && headers.get(0).equals(lastHeader)) {
      return headers.subList(1, headers.size());
    }
    return headers;
  }

  public boolean nextCheckpointEndsAtChainHead(
      final EthPeer peer, final BlockHeader previousCheckpointHeader) {
    final Optional<BlockHeader> finalCheckpointHeader = pivotProvider.maybeProvidePivot();
    if (finalCheckpointHeader.isPresent()) {
      return false;
    }
    final int skip = syncConfig.getDownloaderChainSegmentSize() - 1;
    final long peerEstimatedHeight = peer.chainState().getEstimatedHeight();
    final long previousCheckpointNumber = previousCheckpointHeader.getNumber();
    return previousCheckpointNumber + skip >= peerEstimatedHeight;
  }
}
