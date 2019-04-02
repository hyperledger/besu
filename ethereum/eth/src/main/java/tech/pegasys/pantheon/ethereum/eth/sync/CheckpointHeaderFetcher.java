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

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public class CheckpointHeaderFetcher {

  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<?> protocolSchedule;
  private final EthContext ethContext;
  private final UnaryOperator<List<BlockHeader>> checkpointFilter;
  private final MetricsSystem metricsSystem;

  public CheckpointHeaderFetcher(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final UnaryOperator<List<BlockHeader>> checkpointFilter,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.checkpointFilter = checkpointFilter;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<List<BlockHeader>> getNextCheckpointHeaders(
      final EthPeer peer, final BlockHeader lastHeader) {
    final int skip = syncConfig.downloaderChainSegmentSize() - 1;
    final int additionalHeaderCount = syncConfig.downloaderHeaderRequestSize();
    return GetHeadersFromPeerByHashTask.startingAtHash(
            protocolSchedule,
            ethContext,
            lastHeader.getHash(),
            lastHeader.getNumber(),
            // + 1 because lastHeader will be returned as well.
            additionalHeaderCount + 1,
            skip,
            metricsSystem)
        .assignPeer(peer)
        .run()
        .thenApply(PeerTaskResult::getResult)
        .thenApply(
            headers -> checkpointFilter.apply(stripExistingCheckpointHeader(lastHeader, headers)));
  }

  private List<BlockHeader> stripExistingCheckpointHeader(
      final BlockHeader lastHeader, final List<BlockHeader> headers) {
    if (!headers.isEmpty() && headers.get(0).equals(lastHeader)) {
      return headers.subList(1, headers.size());
    }
    return headers;
  }
}
