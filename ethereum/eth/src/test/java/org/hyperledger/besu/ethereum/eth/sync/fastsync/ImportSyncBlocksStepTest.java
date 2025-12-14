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

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ImportSyncBlocksStepTest {

  @Mock private ProtocolContext protocolContext;
  @Mock private MutableBlockchain blockchain;
  @Mock private BlockHeader pivotHeader;
  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final boolean transactionIndexingEnabled = false;

  private ImportSyncBlocksStep importSyncBlocksStep;

  @BeforeEach
  public void setUp() {
    importSyncBlocksStep =
        new ImportSyncBlocksStep(protocolContext, null, pivotHeader, transactionIndexingEnabled);
  }

  @Test
  public void shouldImportBlocks() {
    final List<Block> realBlocks = gen.blockSequence(5);
    final List<SyncBlock> blocks = blockToSyncBlock(realBlocks);
    final AtomicInteger i = new AtomicInteger(0);
    final List<SyncBlockWithReceipts> blocksWithReceipts =
        blocks.stream()
            .map(
                block ->
                    new SyncBlockWithReceipts(
                        block,
                        gen.receipts(realBlocks.get(i.getAndIncrement())).stream()
                            .map(
                                (tr) ->
                                    new SyncTransactionReceipt(
                                        RLP.encode(
                                            (out) ->
                                                TransactionReceiptEncoder.writeTo(
                                                    tr,
                                                    out,
                                                    TransactionReceiptEncodingConfiguration
                                                        .DEFAULT))))
                            .toList()))
            .collect(toList());

    Mockito.when(protocolContext.getBlockchain()).thenReturn(blockchain);

    importSyncBlocksStep.accept(blocksWithReceipts);

    Mockito.verify(protocolContext).getBlockchain();
    Mockito.verify(blockchain)
        .unsafeImportSyncBodyAndReceipts(blocksWithReceipts, transactionIndexingEnabled);
  }

  private List<SyncBlock> blockToSyncBlock(final List<Block> blocks) {
    final ArrayList<SyncBlock> syncBlocks = new ArrayList<>(blocks.size());
    for (final Block block : blocks) {
      BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
      block.getBody().writeWrappedBodyTo(rlpOutput);
      final BytesValueRLPInput input = new BytesValueRLPInput(rlpOutput.encoded(), false);
      final SyncBlockBody syncBlockBody =
          SyncBlockBody.readWrappedBodyFrom(
              input, false, new DefaultProtocolSchedule(Optional.of(BigInteger.ONE)));
      syncBlocks.add(new SyncBlock(block.getHeader(), syncBlockBody));
    }
    return syncBlocks;
  }
}
