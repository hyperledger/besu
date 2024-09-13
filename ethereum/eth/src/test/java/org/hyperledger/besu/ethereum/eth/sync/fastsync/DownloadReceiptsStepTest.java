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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DownloadReceiptsStepTest {
  @Mock private PeerTaskExecutor peerTaskExecutor;
  private AutoCloseable mockitoCloseable;
  private DownloadReceiptsStep downloadReceiptsStep;

  @BeforeEach
  public void setUp() {
    mockitoCloseable = MockitoAnnotations.openMocks(this);
    downloadReceiptsStep = new DownloadReceiptsStep(peerTaskExecutor);
  }

  @AfterEach
  public void afterEachTest() throws Exception {
    mockitoCloseable.close();
  }

  @Test
  public void shouldDownloadReceiptsForBlocks() throws ExecutionException, InterruptedException {
    final List<Block> blocks = asList(block(), block(), block(), block());

    Map<BlockHeader, List<TransactionReceipt>> receiptsMap = new HashMap<>();
    blocks.forEach(
        (b) -> receiptsMap.put(b.getHeader(), List.of(Mockito.mock(TransactionReceipt.class))));
    PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> peerTaskResult =
        new PeerTaskExecutorResult<>(receiptsMap, PeerTaskExecutorResponseCode.SUCCESS);
    Mockito.when(peerTaskExecutor.executeAsync(Mockito.any(GetReceiptsFromPeerTask.class)))
        .thenReturn(CompletableFuture.completedFuture(peerTaskResult));

    final CompletableFuture<List<BlockWithReceipts>> result = downloadReceiptsStep.apply(blocks);

    assertThat(result.get().get(0).getBlock()).isEqualTo(blocks.get(0));
    assertThat(result.get().get(0).getReceipts().size()).isEqualTo(1);
    assertThat(result.get().get(1).getBlock()).isEqualTo(blocks.get(1));
    assertThat(result.get().get(1).getReceipts().size()).isEqualTo(1);
    assertThat(result.get().get(2).getBlock()).isEqualTo(blocks.get(2));
    assertThat(result.get().get(2).getReceipts().size()).isEqualTo(1);
    assertThat(result.get().get(3).getBlock()).isEqualTo(blocks.get(3));
    assertThat(result.get().get(3).getReceipts().size()).isEqualTo(1);
  }

  private Block block() {
    final Block block = Mockito.mock(Block.class);
    final BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    when(block.getHeader()).thenAnswer((invocationOnMock) -> blockHeader);
    return block;
  }
}
