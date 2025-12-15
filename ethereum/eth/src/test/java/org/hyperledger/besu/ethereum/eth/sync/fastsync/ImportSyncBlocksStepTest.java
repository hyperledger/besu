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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ImportSyncBlocksStepTest {

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private ProtocolContext protocolContext;
  @Mock private BlockImporter blockImporter;
  @Mock private BlockHeader pivotHeader;
  private final BlockDataGenerator gen = new BlockDataGenerator();

  private ImportSyncBlocksStep importSyncBlocksStep;

  @BeforeEach
  public void setUp() {
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);

    importSyncBlocksStep =
        new ImportSyncBlocksStep(
            protocolSchedule, protocolContext, null, null, 0, pivotHeader, false);
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
                        block, gen.receipts(realBlocks.get(i.getAndIncrement()))))
            .collect(toList());

    for (final SyncBlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      when(blockImporter.importSyncBlockForSyncing(
              protocolContext,
              blockWithReceipts.getBlock(),
              blockWithReceipts.getReceipts(),
              false))
          .thenReturn(new BlockImportResult(true));
    }
    importSyncBlocksStep.accept(blocksWithReceipts);

    for (final SyncBlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      verify(protocolSchedule).getByBlockHeader(blockWithReceipts.getHeader());
    }
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
