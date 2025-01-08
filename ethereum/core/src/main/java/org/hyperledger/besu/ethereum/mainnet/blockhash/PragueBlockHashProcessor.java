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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes and stores historical block hashes in accordance with EIP-2935. This class is
 * responsible for managing the storage of block hashes to support EIP-2935, which introduces
 * historical block hash access in smart contracts.
 */
public class PragueBlockHashProcessor extends CancunBlockHashProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(PragueBlockHashProcessor.class);

  public static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0x0F792be4B0c0cb4DAE440Ef133E90C0eCD48CCCC");

  /** The HISTORY_SERVE_WINDOW */
  private static final long HISTORY_SERVE_WINDOW = 8191;

  protected final long historyServeWindow;
  protected final Address historyStorageAddress;

  /** Constructs a BlockHashProcessor. */
  public PragueBlockHashProcessor() {
    this(HISTORY_STORAGE_ADDRESS, HISTORY_SERVE_WINDOW);
  }

  /**
   * Constructs a BlockHashProcessor with a specified history save window. This constructor is
   * primarily used for testing.
   *
   * @param historyStorageAddress the address of the contract storing the history
   * @param historyServeWindow The number of blocks for which history should be saved.
   */
  @VisibleForTesting
  public PragueBlockHashProcessor(
      final Address historyStorageAddress, final long historyServeWindow) {
    this.historyStorageAddress = historyStorageAddress;
    this.historyServeWindow = historyServeWindow;
  }

  @Override
  public void processBlockHashes(
      final MutableWorldState mutableWorldState, final ProcessableBlockHeader currentBlockHeader) {
    super.processBlockHashes(mutableWorldState, currentBlockHeader);

    WorldUpdater worldUpdater = mutableWorldState.updater();
    final MutableAccount historyStorageAccount = worldUpdater.getOrCreate(historyStorageAddress);

    if (currentBlockHeader.getNumber() > 0) {
      storeParentHash(historyStorageAccount, currentBlockHeader);
    }
    worldUpdater.commit();
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
   * Stores the hash in the world state.
   *
   * @param account The account associated with the historical block hash storage.
   * @param number The slot to store.
   * @param hash The hash to be stored.
   */
  private void storeHash(final MutableAccount account, final long number, final Hash hash) {
    UInt256 slot = UInt256.valueOf(number % historyServeWindow);
    UInt256 value = UInt256.fromBytes(hash);
    LOG.trace(
        "Writing to {} {}=%{}", account.getAddress(), slot.toDecimalString(), value.toHexString());
    account.setStorageValue(slot, value);
  }
}
