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
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ParentBeaconBlockRootHelper;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.vm.Eip7709BlockHashLookup;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * Provides a way to create a BlockHashLookup that fetches hashes from system contract storage, in
 * accordance with EIP-7709. It is not used yet since the fork that this EIP should go in has not
 * been decided yet.
 */
public class Eip7709BlockHashProcessor extends PragueBlockHashProcessor {

  public static final Address EIP_7709_HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  public Eip7709BlockHashProcessor() {
    super(EIP_7709_HISTORY_STORAGE_ADDRESS);
  }

  @Override
  public BlockHashLookup createBlockHashLookup(
      final Blockchain blockchain, final ProcessableBlockHeader blockHeader) {
    return new Eip7709BlockHashLookup(EIP_7709_HISTORY_STORAGE_ADDRESS);
  }

  // TODO remove when verkle devnet based on pectra
  @Override
  public Void process(final BlockProcessingContext context) {
    ProcessableBlockHeader currentBlockHeader = context.getBlockHeader();
    currentBlockHeader
        .getParentBeaconBlockRoot()
        .ifPresent(
            beaconBlockRoot -> {
              if (!beaconBlockRoot.isEmpty()) {
                WorldUpdater worldUpdater = context.getWorldState().updater();
                ParentBeaconBlockRootHelper.storeParentBeaconBlockRoot(
                    worldUpdater, currentBlockHeader.getTimestamp(), beaconBlockRoot);
                worldUpdater.commit();
              }
            });
    WorldUpdater worldUpdater = context.getWorldState().updater();
    final MutableAccount historyStorageAccount = worldUpdater.getOrCreate(historyStorageAddress);
    if (currentBlockHeader.getNumber() > 0) {
      storeParentHash(historyStorageAccount, currentBlockHeader);
    }
    worldUpdater.commit();
    return null;
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
    UInt256 slot = UInt256.valueOf(number % 8192);
    UInt256 value = UInt256.fromBytes(hash);
    account.setStorageValue(slot, value);
  }
  // TODO end remove when verkle devnet based on pectra
}
