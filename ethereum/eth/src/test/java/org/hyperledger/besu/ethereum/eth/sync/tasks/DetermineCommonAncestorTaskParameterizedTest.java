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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DetermineCommonAncestorTaskParameterizedTest {
  private final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.MAINNET;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private static Block genesisBlock;
  private static MutableBlockchain localBlockchain;
  private static final int chainHeight = 50;
  private final int headerRequestSize;
  private final int commonAncestorHeight;

  private MutableBlockchain remoteBlockchain;

  public DetermineCommonAncestorTaskParameterizedTest(
      final int headerRequestSize, final int commonAncestorHeight) {
    this.headerRequestSize = headerRequestSize;
    this.commonAncestorHeight = commonAncestorHeight;
  }

  @BeforeClass
  public static void setupClass() {
    genesisBlock = blockDataGenerator.genesisBlock();
    localBlockchain = createInMemoryBlockchain(genesisBlock);

    // Setup local chain
    for (int i = 1; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);
      localBlockchain.appendBlock(block, receipts);
    }
  }

  @Before
  public void setup() {
    remoteBlockchain = createInMemoryBlockchain(genesisBlock);
  }

  @Parameters(name = "requestSize={0}, commonAncestor={1}")
  public static Collection<Object[]> parameters() throws IOException {
    final int[] requestSizes = {5, 12, chainHeight, chainHeight * 2};
    final List<Object[]> params = new ArrayList<>();
    for (final int requestSize : requestSizes) {
      for (int i = 0; i <= chainHeight; i++) {
        params.add(new Object[] {requestSize, i});
      }
    }
    return params;
  }

  @Test
  public void searchesAgainstNetwork() {
    BlockHeader commonHeader = genesisBlock.getHeader();
    for (long i = 1; i <= commonAncestorHeight; i++) {
      commonHeader = localBlockchain.getBlockHeader(i).get();
      final List<TransactionReceipt> receipts =
          localBlockchain.getTxReceipts(commonHeader.getHash()).get();
      final BlockBody commonBody = localBlockchain.getBlockBody(commonHeader.getHash()).get();
      remoteBlockchain.appendBlock(new Block(commonHeader, commonBody), receipts);
    }

    // Remaining blocks are disparate...
    for (long i = commonAncestorHeight + 1L; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions localOptions =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block localBlock = blockDataGenerator.block(localOptions);
      final List<TransactionReceipt> localReceipts = blockDataGenerator.receipts(localBlock);
      localBlockchain.appendBlock(localBlock, localReceipts);

      final BlockDataGenerator.BlockOptions remoteOptions =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(Difficulty.ONE) // differentiator
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block remoteBlock = blockDataGenerator.block(remoteOptions);
      final List<TransactionReceipt> remoteReceipts = blockDataGenerator.receipts(remoteBlock);
      remoteBlockchain.appendBlock(remoteBlock, remoteReceipts);
    }

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            localBlockchain,
            worldStateArchive,
            mock(TransactionPool.class),
            EthProtocolConfiguration.defaultConfig());
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final AtomicReference<BlockHeader> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final EthContext ethContext = ethProtocolManager.ethContext();
    final ProtocolContext protocolContext =
        new ProtocolContext(localBlockchain, worldStateArchive, null);

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            headerRequestSize,
            metricsSystem);

    final CompletableFuture<BlockHeader> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    future.whenComplete(
        (response, error) -> {
          actualResult.set(response);
          done.compareAndSet(false, true);
        });

    assertThat(actualResult.get()).isNotNull();
    assertThat(actualResult.get().getHash())
        .isEqualTo(MainnetBlockHeaderFunctions.createHash(commonHeader));
  }
}
