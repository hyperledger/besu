/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.FULL;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.LIGHT;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FastImportBlocksStepTest {

  @Mock private ProtocolSchedule<Void> protocolSchedule;
  @Mock private ProtocolSpec<Void> protocolSpec;
  @Mock private ProtocolContext<Void> protocolContext;
  @Mock private BlockImporter<Void> blockImporter;
  @Mock private ValidationPolicy validationPolicy;
  @Mock private ValidationPolicy ommerValidationPolicy;
  private final BlockDataGenerator gen = new BlockDataGenerator();

  private FastImportBlocksStep<Void> importBlocksStep;

  @Before
  public void setUp() {
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);
    when(validationPolicy.getValidationModeForNextBlock()).thenReturn(FULL);
    when(ommerValidationPolicy.getValidationModeForNextBlock()).thenReturn(LIGHT);

    importBlocksStep =
        new FastImportBlocksStep<>(
            protocolSchedule, protocolContext, validationPolicy, ommerValidationPolicy);
  }

  @Test
  public void shouldImportBlocks() {
    final List<Block> blocks = gen.blockSequence(5);
    final List<BlockWithReceipts> blocksWithReceipts =
        blocks.stream()
            .map(block -> new BlockWithReceipts(block, gen.receipts(block)))
            .collect(toList());

    for (final BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      when(blockImporter.fastImportBlock(
              protocolContext,
              blockWithReceipts.getBlock(),
              blockWithReceipts.getReceipts(),
              FULL,
              LIGHT))
          .thenReturn(true);
    }
    importBlocksStep.accept(blocksWithReceipts);

    for (final BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      verify(protocolSchedule).getByBlockNumber(blockWithReceipts.getNumber());
    }
    verify(validationPolicy, times(blocks.size())).getValidationModeForNextBlock();
  }

  @Test
  public void shouldThrowExceptionWhenValidationFails() {
    final Block block = gen.block();
    final BlockWithReceipts blockWithReceipts = new BlockWithReceipts(block, gen.receipts(block));

    when(blockImporter.fastImportBlock(
            protocolContext, block, blockWithReceipts.getReceipts(), FULL, LIGHT))
        .thenReturn(false);
    assertThatThrownBy(() -> importBlocksStep.accept(singletonList(blockWithReceipts)))
        .isInstanceOf(InvalidBlockException.class);
  }
}
