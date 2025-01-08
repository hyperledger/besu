/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.blockhash;

import static org.hyperledger.besu.datatypes.Hash.fromHexStringLenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockHashProcessorTest {
  private WorldUpdater worldUpdater;
  private MutableWorldState mutableWorldState;
  private MutableAccount account;
  private BlockHashProcessor processor;

  private final long historicalWindow = 255;

  @BeforeEach
  void setUp() {
    mutableWorldState = mock(MutableWorldState.class);
    worldUpdater = mock(WorldUpdater.class);
    account = mock(MutableAccount.class);
    when(mutableWorldState.updater()).thenReturn(worldUpdater);
    when(worldUpdater.getOrCreate(PragueBlockHashProcessor.HISTORY_STORAGE_ADDRESS))
        .thenReturn(account);
  }

  @Test
  void shouldStoreParentBlockHash() {
    long currentBlock = 3;
    processor = new PragueBlockHashProcessor();
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 3);
    processor.processBlockHashes(mutableWorldState, currentBlockHeader);
    // only parent slot number must be set
    verify(account, times(1)).setStorageValue(any(), any());
    verifyAccount(currentBlock - 1, historicalWindow);
  }

  @Test
  void shouldNotStoreBlockHashForGenesisBlock() {
    //  For the fork to be activated at genesis, no history is written to the genesis state, and at
    // the start of block 1, genesis hash will be written as a normal operation to slot 0.
    long currentBlock = 0;
    processor = new PragueBlockHashProcessor();
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 0);

    processor.processBlockHashes(mutableWorldState, currentBlockHeader);
    verifyNoInteractions(account);
  }

  @Test
  void shouldStoreAncestorBlockHashesAtForkCorrectlyParentIsGenesis() {
    // for activation at block 1, only genesis hash will be written at slot 0 as there is no
    // additional history that needs to be persisted.
    long currentBlock = 1;
    processor = new PragueBlockHashProcessor();
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 10);

    processor.processBlockHashes(mutableWorldState, currentBlockHeader);
    verify(account, times(1)).setStorageValue(any(), any());
    verifyAccount(0, historicalWindow);
  }

  @Test
  void shouldWriteGenesisHashAtSlot0() {
    processor = new PragueBlockHashProcessor();
    BlockHeader header = mockBlockHeader(1);
    mockAncestorHeaders(header, 1);
    processor.processBlockHashes(mutableWorldState, header);
    verify(account)
        .setStorageValue(UInt256.valueOf(0), UInt256.fromHexString(Hash.ZERO.toHexString()));
  }

  private void verifyAccount(final long number, final long historicalWindow) {
    verify(account)
        .setStorageValue(UInt256.valueOf(number % historicalWindow), UInt256.valueOf(number));
  }

  private void mockAncestorHeaders(final BlockHeader blockHeader, final int count) {
    long firstAncestor = Math.max(blockHeader.getNumber() - count, 0);
    var block = blockHeader;
    for (long i = blockHeader.getNumber(); i > firstAncestor; i--) {
      long parentNumber = block.getNumber() - 1;
      block = mockBlockHeader(parentNumber);
    }
  }

  private BlockHeader mockBlockHeader(final long currentNumber) {
    BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getNumber()).thenReturn(currentNumber);
    Hash hash = fromHexStringLenient("0x" + Long.toHexString(currentNumber));
    Hash parentHash = fromHexStringLenient("0x" + Long.toHexString(currentNumber - 1));
    when(blockHeader.getHash()).thenReturn(hash);
    when(blockHeader.getTimestamp()).thenReturn(currentNumber);
    when(blockHeader.getParentHash()).thenReturn(parentHash);
    return blockHeader;
  }
}
