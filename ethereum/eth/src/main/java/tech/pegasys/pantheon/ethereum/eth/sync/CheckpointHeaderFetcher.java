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
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CheckpointHeaderFetcher {

  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<?> protocolSchedule;
  private final EthContext ethContext;
  private final Optional<BlockHeader> lastCheckpointHeader;
  private final MetricsSystem metricsSystem;

  public CheckpointHeaderFetcher(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final Optional<BlockHeader> lastCheckpointHeader,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.lastCheckpointHeader = lastCheckpointHeader;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<List<BlockHeader>> getNextCheckpointHeaders(
      final EthPeer peer, final BlockHeader lastHeader) {
    final int skip = syncConfig.downloaderChainSegmentSize() - 1;
    final int maximumHeaderRequestSize = syncConfig.downloaderHeaderRequestSize();

    final int additionalHeaderCount;
    if (lastCheckpointHeader.isPresent()) {
      final BlockHeader targetHeader = lastCheckpointHeader.get();
      final long blocksUntilTarget = targetHeader.getNumber() - lastHeader.getNumber();
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

    return requestHeaders(peer, lastHeader, additionalHeaderCount, skip);
  }

  private CompletableFuture<List<BlockHeader>> requestHeaders(
      final EthPeer peer,
      final BlockHeader referenceHeader,
      final int headerCount,
      final int skip) {
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
}
