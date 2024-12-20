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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DownloadReceiptsStepTest {

  private static ProtocolContext protocolContext;
  private static ProtocolSchedule protocolSchedule;
  private static MutableBlockchain blockchain;

  private PeerTaskExecutor peerTaskExecutor;
  private EthProtocolManager ethProtocolManager;

  @BeforeAll
  public static void setUpClass() {
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    setupUtil.importFirstBlocks(20);
    protocolContext = setupUtil.getProtocolContext();
    protocolSchedule = setupUtil.getProtocolSchedule();
    blockchain = setupUtil.getBlockchain();
  }

  @BeforeEach
  public void setUp() {
    peerTaskExecutor = mock(PeerTaskExecutor.class);
    TransactionPool transactionPool = mock(TransactionPool.class);
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(ProtocolScheduleFixture.MAINNET)
            .setBlockchain(blockchain)
            .setEthScheduler(new DeterministicEthScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
  }

  @Test
  public void shouldDownloadReceiptsForBlocks() {
    DownloadReceiptsStep downloadReceiptsStep =
        new DownloadReceiptsStep(
            protocolSchedule,
            ethProtocolManager.ethContext(),
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(false).build(),
            new NoOpMetricsSystem());
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final List<Block> blocks = asList(block(1), block(2), block(3), block(4));
    final CompletableFuture<List<BlockWithReceipts>> result = downloadReceiptsStep.apply(blocks);

    peer.respond(RespondingEthPeer.blockchainResponder(blockchain));

    assertThat(result)
        .isCompletedWithValue(
            asList(
                blockWithReceipts(1),
                blockWithReceipts(2),
                blockWithReceipts(3),
                blockWithReceipts(4)));
  }

  @Test
  public void shouldDownloadReceiptsForBlocksUsingPeerTaskSystem()
      throws ExecutionException, InterruptedException {
    DownloadReceiptsStep downloadReceiptsStep =
        new DownloadReceiptsStep(
            protocolSchedule,
            ethProtocolManager.ethContext(),
            SynchronizerConfiguration.builder().isPeerTaskSystemEnabled(true).build(),
            new NoOpMetricsSystem());

    final List<Block> blocks = asList(mockBlock(), mockBlock(), mockBlock(), mockBlock());
    Map<BlockHeader, List<TransactionReceipt>> receiptsMap = new HashMap<>();
    blocks.forEach(
        (b) -> receiptsMap.put(b.getHeader(), List.of(Mockito.mock(TransactionReceipt.class))));
    PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> peerTaskResult =
        new PeerTaskExecutorResult<>(
            Optional.of(receiptsMap), PeerTaskExecutorResponseCode.SUCCESS, Optional.empty());
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetReceiptsFromPeerTask.class)))
        .thenReturn(peerTaskResult);

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

  private Block block(final long number) {
    final BlockHeader header = blockchain.getBlockHeader(number).get();
    return new Block(header, blockchain.getBlockBody(header.getHash()).get());
  }

  private BlockWithReceipts blockWithReceipts(final long number) {
    final Block block = block(number);
    final List<TransactionReceipt> receipts = blockchain.getTxReceipts(block.getHash()).get();
    return new BlockWithReceipts(block, receipts);
  }

  private Block mockBlock() {
    final Block block = Mockito.mock(Block.class);
    final BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    Mockito.when(block.getHeader()).thenAnswer((invocationOnMock) -> blockHeader);
    Mockito.when(blockHeader.getReceiptsRoot()).thenReturn(Hash.fromHexStringLenient("DEADBEEF"));
    final BlockBody blockBody = Mockito.mock(BlockBody.class);
    Mockito.when(block.getBody()).thenAnswer((invocationOnMock) -> blockBody);
    Mockito.when(blockBody.getTransactions())
        .thenAnswer((invocationOnMock) -> List.of(Mockito.mock(Transaction.class)));
    return block;
  }
}
