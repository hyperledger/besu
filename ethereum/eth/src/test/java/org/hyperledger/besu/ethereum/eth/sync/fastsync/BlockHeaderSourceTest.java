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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BlockHeaderSourceTest {

  @Test
  void shouldReadHeadersInForwardOrder() {
    // Given: blockchain with headers 10-20, source from block 10 to 20, batch size 5
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 11);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 20, 5);

    // When: calling next() multiple times
    final List<BlockHeader> batch1 = source.next();
    final List<BlockHeader> batch2 = source.next();
    final List<BlockHeader> batch3 = source.next();

    // Then: returns batches [10-14], [15-19], [20]
    assertThat(batch1).hasSize(5);
    assertThat(batch1.get(0).getNumber()).isEqualTo(10);
    assertThat(batch1.get(4).getNumber()).isEqualTo(14);

    assertThat(batch2).hasSize(5);
    assertThat(batch2.get(0).getNumber()).isEqualTo(15);
    assertThat(batch2.get(4).getNumber()).isEqualTo(19);

    assertThat(batch3).hasSize(1);
    assertThat(batch3.get(0).getNumber()).isEqualTo(20);
  }

  @Test
  void shouldReturnNullWhenExhausted() {
    // Given: blockchain with headers 10-15, source from block 10 to 15
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 6);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 15, 10);

    // When: calling next() after all headers read
    final List<BlockHeader> first = source.next();
    final List<BlockHeader> second = source.next();

    // Then: first returns batch, second returns null
    assertThat(first).hasSize(6);
    assertThat(second).isNull();
  }

  @Test
  void shouldHandleGapsInBlockchain() {
    // Given: blockchain missing header at block 15
    final Blockchain blockchain = mock(Blockchain.class);
    when(blockchain.getBlockHeader(any(Long.class)))
        .thenAnswer(
            invocation -> {
              final long requestedBlock = invocation.getArgument(0);
              if (requestedBlock >= 10 && requestedBlock < 20 && requestedBlock != 15) {
                return Optional.of(createMockBlockHeader(requestedBlock));
              }
              return Optional.empty(); // gap at 15
            });

    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 19, 10);

    // When: next() tries to read that block
    final List<BlockHeader> batch = source.next();

    // Then: returns partial batch up to gap, marks exhausted
    assertThat(batch).hasSize(5); // Only blocks 10-14
    assertThat(batch.get(4).getNumber()).isEqualTo(14);
    assertThat(source.isExhausted()).isTrue();
    assertThat(source.next()).isNull();
  }

  @Test
  void shouldHandlePartialLastBatch() {
    // Given: source from block 10 to 13, batch size 5
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 4);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 13, 5);

    // When: next() called
    final List<BlockHeader> batch = source.next();

    // Then: returns batch of 4 headers [10-13]
    assertThat(batch).hasSize(4);
    assertThat(batch.get(0).getNumber()).isEqualTo(10);
    assertThat(batch.get(3).getNumber()).isEqualTo(13);
    assertThat(source.next()).isNull();
  }

  @Test
  void shouldReportExhaustedAfterGap() {
    // Given: blockchain with gap at block 11
    final Blockchain blockchain = mock(Blockchain.class);
    when(blockchain.getBlockHeader(any(Long.class)))
        .thenAnswer(
            invocation -> {
              final long requestedBlock = invocation.getArgument(0);
              if (requestedBlock == 10) {
                return Optional.of(createMockBlockHeader(10));
              }
              return Optional.empty(); // gap at 11 and beyond
            });

    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 20, 5);

    // When: encountering gap
    source.next();

    // Then: isExhausted() returns true
    assertThat(source.isExhausted()).isTrue();
  }

  @Test
  void shouldReportHasNextCorrectly() {
    // Given: source with headers available
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 10);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 19, 5);

    // When: checking hasNext()
    assertThat(source.hasNext()).isTrue();

    source.next(); // 10-14
    assertThat(source.hasNext()).isTrue();

    source.next(); // 15-19
    assertThat(source.hasNext()).isFalse();

    // Then: returns true before exhaustion, false after
    assertThat(source.next()).isNull();
  }

  @Test
  void shouldQueryBlockchainForEachHeader() {
    // Given: mocked blockchain
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 5);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 14, 5);

    // When: next() called
    source.next();

    // Then: getBlockHeader() called for each block number in batch
    verify(blockchain, times(1)).getBlockHeader(10L);
    verify(blockchain, times(1)).getBlockHeader(11L);
    verify(blockchain, times(1)).getBlockHeader(12L);
    verify(blockchain, times(1)).getBlockHeader(13L);
    verify(blockchain, times(1)).getBlockHeader(14L);
  }

  @Test
  void shouldTrackCurrentBlockCorrectly() {
    // Given: source
    final Blockchain blockchain = createMockBlockchainWithHeaders(10, 15);
    final BlockHeaderSource source = new BlockHeaderSource(blockchain, 10, 24, 5);

    // When: calling next() multiple times
    assertThat(source.getCurrentBlock()).isEqualTo(10);

    source.next(); // reads 10-14
    assertThat(source.getCurrentBlock()).isEqualTo(15);

    source.next(); // reads 15-19
    assertThat(source.getCurrentBlock()).isEqualTo(20);

    // Then: getCurrentBlock() returns correct position
    source.next(); // reads 20-24
    assertThat(source.getCurrentBlock()).isEqualTo(25);
  }

  // Helper methods

  private Blockchain createMockBlockchainWithHeaders(final long startBlock, final int count) {
    final Blockchain blockchain = mock(Blockchain.class);
    when(blockchain.getBlockHeader(any(Long.class)))
        .thenAnswer(
            invocation -> {
              final long requestedBlock = invocation.getArgument(0);
              if (requestedBlock >= startBlock && requestedBlock < startBlock + count) {
                return Optional.of(createMockBlockHeader(requestedBlock));
              }
              return Optional.empty();
            });
    return blockchain;
  }

  private BlockHeader createMockBlockHeader(final long blockNumber) {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getNumber()).thenReturn(blockNumber);
    return header;
  }
}
