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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DownloadReceiptsStepTest {

  private static ProtocolContext protocolContext;
  private static MutableBlockchain blockchain;

  private EthProtocolManager ethProtocolManager;
  private DownloadReceiptsStep downloadReceiptsStep;

  @BeforeClass
  public static void setUpClass() {
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    setupUtil.importFirstBlocks(20);
    protocolContext = setupUtil.getProtocolContext();
    blockchain = setupUtil.getBlockchain();
  }

  @Before
  public void setUp() {
    TransactionPool transactionPool = mock(TransactionPool.class);
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            () -> false,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            EthProtocolConfiguration.defaultConfig());
    downloadReceiptsStep =
        new DownloadReceiptsStep(ethProtocolManager.ethContext(), new NoOpMetricsSystem());
  }

  @Test
  public void shouldDownloadReceiptsForBlocks() {
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

  private Block block(final long number) {
    final BlockHeader header = blockchain.getBlockHeader(number).get();
    return new Block(header, blockchain.getBlockBody(header.getHash()).get());
  }

  private BlockWithReceipts blockWithReceipts(final long number) {
    final Block block = block(number);
    final List<TransactionReceipt> receipts = blockchain.getTxReceipts(block.getHash()).get();
    return new BlockWithReceipts(block, receipts);
  }
}
