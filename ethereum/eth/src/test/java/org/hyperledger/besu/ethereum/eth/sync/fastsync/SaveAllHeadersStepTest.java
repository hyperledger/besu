/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SaveAllHeadersStep.
 *
 * <p>Tests the boundary validation logic that ensures out-of-order header ranges connect correctly
 * using in-memory hash maps. Also tests header storage and progress tracking.
 */
class SaveAllHeadersStepTest {

  private MutableBlockchain blockchain;
  private FastSyncState fastSyncState;
  private FastSyncStateStorage fastSyncStateStorage;
  private SaveAllHeadersStep step;
  private static final Hash PIVOT_BLOCK_HASH = Hash.hash(Bytes.ofUnsignedLong(100));
  private static final long PIVOT_BLOCK = 100L;

  @BeforeEach
  void setUp() {
    blockchain = mock(MutableBlockchain.class);
    fastSyncState = mock(FastSyncState.class);
    fastSyncStateStorage = mock(FastSyncStateStorage.class);

    when(blockchain.calculateTotalDifficulty(any())).thenReturn(Difficulty.ONE);
    when(fastSyncState.getPivotBlockNumber()).thenReturn(java.util.OptionalLong.of(PIVOT_BLOCK));
    when(fastSyncState.getLowestBlockHeaderDownloaded()).thenReturn(java.util.OptionalLong.empty());

    step =
        new SaveAllHeadersStep(
            blockchain,
            new NoOpMetricsSystem(),
            PIVOT_BLOCK_HASH,
            fastSyncState,
            fastSyncStateStorage);
  }

  @Test
  void shouldSaveAllHeadersInBatch() {
    // Given: step with mocked blockchain
    final List<BlockHeader> headers = createHeaderChain(90, 10); // 90 to 81

    // When: apply() with list of 10 headers
    final Stream<Void> result = step.apply(headers);

    // Then: unsafeStoreHeader() called 10 times
    assertThat(result).isNotNull();
    verify(blockchain, times(10)).unsafeStoreHeader(any(), any());
  }

  @Test
  void shouldValidateLowerBoundaryWhenPreviousRangeExists() {
    // Given: step that already processed range [80-71]
    final List<BlockHeader> previousRange = createHeaderChain(80, 10); // blocks 80, 79, ..., 71
    step.apply(previousRange);
    // This stores: highestBlockHashes(80) and lowestBlockParentHashes(71)

    // When: apply() with range [90-81] where block 81's parent hash matches block 80's hash
    final Hash block80Hash = previousRange.getFirst().getHash(); // Hash of block 80
    final List<BlockHeader> currentRange = createHeaderChainWithLowestParent(90, 10, block80Hash);

    // Then: validation succeeds because block 81's parentHash == highestBlockHashes(80)
    assertThat(step.apply(currentRange)).isNotNull();
  }

  @Test
  void shouldFailValidationWhenLowerBoundaryMismatch() {
    // Given: step that processed range [80-71]
    final List<BlockHeader> previousRange = createHeaderChain(80, 10);
    step.apply(previousRange);

    // When: apply() with range [90-81] where block 81's parent hash doesn't match block 80's hash
    final List<BlockHeader> currentRange = createHeaderChainWithLowestParent(90, 10, Hash.ZERO);

    // Then: throws InvalidBlockException
    assertThatThrownBy(() -> step.apply(currentRange))
        .isInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Batch boundary validation failed");
  }

  @Test
  void shouldValidateUpperBoundaryWhenNextRangeExists() {
    // Given: step that already processed HIGHER range [100-91]
    final List<BlockHeader> higherRange = createHeaderChain(100, 10); // blocks 100, 99, ..., 91
    step.apply(higherRange);
    // This stores: lowestBlockParentHashes(91) = parent_of_91 and highestBlockHashes(100)

    // When: apply() with LOWER range [90-81] where block 90's hash matches the stored parent_of_91
    final Hash parent91 = higherRange.get(9).getParentHash(); // parent of block 91
    final List<BlockHeader> currentRange = createHeaderChainWithHighestHash(90, 10, parent91);

    // Then: validation succeeds because block 90's hash == lowestBlockParentHashes(91)
    assertThat(step.apply(currentRange)).isNotNull();
  }

  @Test
  void shouldFailValidationWhenUpperBoundaryMismatch() {
    // Given: step that processed HIGHER range [100-91]
    final List<BlockHeader> higherRange = createHeaderChain(100, 10);
    step.apply(higherRange);
    // This stores lowestBlockParentHashes(91) = parent_of_91

    // When: apply() with LOWER range [90-81] where block 90's hash doesn't match parent_of_91
    final List<BlockHeader> currentRange = createHeaderChainWithHighestHash(90, 10, Hash.ZERO);

    // Then: throws InvalidBlockException
    assertThatThrownBy(() -> step.apply(currentRange))
        .isInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Batch boundary validation failed");
  }

  @Test
  void shouldHandleOutOfOrderRanges() {
    // Given: step receives ranges out of order: [100-91], [70-61], then [90-81] to connect them
    final List<BlockHeader> range1 = createHeaderChain(100, 10); // blocks 100-91
    final List<BlockHeader> range3 = createHeaderChain(70, 10); // blocks 70-61

    step.apply(range1);
    step.apply(range3);
    // Range1 stores: highestBlockHashes(100) and lowestBlockParentHashes(91)
    // Range3 stores: highestBlockHashes(70) and lowestBlockParentHashes(61)

    // When: applying middle range [90-81] that connects both
    // Block 81's parentHash should match block 80's hash (not stored, validation skipped)
    // Block 90's hash should match lowestBlockParentHashes(91) from range1
    final Hash block91ExpectedParent = range1.get(9).getParentHash(); // parent of block 91
    final List<BlockHeader> range2 =
        createHeaderChainWithHighestHash(90, 10, block91ExpectedParent);

    // Then: all validate correctly despite out-of-order processing
    assertThat(step.apply(range2)).isNotNull();
    verify(blockchain, times(30)).unsafeStoreHeader(any(), any());
  }

  @Test
  void shouldUpdateLowestSeenBlock() {
    // Given: step initialized at pivot 100
    assertThat(step.getLowestSeenBlock()).isEqualTo(PIVOT_BLOCK);

    // When: apply() with ranges going backward
    step.apply(createHeaderChain(90, 10)); // 90 to 81
    assertThat(step.getLowestSeenBlock()).isEqualTo(81);

    step.apply(createHeaderChain(70, 10)); // 70 to 61
    assertThat(step.getLowestSeenBlock()).isEqualTo(61);

    // Then: lowestSeenBlock updated correctly
    step.apply(createHeaderChain(50, 10)); // 50 to 41
    assertThat(step.getLowestSeenBlock()).isEqualTo(41);
  }

  @Test
  void shouldTrackTotalHeadersSaved() {
    // Given: step
    assertThat(step.getTotalHeadersSaved()).isEqualTo(0);

    // When: apply() multiple times
    step.apply(createHeaderChain(90, 10));
    assertThat(step.getTotalHeadersSaved()).isEqualTo(10);

    step.apply(createHeaderChain(70, 5));
    assertThat(step.getTotalHeadersSaved()).isEqualTo(15);

    // Then: total count tracked correctly
    step.apply(createHeaderChain(50, 8));
    assertThat(step.getTotalHeadersSaved()).isEqualTo(23);
  }

  @Test
  void shouldReturnEmptyStreamWhenGivenEmptyList() {
    // Given: step
    final List<BlockHeader> emptyList = Collections.emptyList();

    // When: apply(emptyList())
    final Stream<Void> result = step.apply(emptyList);

    // Then: returns empty stream without errors
    assertThat(result).isEmpty();
    verify(blockchain, times(0)).unsafeStoreHeader(any(), any());
  }

  @Test
  void shouldCalculateTotalDifficultyCorrectly() {
    // Given: mocked blockchain with specific difficulty
    final Difficulty expectedDifficulty = Difficulty.of(12345);
    when(blockchain.calculateTotalDifficulty(any())).thenReturn(expectedDifficulty);

    final List<BlockHeader> headers = createHeaderChain(90, 5);

    // When: storing headers
    step.apply(headers);

    // Then: calculateTotalDifficulty() called for each header
    verify(blockchain, times(5)).calculateTotalDifficulty(any());
    verify(blockchain, times(5)).unsafeStoreHeader(any(), eq(expectedDifficulty));
  }

  @Test
  void shouldStoreFirstRangeBoundaries() {
    // Given: empty step, first range [100-91]
    final List<BlockHeader> headers = createHeaderChain(100, 10);

    // When: applying first range
    step.apply(headers);
    // This stores: highestBlockHashes(100) = hash_100 and lowestBlockParentHashes(91) = parent_91

    // Then: boundaries stored for future validation
    // Lower range [90-81] should validate if block 90's hash == parent_91
    final Hash parent91 = headers.get(9).getParentHash(); // parent of block 91 (last in range)
    final List<BlockHeader> lowerRange = createHeaderChainWithHighestHash(90, 10, parent91);
    assertThat(step.apply(lowerRange)).isNotNull(); // Should validate successfully (upper boundary)
  }

  @Test
  void shouldHandleSingleBlockRange() {
    // Given: step
    final BlockHeader singleHeader =
        createMockBlockHeader(
            85, Hash.hash(Bytes.ofUnsignedLong(85)), Hash.hash(Bytes.ofUnsignedLong(84)));

    // When: applying single-block range
    final Stream<Void> result = step.apply(List.of(singleHeader));

    // Then: processes successfully
    assertThat(result).isNotNull();
    verify(blockchain, times(1)).unsafeStoreHeader(eq(singleHeader), any());
  }

  @Test
  void shouldHandleNonContiguousRanges() {
    // Given: step
    final List<BlockHeader> range1 = createHeaderChain(100, 10); // 100-91
    final List<BlockHeader> range2 = createHeaderChain(70, 10); // 70-61 (gap of 20 blocks)

    // When: applying non-contiguous ranges
    step.apply(range1);
    step.apply(range2);

    // Then: both ranges processed independently
    verify(blockchain, times(20)).unsafeStoreHeader(any(), any());
    assertThat(step.getLowestSeenBlock()).isEqualTo(61);
  }

  // Helper methods

  /**
   * Creates a chain of headers where each parent hash matches the previous block's hash. Headers
   * are in reverse order: [startBlock, startBlock-1, ..., startBlock-count+1]
   */
  private List<BlockHeader> createHeaderChain(final long startBlock, final int count) {
    final List<BlockHeader> headers = new ArrayList<>();
    Hash previousHash = Hash.hash(Bytes.ofUnsignedLong(startBlock + 1));

    for (int i = 0; i < count; i++) {
      final long blockNumber = startBlock - i;
      final Hash currentHash = Hash.hash(Bytes.ofUnsignedLong(blockNumber));
      final BlockHeader header = createMockBlockHeader(blockNumber, currentHash, previousHash);
      headers.add(header);
      previousHash = currentHash;
    }

    return headers;
  }

  /** Creates a header chain where the lowest block has a specific parent hash. */
  private List<BlockHeader> createHeaderChainWithLowestParent(
      final long startBlock, final int count, final Hash lowestBlockParent) {
    final List<BlockHeader> headers = new ArrayList<>();

    // Build the chain from top down
    Hash previousHash = Hash.hash(Bytes.ofUnsignedLong(startBlock + 1)); // parent of highest block

    for (int i = 0; i < count - 1; i++) {
      final long blockNumber = startBlock - i;
      final Hash currentHash = Hash.hash(Bytes.ofUnsignedLong(blockNumber));
      final BlockHeader header = createMockBlockHeader(blockNumber, currentHash, previousHash);
      headers.add(header);
      previousHash = currentHash;
    }

    // Add the lowest block with specific parent hash
    final long lowestBlockNumber = startBlock - count + 1;
    final Hash lowestHash = Hash.hash(Bytes.ofUnsignedLong(lowestBlockNumber));
    final BlockHeader lowestHeader =
        createMockBlockHeader(lowestBlockNumber, lowestHash, lowestBlockParent);
    headers.add(lowestHeader);

    return headers;
  }

  /** Creates a header chain where the highest block has a specific hash. */
  private List<BlockHeader> createHeaderChainWithHighestHash(
      final long startBlock, final int count, final Hash highestBlockHash) {
    final List<BlockHeader> headers = new ArrayList<>();

    // First header with specific hash
    final Hash parentOfHighest = Hash.hash(Bytes.ofUnsignedLong(startBlock + 1));
    final BlockHeader highestHeader =
        createMockBlockHeader(startBlock, highestBlockHash, parentOfHighest);
    headers.add(highestHeader);

    // Rest of the chain
    Hash previousHash = highestBlockHash;
    for (int i = 1; i < count; i++) {
      final long blockNumber = startBlock - i;
      final Hash currentHash = Hash.hash(Bytes.ofUnsignedLong(blockNumber));
      final BlockHeader header = createMockBlockHeader(blockNumber, currentHash, previousHash);
      headers.add(header);
      previousHash = currentHash;
    }

    return headers;
  }

  private BlockHeader createMockBlockHeader(
      final long blockNumber, final Hash hash, final Hash parentHash) {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getNumber()).thenReturn(blockNumber);
    when(header.getHash()).thenReturn(hash);
    when(header.getParentHash()).thenReturn(parentHash);
    return header;
  }
}
