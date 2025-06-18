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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BlockSizeBlockValidatorTest {
  private final BlockProcessor blockProcessor = mock(BlockProcessor.class);
  private final BlockHeaderValidator blockHeaderValidator = mock(BlockHeaderValidator.class);
  private final BlockBodyValidator blockBodyValidator = mock(BlockBodyValidator.class);
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();

  @Test
  void validationFailsForBlockGreaterThanMaxBlockSize() {
    BadBlockManager badBlockManager = new BadBlockManager();
    when(protocolContext.getBadBlockManager()).thenReturn(badBlockManager);

    final Transaction transaction =
        generator.transaction(Bytes.random(BlockSizeBlockValidator.MAX_RLP_BLOCK_SIZE + 1));
    BlockDataGenerator.BlockOptions blockOptions =
        new BlockDataGenerator.BlockOptions().setBlockNumber(1).addTransaction(transaction);
    Block block = generator.block(blockOptions);

    final BlockSizeBlockValidator blockSizeBlockValidator =
        new BlockSizeBlockValidator(blockHeaderValidator, blockBodyValidator, blockProcessor);

    BlockProcessingResult result =
        blockSizeBlockValidator.validateAndProcessBlock(
            protocolContext,
            block,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(badBlockManager.getBadBlock(block.getHash())).isPresent();
    assertThat(badBlockManager.getBadBlocks()).containsExactly(block);
  }

  @Test
  void validationSuccessfulForBlockLessThanMaxBlockSize() {
    var blockchainSetupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);
    blockchainSetupUtil.importAllBlocks();
    final BadBlockManager badBlockManager =
        blockchainSetupUtil.getProtocolContext().getBadBlockManager();
    final MutableBlockchain blockchain = spy(blockchainSetupUtil.getBlockchain());
    final BlockProcessingResult successfulProcessingResult =
        new BlockProcessingResult(Optional.empty(), false);
    when(protocolContext.getBadBlockManager()).thenReturn(badBlockManager);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getWorldStateArchive()).thenReturn(blockchainSetupUtil.getWorldArchive());
    when(blockHeaderValidator.validateHeader(any(), any(), any())).thenReturn(true);
    when(blockHeaderValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    when(blockProcessor.processBlock(eq(protocolContext), any(), any(), any()))
        .thenReturn(successfulProcessingResult);
    when(blockBodyValidator.validateBody(any(), any(), any(), any(), any(), any()))
        .thenReturn(true);

    final Block block = blockchainSetupUtil.getBlock(2);
    final BlockSizeBlockValidator blockSizeBlockValidator =
        new BlockSizeBlockValidator(blockHeaderValidator, blockBodyValidator, blockProcessor);

    BlockProcessingResult result =
        blockSizeBlockValidator.validateAndProcessBlock(
            protocolContext,
            block,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    assertThat(result.isSuccessful()).isTrue();
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }
}
