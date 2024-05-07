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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Processes and stores historical block hashes in accordance with EIP-2935. This class is
 * responsible for managing the storage of block hashes to support EIP-2935, which introduces
 * historical block hash access in smart contracts.
 */
public class HistoricalBlockHashProcessor {

  public static final Address HISTORY_STORAGE_ADDRESS = BlockHashOperation.HISTORY_STORAGE_ADDRESS;

  private static final long HISTORY_SERVE_WINDOW = 8192;

  private final long forkTimestamp;
  private final long historySaveWindow;

  /**
   * Constructs a HistoricalBlockHashProcessor with a specified fork timestamp.
   *
   * @param forkTimestamp The timestamp at which the fork becomes active.
   */
  public HistoricalBlockHashProcessor(final long forkTimestamp) {
    this(forkTimestamp, HISTORY_SERVE_WINDOW);
  }

  /**
   * Constructs a HistoricalBlockHashProcessor with a specified fork timestamp and history save
   * window. This constructor is primarily used for testing.
   *
   * @param forkTimestamp The timestamp at which the fork becomes active.
   * @param historySaveWindow The number of blocks for which history should be saved.
   */
  @VisibleForTesting
  public HistoricalBlockHashProcessor(final long forkTimestamp, final long historySaveWindow) {
    this.forkTimestamp = forkTimestamp;
    this.historySaveWindow = historySaveWindow;
  }

  /**
   * Stores the historical block hashes based on the current block header. This method calculates
   * the appropriate storage slot for the parent block hash and any additional ancestors within the
   * history save window, then stores those hashes in the world state.
   *
   * @param blockchain The blockchain from which block headers are retrieved.
   * @param worldUpdater The world state updater to store the block hashes.
   * @param currentBlockHeader The current block header being processed.
   */
  public void storeHistoricalBlockHashes(
      final Blockchain blockchain,
      final WorldUpdater worldUpdater,
      final ProcessableBlockHeader currentBlockHeader) {

    final MutableAccount account = worldUpdater.getOrCreate(HISTORY_STORAGE_ADDRESS);

    if (currentBlockHeader.getNumber() > 0) {
      storeParentHash(account, currentBlockHeader);

      BlockHeader ancestor =
          blockchain.getBlockHeader(currentBlockHeader.getParentHash()).orElseThrow();

      // If fork block, add the parent's direct `HISTORY_SERVE_WINDOW - 1`
      if (ancestor.getTimestamp() < forkTimestamp) {
        for (int i = 0; i < (historySaveWindow - 1) && ancestor.getNumber() > 0; i++) {
          ancestor = blockchain.getBlockHeader(ancestor.getParentHash()).orElseThrow();
          storeBlockHeaderHash(account, ancestor);
        }
      }
    }
  }

  /**
   * Stores the hash of the parent block in the world state.
   *
   * @param account The account associated with the historical block hash storage.
   * @param header The current block header being processed.
   */
  private void storeParentHash(final MutableAccount account, final ProcessableBlockHeader header) {
    storeHash(account, header.getNumber() - 1, header.getParentHash());
  }

  /**
   * Stores the hash of a block in the world state.
   *
   * @param account The account associated with the historical block hash storage.
   * @param header The block header whose hash is to be stored.
   */
  private void storeBlockHeaderHash(final MutableAccount account, final BlockHeader header) {
    storeHash(account, header.getNumber(), header.getHash());
  }

  /**
   * Stores the hash in the world state.
   *
   * @param account The account associated with the historical block hash storage.
   * @param number The slot to store.
   * @param hash The hash to be stored.
   */
  private void storeHash(final MutableAccount account, final long number, final Hash hash) {
    account.setStorageValue(UInt256.valueOf(number % historySaveWindow), UInt256.fromBytes(hash));
  }
}
