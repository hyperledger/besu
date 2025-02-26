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
package org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.PreloaderManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The AccumulatorUpdater is responsible for managing state transitions in the accumulator-based
 * world view. It processes updates and deletions of accounts and their corresponding storage and
 * code.
 *
 * @param <ACCOUNT> The type representing a diff-based account.
 */
@SuppressWarnings("unchecked")
public class AccumulatorUpdater<ACCOUNT extends DiffBasedAccount>
    extends AbstractWorldUpdater<DiffBasedWorldView, ACCOUNT> {

  // Manager for preloading storage data into memory to optimize state access.
  private final PreloaderManager<ACCOUNT> preloaderManager;

  /**
   * Instantiates a new world updater for the accumulator.
   *
   * @param world the world view update accumulator that holds pending state changes
   * @param preloaderManager preloader of the trie nodes for efficient storage access
   * @param evmConfiguration the EVM configuration parameters
   */
  protected AccumulatorUpdater(
      final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> world,
      final PreloaderManager<ACCOUNT> preloaderManager,
      final EvmConfiguration evmConfiguration) {
    super(world, evmConfiguration);
    this.preloaderManager = preloaderManager;
  }

  /**
   * Loads or creates an account for mutation.
   *
   * @param address the address of the account
   * @return the diff-based account to be mutated
   */
  @Override
  protected ACCOUNT getForMutation(final Address address) {
    return wrappedWorldView().loadAccount(address, DiffBasedValue::getUpdated);
  }

  /**
   * Returns a collection of accounts that have been touched during the transaction.
   *
   * @return the collection of touched accounts
   */
  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return getUpdatedAccounts();
  }

  /**
   * Returns the addresses of accounts that have been deleted during the transaction.
   *
   * @return the set of deleted account addresses
   */
  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return getDeletedAccounts();
  }

  /** Reverts all pending updates by clearing the tracked updates and deletions. */
  @Override
  public void revert() {
    updatedAccounts.clear();
    deletedAccounts.clear();
  }

  /**
   * Commits the accumulated state changes into the world view. This method processes: This method
   * ensures that all the changes from the current updater are merged into the underlying world
   * view.
   */
  @Override
  public void commit() {

    final Map<Address, DiffBasedValue<ACCOUNT>> accountsToUpdate =
        wrappedWorldView().getAccountsToUpdate();
    final Map<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
        storageToUpdate = wrappedWorldView().getStorageToUpdate();
    final Map<Address, DiffBasedValue<Bytes>> codeToUpdate = wrappedWorldView().getCodeToUpdate();
    final Set<Address> storageToClear = wrappedWorldView().getStorageToClear();

    // Process deleted accounts.
    for (final Address deletedAddress : getDeletedAccounts()) {
      final DiffBasedValue<ACCOUNT> accountValue =
          accountsToUpdate.computeIfAbsent(
              deletedAddress,
              __ ->
                  wrappedWorldView()
                      .loadAccountFromParent(
                          deletedAddress, new DiffBasedValue<>(null, null, true)));
      storageToClear.add(deletedAddress);
      final DiffBasedValue<Bytes> codeValue = codeToUpdate.get(deletedAddress);
      if (codeValue != null) {
        codeValue.setUpdated(null).setCleared();
      } else {
        wrappedWorldView()
            .getCode(
                deletedAddress,
                Optional.ofNullable(accountValue)
                    .map(DiffBasedValue::getPrior)
                    .map(DiffBasedAccount::getCodeHash)
                    .orElse(Hash.EMPTY))
            .ifPresent(
                deletedCode ->
                    codeToUpdate.put(
                        deletedAddress, new DiffBasedValue<>(deletedCode, null, true)));
      }

      // Mark storage changes for deletion.
      final Map<StorageSlotKey, DiffBasedValue<UInt256>> deletedStorageUpdates =
          storageToUpdate.computeIfAbsent(
              deletedAddress,
              k ->
                  new StorageConsumingMap<>(
                      deletedAddress,
                      new ConcurrentHashMap<>(),
                      preloaderManager.getStoragePreloader(
                          wrappedWorldView().getParentWorldView())));
      final Iterator<Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>>> iter =
          deletedStorageUpdates.entrySet().iterator();
      while (iter.hasNext()) {
        final Map.Entry<StorageSlotKey, DiffBasedValue<UInt256>> updateEntry = iter.next();
        final DiffBasedValue<UInt256> updatedSlot = updateEntry.getValue();
        if (updatedSlot.getPrior() == null || updatedSlot.getPrior().isZero()) {
          iter.remove();
        } else {
          updatedSlot.setUpdated(null).setCleared();
        }
      }

      final ACCOUNT originalValue = accountValue.getPrior();
      if (originalValue != null) {
        // Enumerate and delete addresses not updated
        wrappedWorldView()
            .getAllAccountStorage(deletedAddress, originalValue.getStorageRoot())
            .forEach(
                (keyHash, entryValue) -> {
                  final StorageSlotKey storageSlotKey =
                      new StorageSlotKey(Hash.wrap(keyHash), Optional.empty());
                  if (!deletedStorageUpdates.containsKey(storageSlotKey)) {
                    final UInt256 value = UInt256.fromBytes(RLP.decodeOne(entryValue));
                    deletedStorageUpdates.put(
                        storageSlotKey, new DiffBasedValue<>(value, null, true));
                  }
                });
      }
      if (deletedStorageUpdates.isEmpty()) {
        storageToUpdate.remove(deletedAddress);
      }
      accountValue.setUpdated(null);
    }

    // Process updated accounts.
    getUpdatedAccounts()
        .forEach(
            tracked -> {
              final Address updatedAddress = tracked.getAddress();
              System.out.println("commit " + updatedAddress);
              final ACCOUNT updatedAccount;
              final DiffBasedValue<ACCOUNT> updatedAccountValue =
                  accountsToUpdate.get(updatedAddress);
              final Map<StorageSlotKey, DiffBasedValue<UInt256>> pendingStorageUpdates =
                  storageToUpdate.computeIfAbsent(
                      updatedAddress,
                      k ->
                          new StorageConsumingMap<>(
                              updatedAddress,
                              new ConcurrentHashMap<>(),
                              preloaderManager.getStoragePreloader(
                                  wrappedWorldView().getParentWorldView())));

              if (tracked.getWrappedAccount() == null) {
                updatedAccount = wrappedWorldView().createAccount(tracked);
                tracked.setWrappedAccount(updatedAccount);
                if (updatedAccountValue == null) {
                  accountsToUpdate.put(updatedAddress, new DiffBasedValue<>(null, updatedAccount));
                  codeToUpdate.put(
                      updatedAddress, new DiffBasedValue<>(null, updatedAccount.getCode()));
                } else {
                  updatedAccountValue.setUpdated(updatedAccount);
                }
              } else {
                updatedAccount = tracked.getWrappedAccount();
                updatedAccount.setBalance(tracked.getBalance());
                updatedAccount.setNonce(tracked.getNonce());
                if (tracked.codeWasUpdated()) {
                  updatedAccount.setCode(tracked.getCode());
                }
                if (tracked.getStorageWasCleared()) {
                  updatedAccount.clearStorage();
                }
                tracked.getUpdatedStorage().forEach(updatedAccount::setStorageValue);
              }

              if (tracked.codeWasUpdated()) {
                final DiffBasedValue<Bytes> pendingCode =
                    codeToUpdate.computeIfAbsent(
                        updatedAddress,
                        addr ->
                            new DiffBasedValue<>(
                                wrappedWorldView()
                                    .getCode(
                                        addr,
                                        Optional.ofNullable(updatedAccountValue)
                                            .map(DiffBasedValue::getPrior)
                                            .map(DiffBasedAccount::getCodeHash)
                                            .orElse(Hash.EMPTY))
                                    .orElse(null),
                                null));
                pendingCode.setUpdated(updatedAccount.getCode());
              }

              if (tracked.getStorageWasCleared()) {
                storageToClear.add(updatedAddress);
                pendingStorageUpdates.clear();
              }

              // parallel stream here may cause database corruption
              updatedAccount
                  .getUpdatedStorage()
                  .entrySet()
                  .forEach(
                      storageUpdate -> {
                        final UInt256 keyUInt = storageUpdate.getKey();
                        final StorageSlotKey slotKey =
                            new StorageSlotKey(
                                wrappedWorldView().hashAndSaveSlotPreImage(keyUInt),
                                Optional.of(keyUInt));
                        final UInt256 value = storageUpdate.getValue();
                        final DiffBasedValue<UInt256> pendingValue =
                            pendingStorageUpdates.get(slotKey);
                        if (pendingValue == null) {
                          pendingStorageUpdates.put(
                              slotKey,
                              new DiffBasedValue<>(
                                  updatedAccount.getOriginalStorageValue(keyUInt), value));
                        } else {
                          pendingValue.setUpdated(value);
                        }
                      });

              updatedAccount.getUpdatedStorage().clear();

              if (pendingStorageUpdates.isEmpty()) {
                storageToUpdate.remove(updatedAddress);
              }

              if (tracked.getStorageWasCleared()) {
                tracked.setStorageWasCleared(false); // storage already cleared for this transaction
              }
            });
  }

  @Override
  protected DiffBasedWorldStateUpdateAccumulator<ACCOUNT> wrappedWorldView() {
    return (DiffBasedWorldStateUpdateAccumulator<ACCOUNT>) super.wrappedWorldView();
  }
}
