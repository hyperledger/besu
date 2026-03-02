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
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncBlockBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadSyncBodiesStep
    implements Function<List<BlockHeader>, CompletableFuture<List<SyncBlock>>> {

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;

  public DownloadSyncBodiesStep(
      final ProtocolSchedule protocolSchedule, final EthContext ethContext) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
  }

  @Override
  public CompletableFuture<List<SyncBlock>> apply(final List<BlockHeader> blockHeaders) {
    return ethContext
        .getScheduler()
        .scheduleServiceTask(() -> getSyncBodiesWithPeerTaskSystem(blockHeaders));
  }

  private CompletableFuture<List<SyncBlock>> getSyncBodiesWithPeerTaskSystem(
      final List<BlockHeader> headers) {
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
    return CompletableFuture.completedFuture(syncBlocks);
  }
}
