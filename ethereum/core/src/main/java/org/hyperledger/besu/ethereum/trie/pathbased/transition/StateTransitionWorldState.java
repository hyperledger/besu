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
package org.hyperledger.besu.ethereum.trie.pathbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.worldview.VerkleWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.trielogs.StateMigrationLog;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Manages state transitions between Bonsai and Verkle world states, ensuring proper migration of
 * storage and accounts.
 *
 * <p>This class is responsible for converting and merging data structures from the Bonsai world
 * state into the Verkle world state, ensuring data consistency and preventing overwriting of
 * existing state.
 */
@SuppressWarnings("unchecked")
public class StateTransitionWorldState implements MutableWorldState, PathBasedWorldView {

  private final BonsaiWorldState bonsaiWorldState;
  private final VerkleWorldState verkleWorldState;
  private final StateMigrationLog migrationProgress;
  private PathBasedWorldStateUpdateAccumulator<?> accumulator;

  private boolean isVerkleActive;
  private final long verkleForkTimeStamp;

  public StateTransitionWorldState(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final StateMigrationLog migrationProgress,
      final boolean isVerkleActive,
      final long verkleForkTimeStamp) {
    this.bonsaiWorldState = bonsaiWorldState;
    this.verkleWorldState = verkleWorldState;
    this.isVerkleActive = isVerkleActive;
    this.verkleForkTimeStamp = verkleForkTimeStamp;
    this.migrationProgress = migrationProgress;
    this.accumulator = loadAccumulator();
  }

  @Override
  public void announceBlockToImport(final BlockHeader blockToImport) {
    // Determine if Verkle should be activated based on the fork timestamp.
    this.isVerkleActive = blockToImport.getTimestamp() >= verkleForkTimeStamp;
    // Load the appropriate state accumulator.
    this.accumulator = loadAccumulator();
  }

  @Override
  public Account get(final Address address) {
    if (isVerkleActive) {
      Account account = verkleWorldState.get(address);
      // If the account is not found in Verkle but migration is ongoing, check in Bonsai
      if (account == null && migrationProgress.isMigrationInProgress()) {
        account = bonsaiWorldState.get(address);
        // Convert Bonsai account to Verkle format if necessary
        if (account instanceof BonsaiAccount bonsaiAccount) {
          VerkleAccount verkleAccount =
              new VerkleAccount(
                  getAccumulator(),
                  address,
                  address.addressHash(),
                  bonsaiAccount.getNonce(),
                  bonsaiAccount.getBalance(),
                  bonsaiAccount.getCodeSize().orElse(0L),
                  bonsaiAccount.getCode(),
                  bonsaiAccount.getCodeHash(),
                  true);
          // notify verkle accumulator that the value was not found in verkle by creating a pmt
          // sourced
          // diff based value
          verkleWorldState
              .getAccumulator()
              .getAccountsToUpdate()
              .put(
                  address,
                  new MigratedDiffValue<>(new VerkleAccount(verkleAccount), verkleAccount));
          return verkleAccount;
        }
      }

      return account;
    } else {
      // If Verkle is not active, retrieve the account from Bonsai
      return bonsaiWorldState.get(address);
    }
  }

  @Override
  public Optional<Bytes> getCode(final Address address, final Hash codeHash) {
    return getWorldState().getCode(address, codeHash);
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 key) {
    return getStorageValueByStorageSlotKey(address, new StorageSlotKey(key)).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {

    if (isVerkleActive) {
      Optional<UInt256> maybeSlot =
          verkleWorldState.getStorageValueByStorageSlotKey(address, storageSlotKey);

      // If not found in Verkle and migration is ongoing, check in Bonsai
      if (maybeSlot.isEmpty() && migrationProgress.isMigrationInProgress()) {
        // read bonsai worldstate
        maybeSlot = bonsaiWorldState.getStorageValueByStorageSlotKey(address, storageSlotKey);
        // notify verkle accumulator that the value was not found in verkle by creating a pmt
        // sourced
        // diff based value
        maybeSlot.ifPresent(
            slot -> {
              verkleWorldState
                  .getAccumulator()
                  .maybeCreateStorageMap(address)
                  .put(storageSlotKey, new MigratedDiffValue<>(slot, slot));
            });
      }
      return maybeSlot;
    } else {
      // If Verkle is not active, retrieve the storage value from Bonsai
      return bonsaiWorldState.getStorageValueByStorageSlotKey(address, storageSlotKey);
    }
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 key) {
    return getStorageValue(address, key);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    return getWorldState().getAllAccountStorage(address, rootHash);
  }

  @Override
  public boolean isModifyingHeadWorldState() {
    return getWorldState().isModifyingHeadWorldState();
  }

  @Override
  public PathBasedWorldStateKeyValueStorage getWorldStateStorage() {
    return getWorldState().getWorldStateStorage();
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    if (isVerkleActive) {
      // Import state changes into the Verkle accumulator
      verkleWorldState
          .getAccumulator()
          .importStateChangesFromSource(
              (PathBasedWorldStateUpdateAccumulator<VerkleAccount>) accumulator);

      // If migration is in progress, perform conversion from Bonsai to Verkle
      if (migrationProgress.isMigrationInProgress()) {
        PatriciaToVerkleConverter.convert(bonsaiWorldState, verkleWorldState, migrationProgress);
      }
      if (migrationProgress.isMigrationInProgress()
          || migrationProgress.isAccountsFullyMigrated()) {
        verkleWorldState.getAccumulator().setStateMigrationLog(Optional.of(migrationProgress));
      }

      // Persist Verkle world state
      verkleWorldState.persist(blockHeader);
    } else {
      // Import state changes into the Bonsai accumulator
      bonsaiWorldState
          .getAccumulator()
          .importStateChangesFromSource(
              (PathBasedWorldStateUpdateAccumulator<BonsaiAccount>) accumulator);

      // Generate pre-images for Bonsai state transition
      // TODO (this should be removed in final version)
      //generatePreImagesForBonsai();

      // Persist Bonsai world state
      bonsaiWorldState.persist(blockHeader);
    }

    // Reset the accumulator after persisting state
    accumulator.reset();
  }

  /**
   * Generates pre-images for the Bonsai world state.
   *
   * <p>This method ensures that addresses and storage slot keys are correctly mapped to their
   * hashes. It is currently used for pre-image generation and should be removed in the final
   * version.
   */
  @SuppressWarnings("unused")
  private void generatePreImagesForBonsai() {
    bonsaiWorldState
        .getAccumulator()
        .getAccountsToUpdate()
        .forEach(
            (address, bonsaiAccountDiffBasedValue) ->
                PatriciaToVerkleConverter.addPreImage(Hash.hash(address), address));

    bonsaiWorldState
        .getAccumulator()
        .getStorageToUpdate()
        .forEach(
            (address, storageUpdates) ->
                storageUpdates.forEach(
                    (storageSlotKey, value) ->
                        PatriciaToVerkleConverter.addPreImage(
                            storageSlotKey.getSlotHash(),
                            storageSlotKey.getSlotKey().orElseThrow())));
  }

  @Override
  public WorldUpdater updater() {
    return getAccumulator();
  }

  @Override
  public Hash rootHash() {
    return getWorldState().rootHash();
  }

  @Override
  public Hash frontierRootHash() {
    return getWorldState().frontierRootHash();
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    return getWorldState().streamAccounts(startKeyHash, limit);
  }

  private PathBasedWorldStateUpdateAccumulator<?> loadAccumulator() {
    if (isVerkleActive) {
      return new VerkleWorldStateUpdateAccumulator(this, verkleWorldState.getAccumulator());
    } else {
      return new BonsaiWorldStateUpdateAccumulator(this, bonsaiWorldState.getAccumulator());
    }
  }

  private PathBasedWorldState getWorldState() {
    if (isVerkleActive) {
      return verkleWorldState;
    } else {
      return bonsaiWorldState;
    }
  }

  private PathBasedWorldStateUpdateAccumulator<?> getAccumulator() {
    return this.accumulator;
  }
}
