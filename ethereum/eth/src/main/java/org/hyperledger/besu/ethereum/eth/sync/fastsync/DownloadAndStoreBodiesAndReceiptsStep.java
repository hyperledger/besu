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
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncBlockBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.tasks.GetReceiptsForHeadersTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadAndStoreBodiesAndReceiptsStep
    implements Function<List<BlockHeader>, CompletableFuture<List<BlockHeader>>> {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MutableBlockchain blockchain;
  private final Boolean transactionIndexingEnabled;
  private final MetricsSystem metricsSystem;

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
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final List<BlockHeader> blockHeaders) {
    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              getAndStoreSyncBodies(blockHeaders);
              getAndStoreReceipts(List.copyOf(blockHeaders));
              return CompletableFuture.completedFuture(blockHeaders);
            });
  }

  private void getAndStoreSyncBodies(final List<BlockHeader> headers) {
    final int numSyncBlocksToGet = headers.size();
    final List<SyncBlock> syncBlocks = new ArrayList<>(numSyncBlocksToGet);
    do {
      final List<BlockHeader> headersForBodiesStillToGet =
          headers.subList(syncBlocks.size(), numSyncBlocksToGet);
      GetSyncBlockBodiesFromPeerTask task =
          new GetSyncBlockBodiesFromPeerTask(headersForBodiesStillToGet, protocolSchedule);
      PeerTaskExecutorResult<List<SyncBlock>> result =
          ethContext.getPeerTaskExecutor().execute(task);
      if (result.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
          && result.result().isPresent()) {
        List<SyncBlock> taskResult = result.result().get();
        syncBlocks.addAll(taskResult);
      }
      // repeat until all sync blocks have been downloaded
    } while (syncBlocks.size() < numSyncBlocksToGet);
    blockchain.unsafeImportSyncBodies(syncBlocks, transactionIndexingEnabled);
  }

  private void getAndStoreReceipts(final List<BlockHeader> headers) {
    GetReceiptsForHeadersTask.forHeaders(ethContext, headers, metricsSystem)
        .run()
        .thenAccept(blockchain::unsafeImportReceipts);
  }
}
