/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncBlockBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DownloadSyncBodiesStepTest {

  private @Mock ProtocolSchedule protocolSchedule;
  private @Mock ProtocolSpec protocolSpec;
  private @Mock EthContext ethContext;
  private @Mock PeerTaskExecutor peerTaskExecutor;

  private DownloadSyncBodiesStep downloadSyncBodiesStep;

  @BeforeEach
  public void beforeTest() {
    Mockito.when(protocolSchedule.getByBlockHeader(Mockito.any())).thenReturn(protocolSpec);
    Mockito.when(protocolSpec.isPoS()).thenReturn(true);

    Mockito.when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);
    Mockito.when(ethContext.getScheduler()).thenReturn(new DeterministicEthScheduler());

    downloadSyncBodiesStep = new DownloadSyncBodiesStep(protocolSchedule, ethContext);
  }

  @Test
  public void testApply() throws ExecutionException, InterruptedException {
    List<BlockHeader> headersForBodies = List.of(mockBlockHeader(1), mockBlockHeader(2));

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetSyncBlockBodiesFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(mockSyncBlock(), mockSyncBlock())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()));

    CompletableFuture<List<SyncBlock>> result = downloadSyncBodiesStep.apply(headersForBodies);

    Assertions.assertTrue(result.isDone());
    Assertions.assertEquals(2, result.get().size());
  }

  @Test
  public void testApplyWithMultipleRequests() throws ExecutionException, InterruptedException {
    List<BlockHeader> headersForBodies = List.of(mockBlockHeader(1), mockBlockHeader(2));

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetSyncBlockBodiesFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(mockSyncBlock())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(mockSyncBlock())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Collections.emptyList()));

    CompletableFuture<List<SyncBlock>> result = downloadSyncBodiesStep.apply(headersForBodies);

    Assertions.assertTrue(result.isDone());
    Assertions.assertEquals(2, result.get().size());
  }

  private BlockHeader mockBlockHeader(final long blockNumber) {
    BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    Mockito.when(blockHeader.getNumber()).thenReturn(blockNumber);

    return blockHeader;
  }

  private SyncBlock mockSyncBlock() {
    SyncBlock syncBlock = Mockito.mock(SyncBlock.class);
    return syncBlock;
  }
}
