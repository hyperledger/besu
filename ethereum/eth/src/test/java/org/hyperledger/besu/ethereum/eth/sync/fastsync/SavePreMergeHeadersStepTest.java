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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.SavePreMergeHeadersStep;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavePreMergeHeadersStepTest {
  private MutableBlockchain blockchain;
  private SavePreMergeHeadersStep savePreMergeHeadersStep;
  private static final long MERGE_BLOCK_NUMBER = 1000;

  @BeforeEach
  void setUp() {
    blockchain = mock(MutableBlockchain.class);
    savePreMergeHeadersStep = new SavePreMergeHeadersStep(blockchain, MERGE_BLOCK_NUMBER);
  }

  @Test
  void shouldSaveFullBlockForMergeBlock() {
    BlockHeader blockHeader = createMockBlockHeader(MERGE_BLOCK_NUMBER);
    Difficulty difficulty = mock(Difficulty.class);
    when(blockchain.calculateTotalDifficulty(blockHeader)).thenReturn(difficulty);

    Stream<BlockHeader> nextProcessingStateInput = savePreMergeHeadersStep.apply(blockHeader);

    assertEquals(1, nextProcessingStateInput.count());
    verify(blockchain, never()).unsafeStoreHeader(blockHeader, difficulty);
  }

  @Test
  void shouldNotStoreOnlyBlockHeaderWhenPostMergeBlock() {
    BlockHeader blockHeader = createMockBlockHeader(MERGE_BLOCK_NUMBER + 1);

    Stream<BlockHeader> nextProcessingStateInput = savePreMergeHeadersStep.apply(blockHeader);

    assertEquals(1, nextProcessingStateInput.count());
    verify(blockchain, never()).unsafeStoreHeader(any(), any());
  }

  @Test
  void shouldNotSaveIfCheckpointIsGenesisBlock() {
    savePreMergeHeadersStep =
        new SavePreMergeHeadersStep(blockchain, BlockHeader.GENESIS_BLOCK_NUMBER);

    BlockHeader block0 = createMockBlockHeader(0);
    BlockHeader block1 = createMockBlockHeader(1);

    List<BlockHeader> nextProcessingStateInput = savePreMergeHeadersStep.apply(block0).toList();
    assertEquals(1, nextProcessingStateInput.size());
    assertEquals(nextProcessingStateInput.getFirst(), block0);

    nextProcessingStateInput = savePreMergeHeadersStep.apply(block1).toList();
    assertEquals(1, nextProcessingStateInput.size());
    assertEquals(nextProcessingStateInput.getFirst(), block1);

    verify(blockchain, never()).unsafeStoreHeader(any(), any());
  }

  @Test
  void shouldStoreOnlyBlockHeaderWhenPreMergeBlock() {
    BlockHeader blockHeader = createMockBlockHeader(MERGE_BLOCK_NUMBER - 1);
    Difficulty difficulty = mock(Difficulty.class);
    when(blockchain.calculateTotalDifficulty(blockHeader)).thenReturn(difficulty);

    Stream<BlockHeader> nextProcessingStateInput = savePreMergeHeadersStep.apply(blockHeader);

    assertEquals(0, nextProcessingStateInput.count());
    verify(blockchain).unsafeStoreHeader(blockHeader, difficulty);
  }

  private BlockHeader createMockBlockHeader(final long blockNumber) {
    BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    return blockHeader;
  }
}
