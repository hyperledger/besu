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
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PosDownloadAndStoreSyncReceiptsStep
    implements Function<List<BlockHeader>, CompletableFuture<List<BlockHeader>>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PosDownloadAndStoreSyncReceiptsStep.class);

  private final ProtocolSchedule protocolSchedule;
  private final PeerTaskExecutor peerTaskExecutor;
  private final MutableBlockchain blockchain;

  public PosDownloadAndStoreSyncReceiptsStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final SynchronizerConfiguration synchronizerConfiguration,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    peerTaskExecutor = ethContext.getPeerTaskExecutor();
    blockchain = ethContext.getBlockchain();
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final List<BlockHeader> blockHeaders) {
    return CompletableFuture.runAsync(() -> getAndStoreReceipts(blockHeaders))
        .thenApply((__) -> blockHeaders);
  }

  private void getAndStoreReceipts(final List<BlockHeader> blockHeaders) {
    LOG.atDebug()
        .setMessage("Downloading {} receipts starting with {}")
        .addArgument(blockHeaders.size())
        .addArgument(blockHeaders.getFirst().getNumber())
        .log();

    final List<BlockHeader> headers = new ArrayList<>(blockHeaders);
    Map<BlockHeader, List<SyncTransactionReceipt>> getReceipts = new HashMap<>();
    do {
      GetSyncReceiptsFromPeerTask task = new GetSyncReceiptsFromPeerTask(headers, protocolSchedule);
      PeerTaskExecutorResult<Map<BlockHeader, List<SyncTransactionReceipt>>> getReceiptsResult =
          peerTaskExecutor.execute(task);
      if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
          && getReceiptsResult.result().isPresent()) {
        Map<BlockHeader, List<SyncTransactionReceipt>> taskResult =
            getReceiptsResult.result().get();
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
    final List<List<SyncTransactionReceipt>> receiptsList =
        getReceipts.values().stream().toList(); // one per block
    blockchain.appendSyncTransactionReceiptsForPoC(
        blockHeaders, receiptsList); // store all receipts for these headers
    LOG.atDebug()
        .setMessage("Block no. {} Stored receipts for {} blocks")
        .addArgument(blockHeaders.getLast().getNumber())
        .addArgument(blockHeaders.size())
        .log();
  }
}
