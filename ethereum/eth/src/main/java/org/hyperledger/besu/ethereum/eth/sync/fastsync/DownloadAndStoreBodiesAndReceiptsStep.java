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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.tasks.CompleteSyncBlocksTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.SimpleNoCopyRlpEncoder;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadAndStoreBodiesAndReceiptsStep
    implements Function<List<BlockHeader>, CompletableFuture<List<BlockHeader>>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(DownloadAndStoreBodiesAndReceiptsStep.class);

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MutableBlockchain blockchain;
  private final Boolean transactionIndexingEnabled;
  private final MetricsSystem metricsSystem;
  private final PeerTaskExecutor peerTaskExecutor;
  private final EthScheduler ethScheduler;
  private final SyncTransactionReceiptEncoder syncTransactionReceiptEncoder =
      new SyncTransactionReceiptEncoder(new SimpleNoCopyRlpEncoder());

  public DownloadAndStoreBodiesAndReceiptsStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MutableBlockchain blockchain,
      final Boolean transactionIndexingEnabled,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.blockchain = blockchain;
    this.transactionIndexingEnabled = transactionIndexingEnabled;
    this.metricsSystem = metricsSystem;
    this.peerTaskExecutor = ethContext.getPeerTaskExecutor();
    this.ethScheduler = ethContext.getScheduler();
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final List<BlockHeader> blockHeaders) {
    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              final CompletableFuture<Void> bodiesFuture = getAndStoreSyncBodies(blockHeaders);
              final CompletableFuture<Void> receiptsFuture = getAndStoreReceipts(blockHeaders);
              return CompletableFuture.allOf(bodiesFuture, receiptsFuture)
                  .thenApply(v -> blockHeaders);
            });
  }

  private CompletableFuture<Void> getAndStoreSyncBodies(final List<BlockHeader> headers) {
    return CompleteSyncBlocksTask.forHeaders(protocolSchedule, ethContext, headers, metricsSystem)
        .run()
        .thenAccept(
            bodies -> blockchain.unsafeImportSyncBodies(bodies, transactionIndexingEnabled));
  }

  private CompletableFuture<Void> getAndStoreReceipts(
      final List<BlockHeader> originalBlockHeaders) {
    List<BlockHeader> headers = new ArrayList<>(originalBlockHeaders);
    return ethScheduler
        .scheduleServiceTask(
            () -> {
              Map<BlockHeader, List<SyncTransactionReceipt>> receiptsByBlockHeader =
                  new HashMap<>();
              while (!headers.isEmpty()) {
                Map<BlockHeader, List<SyncTransactionReceipt>> receipts = getReceipts(headers);
                headers.removeAll(receipts.keySet());
                for (BlockHeader blockHeader : receipts.keySet()) {
                  receiptsByBlockHeader.put(blockHeader, receipts.get(blockHeader));
                }
              }
              if (LOG.isTraceEnabled()) {
                for (BlockHeader blockHeader : originalBlockHeaders) {
                  final List<SyncTransactionReceipt> transactionReceipts =
                      receiptsByBlockHeader.get(blockHeader);
                  LOG.atTrace()
                      .setMessage("{} receipts received for header {}")
                      .addArgument(transactionReceipts == null ? 0 : transactionReceipts.size())
                      .addArgument(blockHeader.getBlockHash())
                      .log();
                }
              }
              return CompletableFuture.completedFuture(receiptsByBlockHeader);
            })
        .thenAccept(blockchain::unsafeImportSyncReceipts);
  }

  private Map<BlockHeader, List<SyncTransactionReceipt>> getReceipts(
      final List<BlockHeader> headers) {
    GetSyncReceiptsFromPeerTask task =
        new GetSyncReceiptsFromPeerTask(headers, protocolSchedule, syncTransactionReceiptEncoder);
    PeerTaskExecutorResult<Map<BlockHeader, List<SyncTransactionReceipt>>> getReceiptsResult =
        peerTaskExecutor.execute(task);
    if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
        && getReceiptsResult.result().isPresent()) {
      return getReceiptsResult.result().get();
    }
    return Collections.emptyMap();
  }
}
