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
package org.hyperledger.besu.ethereum.eth.sync.common;

import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration.ETH69_RECEIPT_CONFIGURATION;
import static org.hyperledger.besu.ethereum.eth.core.Utils.blocksToSyncBlocks;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.core.Utils;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ImportSyncBlocksStepTest {

  @Mock private ProtocolContext protocolContext;
  @Mock private MutableBlockchain blockchain;
  @Mock private SyncState syncState;
  private final BlockDataGenerator gen = new BlockDataGenerator();
  // the index of a block is its number
  private final List<Block> chain = gen.blockSequence(10);

  private ImportSyncBlocksStep importSyncBlocksStep;

  @BeforeEach
  public void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);

    importSyncBlocksStep =
        new ImportSyncBlocksStep(
            protocolContext, null, syncState, 0L, 10L, false, Optional.empty());
  }

  @Test
  public void shouldStoreBlocksAndMoveTheChainHeadOverThem() {
    final List<SyncBlockWithReceipts> batch = batch(1, 5);

    importSyncBlocksStep.accept(batch);

    verify(blockchain).unsafeStoreSyncBodiesAndReceipts(batch, false);
    verify(blockchain).unsafeAdvanceSyncChainHead(headersOf(batch));
    verify(syncState).setSyncProgress(0L, 5L, 10L);
  }

  @Test
  public void shouldKeepABatchOffTheChainUntilTheBatchBeforeItIsStored() {
    final List<SyncBlockWithReceipts> first = batch(1, 3);
    final List<SyncBlockWithReceipts> second = batch(4, 6);
    final List<SyncBlockWithReceipts> third = batch(7, 9);

    // the downloads of the later batches complete first
    importSyncBlocksStep.accept(third);
    importSyncBlocksStep.accept(second);

    verify(blockchain).unsafeStoreSyncBodiesAndReceipts(third, false);
    verify(blockchain).unsafeStoreSyncBodiesAndReceipts(second, false);
    verify(blockchain, never()).unsafeAdvanceSyncChainHead(any());
    verifyNoInteractions(syncState);

    importSyncBlocksStep.accept(first);

    final InOrder inOrder = inOrder(blockchain);
    inOrder.verify(blockchain).unsafeStoreSyncBodiesAndReceipts(first, false);
    inOrder.verify(blockchain).unsafeAdvanceSyncChainHead(headersOf(first));
    inOrder.verify(blockchain).unsafeAdvanceSyncChainHead(headersOf(second));
    inOrder.verify(blockchain).unsafeAdvanceSyncChainHead(headersOf(third));
    verify(syncState).setSyncProgress(0L, 3L, 10L);
    verify(syncState).setSyncProgress(0L, 6L, 10L);
    verify(syncState).setSyncProgress(0L, 9L, 10L);
  }

  @Test
  public void shouldMoveTheChainHeadOnlyAsFarAsBlocksAreStoredWithoutGap() {
    final List<SyncBlockWithReceipts> first = batch(1, 3);
    final List<SyncBlockWithReceipts> third = batch(7, 9);

    importSyncBlocksStep.accept(first);
    importSyncBlocksStep.accept(third);

    verify(blockchain).unsafeAdvanceSyncChainHead(headersOf(first));
    verify(blockchain, never()).unsafeAdvanceSyncChainHead(headersOf(third));
    verify(syncState).setSyncProgress(0L, 3L, 10L);
    verify(syncState, never()).setSyncProgress(0L, 9L, 10L);
  }

  /** The blocks with the given numbers, as a batch of the download. */
  private List<SyncBlockWithReceipts> batch(final int firstBlock, final int lastBlock) {
    final List<Block> realBlocks = chain.subList(firstBlock, lastBlock + 1);
    final List<SyncBlock> blocks = blocksToSyncBlocks(realBlocks);
    return blocks.stream()
        .map(
            block ->
                new SyncBlockWithReceipts(
                    block,
                    Utils.receiptsToSyncReceipts(
                        gen.receipts(realBlocks.get(blocks.indexOf(block))),
                        ETH69_RECEIPT_CONFIGURATION)))
        .collect(toList());
  }

  private static List<BlockHeader> headersOf(final List<SyncBlockWithReceipts> batch) {
    return batch.stream().map(SyncBlockWithReceipts::getHeader).toList();
  }
}
