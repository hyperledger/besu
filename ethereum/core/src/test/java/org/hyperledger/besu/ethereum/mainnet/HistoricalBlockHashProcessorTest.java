/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.datatypes.Hash.fromHexStringLenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoricalBlockHashProcessorTest {
  private Blockchain blockchain;
  private WorldUpdater worldUpdater;
  private MutableAccount account;
  private HistoricalBlockHashProcessor processor;

  private long historicalWindow = 8192;

  @BeforeEach
  void setUp() {
    blockchain = mock(Blockchain.class);
    worldUpdater = mock(WorldUpdater.class);
    account = mock(MutableAccount.class);
    when(worldUpdater.getOrCreate(HistoricalBlockHashProcessor.HISTORICAL_BLOCKHASH_ADDRESS))
        .thenReturn(account);
  }

  @Test
  void shouldStoreParentBlockHash() {
    long forkTimestamp = 0;
    long currentBlock = 3;
    processor = new HistoricalBlockHashProcessor(forkTimestamp);
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 3);
    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, currentBlockHeader);
    // only parent slot number must be set
    verify(account, times(1)).setStorageValue(any(), any());
    verifyAccount(currentBlock - 1, historicalWindow);
  }

  @Test
  void shouldNotStoreBlockHashForGenesisBlock() {
    //  For the fork to be activated at genesis, no history is written to the genesis state, and at
    // the start of block 1, genesis hash will be written as a normal operation to slot 0.
    long forkTimestamp = 0;
    long currentBlock = 0;
    processor = new HistoricalBlockHashProcessor(forkTimestamp);
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 0);

    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, currentBlockHeader);
    verifyNoInteractions(account);
  }

  @Test
  void shouldStoreAncestorBlockHashesAtForkCorrectlyParentIsGenesis() {
    // for activation at block 1, only genesis hash will be written at slot 0 as there is no
    // additional history that needs to be persisted.
    long forkTimestamp = 1;
    long currentBlock = 1;
    processor = new HistoricalBlockHashProcessor(forkTimestamp);
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 10);

    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, currentBlockHeader);
    verify(account, times(1)).setStorageValue(any(), any());
    verifyAccount(0, historicalWindow);
  }

  @Test
  void shouldStoreAncestorBlockHashesAtForkCorrectly() {
    // for activation at block 32, block 31’s hash will be written to slot 31 and additional history
    // for 0..30’s hashes will be persisted, so all in all 0..31’s hashes.
    long forkTimestamp = 32;
    long currentBlock = 32;
    processor = new HistoricalBlockHashProcessor(forkTimestamp);
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 32);

    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, currentBlockHeader);
    verifyAncestor(currentBlock, 32, historicalWindow);
  }

  @Test
  void shouldStoreAncestorBlockHashesAtForkCorrectlyMaxWindows() {
    long forkTimestamp = 10000;
    long currentBlock = 10000;
    historicalWindow = 8192;
    processor = new HistoricalBlockHashProcessor(forkTimestamp, historicalWindow);
    BlockHeader currentBlockHeader = mockBlockHeader(currentBlock);
    mockAncestorHeaders(currentBlockHeader, 10000);
    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, currentBlockHeader);

    // Total of historicalWindow hashes were stored
    verify(account, times((int) historicalWindow)).setStorageValue(any(), any());

    // for activation at block 10000, block 1808-9999’s hashes will be presisted in the slot
    verifyAccount(1808, historicalWindow);
    verifyAccount(9999, historicalWindow);
    // BLOCKHASH for 1807 or less would resolve to 0 as only HISTORY_SERVE_WINDOW are persisted.
    verifyAccountNoIteraction(1807, historicalWindow);
    verifyAccountNoIteraction(10000, historicalWindow);
  }

  @Test
  void shouldWriteGenesisHashAtSlot0() {
    processor = new HistoricalBlockHashProcessor(0);
    BlockHeader header = mockBlockHeader(1);
    mockAncestorHeaders(header, 1);
    processor.storeHistoricalBlockHashes(blockchain, worldUpdater, header);
    verify(account)
        .setStorageValue(UInt256.valueOf(0), UInt256.fromHexString(Hash.ZERO.toHexString()));
  }

  private void verifyAncestor(
      final long blockNumber, final int count, final long historicalWindow) {
    int totalTouchedSlots = (int) (blockNumber - count <= 0 ? blockNumber : count);
    long firstAncestor = Math.max(blockNumber - count - 1, 0);
    verify(account, times(totalTouchedSlots)).setStorageValue(any(), any());
    for (long i = firstAncestor; i < blockNumber; i++) {
      verifyAccount(i, historicalWindow);
    }
  }

  private void verifyAccount(final long number, final long historicalWindow) {
    verify(account)
        .setStorageValue(UInt256.valueOf(number % historicalWindow), UInt256.valueOf(number));
  }

  private void verifyAccountNoIteraction(final long number, final long historicalWindow) {
    verify(account, times(0))
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
    when(blockchain.getBlockHeader(hash)).thenReturn(Optional.of(blockHeader));
    return blockHeader;
  }
}
