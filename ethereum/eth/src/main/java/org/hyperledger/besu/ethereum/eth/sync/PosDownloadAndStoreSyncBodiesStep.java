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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.tasks.CompleteSyncBlocksTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PosDownloadAndStoreSyncBodiesStep
    implements Function<List<BlockHeader>, CompletableFuture<List<BlockHeader>>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PosDownloadAndStoreSyncBodiesStep.class);

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public PosDownloadAndStoreSyncBodiesStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final SynchronizerConfiguration syncConfig) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final List<BlockHeader> blockHeaders) {

    LOG.atDebug()
        .setMessage("Downloading {} block headers starting with {}")
        .addArgument(blockHeaders.size())
        .addArgument(blockHeaders.getFirst().getNumber())
        .log();
    // for now only use the legacy peer tasks
    final CompleteSyncBlocksTask syncBlocksTask =
        CompleteSyncBlocksTask.forHeaders(
            protocolSchedule, ethContext, blockHeaders, metricsSystem);

    return syncBlocksTask
        .run()
        .exceptionally(
            th -> {
              LOG.info(
                  "Exception while downloading sync blocks: "
                      + blockHeaders.getFirst().getHash()
                      + " first of "
                      + blockHeaders.size(),
                  th);
              throw new RuntimeException(th);
            })
        .thenApply(
            (sbList) -> {
              // store blocks in the database, no TX indexing.
              ethContext.getBlockchain().appendSyncBlocksForPoC(sbList);
              LOG.atDebug()
                  .setMessage("Stored {} Sync blocks for up to block no {}")
                  .addArgument(sbList.size())
                  .addArgument(blockHeaders.getLast().getNumber())
                  .log();
              return blockHeaders;
            });
  }
}
