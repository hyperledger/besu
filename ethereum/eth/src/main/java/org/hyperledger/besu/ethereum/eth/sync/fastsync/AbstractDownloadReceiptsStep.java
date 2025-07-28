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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.tasks.GetReceiptsForHeadersTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDownloadReceiptsStep<B, BWR>
    implements Function<List<B>, CompletableFuture<List<BWR>>> {
  // B is the type of block being processed (Block, SyncBlock), BWR is the type of block with
  // receipts (BlockWithReceipts, SyncBlockWithReceipts)

  private static final Logger LOG = LoggerFactory.getLogger(AbstractDownloadReceiptsStep.class);

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final SynchronizerConfiguration synchronizerConfiguration;
  private final MetricsSystem metricsSystem;

  public AbstractDownloadReceiptsStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final SynchronizerConfiguration synchronizerConfiguration,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.synchronizerConfiguration = synchronizerConfiguration;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<BWR>> apply(final List<B> blocks) {
    final List<BlockHeader> headers = blocks.stream().map(this::getBlockHeader).collect(toList());
    if (synchronizerConfiguration.isPeerTaskSystemEnabled()) {
      return ethContext
          .getScheduler()
          .scheduleServiceTask(() -> getReceiptsWithPeerTaskSystem(headers))
          .thenApply((receipts) -> combineBlocksAndReceipts(blocks, receipts));
    } else {
      return GetReceiptsForHeadersTask.forHeaders(ethContext, headers, metricsSystem)
          .run()
          .thenApply((receipts) -> combineBlocksAndReceipts(blocks, receipts));
    }
  }

  abstract BlockHeader getBlockHeader(final B b);

  abstract List<BWR> combineBlocksAndReceipts(
      final List<B> blocks, final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader);

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>>
      getReceiptsWithPeerTaskSystem(final List<BlockHeader> headers) {
    final ArrayList<BlockHeader> originalBlockHeaders = new ArrayList<>(headers);
    Map<BlockHeader, List<TransactionReceipt>> getReceipts = HashMap.newHashMap(headers.size());
    try (OperationTimer.TimingContext ignored =
        metricsSystem
            .createLabelledTimer(
                BesuMetricCategory.SYNCHRONIZER, "task", "Internal processing tasks", "taskName")
            .labels(GetReceiptsForHeadersTask.class.getSimpleName())
            .startTimer()) {
      do {
        GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(headers, protocolSchedule);
        PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> getReceiptsResult =
            ethContext.getPeerTaskExecutor().execute(task);
        if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
            && getReceiptsResult.result().isPresent()) {
          Map<BlockHeader, List<TransactionReceipt>> taskResult = getReceiptsResult.result().get();
          taskResult
              .keySet()
              .forEach(
                  (blockHeader) ->
                      getReceipts.merge(
                          blockHeader,
                          taskResult.get(blockHeader),
                          (initialReceipts, newReceipts) -> {
                            throw new IllegalStateException(
                                "Unexpectedly got receipts for block header already populated!");
                          }));
          // remove all the headers we found receipts for
          headers.removeAll(getReceipts.keySet());
        }
        // repeat until all headers have receipts
      } while (!headers.isEmpty());
    }
    if (LOG.isTraceEnabled()) {
      for (BlockHeader blockHeader : originalBlockHeaders) {
        final List<TransactionReceipt> transactionReceipts = getReceipts.get(blockHeader);
        LOG.atTrace()
            .setMessage("{} receipts received for header {}")
            .addArgument(transactionReceipts == null ? 0 : transactionReceipts.size())
            .addArgument(blockHeader.getBlockHash())
            .log();
      }
    }
    return CompletableFuture.completedFuture(getReceipts);
  }
}
