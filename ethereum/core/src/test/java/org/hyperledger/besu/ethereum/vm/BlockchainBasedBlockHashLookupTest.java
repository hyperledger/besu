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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainBasedBlockHashLookupTest {

  private static final int MAXIMUM_COMPLETE_BLOCKS_BEHIND = 256;
  private static final int CURRENT_BLOCK_NUMBER = MAXIMUM_COMPLETE_BLOCKS_BEHIND * 2;
  private final Blockchain blockchain = mock(Blockchain.class);
  private BlockHeader[] headers;
  private BlockHashLookup lookup;
  private final MessageFrame messageFrameMock = mock(MessageFrame.class);

  @BeforeEach
  void setUp() {
    setUpBlockchain(CURRENT_BLOCK_NUMBER);
    lookup =
        new BlockchainBasedBlockHashLookup(
            createHeader(CURRENT_BLOCK_NUMBER, headers[headers.length - 1]), blockchain);
  }

  private void setUpBlockchain(final int blockNumberAtHead) {
    headers = new BlockHeader[blockNumberAtHead];
    BlockHeader parentHeader = null;
    for (int i = 0; i < headers.length; i++) {
      final BlockHeader header = createHeader(i, parentHeader);
      when(blockchain.getBlockHeader(header.getHash())).thenReturn(Optional.of(header));
      headers[i] = header;
      parentHeader = headers[i];
    }
  }

  @AfterEach
  void verifyBlocksNeverLookedUpByNumber() {
    // Looking up the block by number is incorrect because it always uses the canonical chain even
    // if the block being imported is on a fork.
    verify(blockchain, never()).getBlockHeader(anyLong());
  }

  @Test
  void shouldGetHashOfImmediateParent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  void shouldGetHashOfMaxBlocksBehind() {
    assertHashForBlockNumber(MAXIMUM_COMPLETE_BLOCKS_BEHIND);
  }

  @Test
  void shouldReturnEmptyHashWhenRequestedBlockNotOnchain() {
    assertThat(lookup.apply(messageFrameMock, CURRENT_BLOCK_NUMBER + 20L)).isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldReturnEmptyHashWhenParentBlockNotOnchain() {
    final BlockHashLookup lookupWithUnavailableParent =
        new BlockchainBasedBlockHashLookup(
            new BlockHeaderTestFixture().number(CURRENT_BLOCK_NUMBER + 20).buildHeader(),
            blockchain);
    Assertions.assertThat(
            lookupWithUnavailableParent.apply(messageFrameMock, (long) CURRENT_BLOCK_NUMBER))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldCacheBlockHashesWhileIteratingBackToPreviousHeader() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 4);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 7);
    for (int i = 1; i < 7; i++) {
      verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - i].getHash());
    }
    verifyNoMoreInteractions(blockchain);
  }

  @Test
  void shouldReturnZeroWhenCurrentBlockIsGenesis() {
    lookup = new BlockchainBasedBlockHashLookup(createHeader(0, null), mock(Blockchain.class));
    assertHashForBlockNumber(0, Hash.ZERO);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockEqualToImportingBlock() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER, Hash.ZERO);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockAheadOfCurrent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER + 1, Hash.ZERO);
  }

  @Test
  void shouldReturnZeroWhenRequestedBlockTooFarBehindCurrent() {
    assertHashForBlockNumber(MAXIMUM_COMPLETE_BLOCKS_BEHIND - 1, Hash.ZERO);
    assertHashForBlockNumber(10, Hash.ZERO);
  }

  private void assertHashForBlockNumber(final int blockNumber) {
    assertHashForBlockNumber(blockNumber, headers[blockNumber].getHash());
  }

  private void assertHashForBlockNumber(final int blockNumber, final Hash hash) {
    Assertions.assertThat(lookup.apply(messageFrameMock, (long) blockNumber)).isEqualTo(hash);
  }

  private BlockHeader createHeader(final int blockNumber, final BlockHeader parentHeader) {
    return new BlockHeaderTestFixture()
        .number(blockNumber)
        .parentHash(parentHeader != null ? parentHeader.getHash() : Hash.EMPTY)
        .buildHeader();
  }
}
