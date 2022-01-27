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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BlockHashLookupTest {

  private static final int CURRENT_BLOCK_NUMBER = 256;
  private final Blockchain blockchain = mock(Blockchain.class);
  private final BlockHeader[] headers = new BlockHeader[CURRENT_BLOCK_NUMBER];
  private BlockHashLookup lookup;

  @Before
  public void setUp() {
    BlockHeader parentHeader = null;
    for (int i = 0; i < headers.length; i++) {
      final BlockHeader header = createHeader(i, parentHeader);
      when(blockchain.getBlockHeader(header.getHash())).thenReturn(Optional.of(header));
      headers[i] = header;
      parentHeader = headers[i];
    }
    lookup =
        new BlockHashLookup(
            createHeader(CURRENT_BLOCK_NUMBER, headers[headers.length - 1]), blockchain);
  }

  @After
  public void verifyBlocksNeverLookedUpByNumber() {
    // Looking up the block by number is incorrect because it always uses the canonical chain even
    // if the block being imported is on a fork.
    verify(blockchain, never()).getBlockHeader(anyLong());
  }

  @Test
  public void shouldGetHashOfImmediateParent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  public void shouldGetHashOfGenesisBlock() {
    assertHashForBlockNumber(0);
  }

  @Test
  public void shouldGetHashForRecentBlockAfterOlderBlock() {
    assertHashForBlockNumber(10);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  public void shouldReturnEmptyHashWhenRequestedBlockNotOnchain() {
    Assertions.assertThat(lookup.apply(CURRENT_BLOCK_NUMBER + 20L)).isEqualTo(Hash.ZERO);
  }

  @Test
  public void shouldReturnEmptyHashWhenParentBlockNotOnchain() {
    final BlockHashLookup lookupWithUnavailableParent =
        new BlockHashLookup(
            new BlockHeaderTestFixture().number(CURRENT_BLOCK_NUMBER + 20).buildHeader(),
            blockchain);
    Assertions.assertThat(lookupWithUnavailableParent.apply((long) CURRENT_BLOCK_NUMBER))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  public void shouldGetParentHashFromCurrentBlock() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
    verifyNoInteractions(blockchain);
  }

  @Test
  public void shouldCacheBlockHashesWhileIteratingBackToPreviousHeader() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 4);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 1].getHash());
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 2].getHash());
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 3].getHash());
    verifyNoMoreInteractions(blockchain);
  }

  private void assertHashForBlockNumber(final int blockNumber) {
    Assertions.assertThat(lookup.apply((long) blockNumber))
        .isEqualTo(headers[blockNumber].getHash());
  }

  private BlockHeader createHeader(final int blockNumber, final BlockHeader parentHeader) {
    return new BlockHeaderTestFixture()
        .number(blockNumber)
        .parentHash(parentHeader != null ? parentHeader.getHash() : Hash.EMPTY)
        .buildHeader();
  }
}
