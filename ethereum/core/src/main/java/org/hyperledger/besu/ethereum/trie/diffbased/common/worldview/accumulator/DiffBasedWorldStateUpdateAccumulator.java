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

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.AccountConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.PreloaderManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public abstract class DiffBasedWorldStateUpdateAccumulator<ACCOUNT extends DiffBasedAccount>
    implements WorldUpdater, DiffBasedWorldView, TrieLogAccumulator {

  private static final Logger LOG =
      LoggerFactory.getLogger(DiffBasedWorldStateUpdateAccumulator.class);

  // Preloader manager for accounts and storage.
  protected final PreloaderManager<ACCOUNT> preloaderManager;

  private final DiffBasedWorldState parentWorldView;

  // Maps tracking modifications to accounts, code and storage.
  private final AccountConsumingMap<DiffBasedValue<ACCOUNT>> accountsToUpdate;
  private final Map<Address, DiffBasedValue<Bytes>> codeToUpdate = new ConcurrentHashMap<>();
  private final Set<Address> storageToClear = Collections.synchronizedSet(new HashSet<>());
  private final Map<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
      storageToUpdate = new ConcurrentHashMap<>();

  // Cache for storage key pre-images.
  private final Map<UInt256, Hash> storageKeyHashLookup = new ConcurrentHashMap<>();

  // EVM configuration reference.
  protected final EvmConfiguration evmConfiguration;

  protected AccumulatorUpdater<ACCOUNT> updater;

  public DiffBasedWorldStateUpdateAccumulator(
      final DiffBasedWorldState parentWorldView,
      final PreloaderManager<ACCOUNT> preloaderManager,
      final EvmConfiguration evmConfiguration) {
    this.parentWorldView = parentWorldView;
    this.preloaderManager = preloaderManager;
    this.accountsToUpdate =
        new AccountConsumingMap<>(
            new ConcurrentHashMap<>(), preloaderManager.getAccountPreloader(parentWorldView));
    this.evmConfiguration = evmConfiguration;
    this.updater = new AccumulatorUpdater<>(this, preloaderManager, evmConfiguration);
  }

  // GETTERS & BASIC WORLDVIEW METHODS

  @Override
  public Account get(final Address address) {
    return loadAccount(address, DiffBasedValue::getUpdated);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    final ACCOUNT origin = loadAccount(address, DiffBasedValue::getUpdated);
    if (origin == null) {
      return null;
    } else {
      return updater.track(new UpdateTrackingAccount<>(origin));
    }
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    DiffBasedValue<ACCOUNT> diffBasedValue = accountsToUpdate.get(address);

    if (diffBasedValue == null) {
      diffBasedValue = new DiffBasedValue<>(null, null);
      accountsToUpdate.put(address, diffBasedValue);
    } else if (diffBasedValue.getUpdated() != null) {
      if (diffBasedValue.getUpdated().isEmpty()) {
        return updater.track(new UpdateTrackingAccount<>(diffBasedValue.getUpdated()));
      } else {
        throw new IllegalStateException("Cannot create an account when one already exists");
      }
    }

    final ACCOUNT newAccount =
        createAccount(
            address,
            hashAndSaveAccountPreImage(address),
            nonce,
            balance,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    diffBasedValue.setUpdated(newAccount);
    return updater.track(new UpdateTrackingAccount<>(newAccount));
  }

  protected ACCOUNT loadAccount(
      final Address address, final Function<DiffBasedValue<ACCOUNT>, ACCOUNT> accountFunction) {
    try {
      final DiffBasedValue<ACCOUNT> diffBasedValue = accountsToUpdate.get(address);
      if (diffBasedValue == null) {
        final Account account = parentWorldView.get(address);
        if (account instanceof DiffBasedAccount diffBasedAccount) {
          ACCOUNT mutableAccount = copyAccount((ACCOUNT) diffBasedAccount, this, true);
          accountsToUpdate.put(
              address, new DiffBasedValue<>((ACCOUNT) diffBasedAccount, mutableAccount));
          return mutableAccount;
        } else {
          // add the empty read in accountsToUpdate
          accountsToUpdate.put(address, new DiffBasedValue<>(null, null));
          return null;
        }
      } else {
        return accountFunction.apply(diffBasedValue);
      }
    } catch (MerkleTrieException e) {
      // need to throw to trigger the heal
      throw new MerkleTrieException(
          e.getMessage(), Optional.of(address), e.getHash(), e.getLocation());
    }
  }

  /**
   * Retrieves the code associated with the given account address.
   *
   * <p>If there is a locally updated code entry in the accumulator, it returns that; otherwise, it
   * fetches the code from the underlying world view. If the code is not found and the provided
   * codeHash is not empty, a {@link MerkleTrieException} is thrown.
   *
   * @param address the account address.
   * @param codeHash the expected hash of the code.
   * @return an Optional containing the account's code if available.
   */
  @Override
  public Optional<Bytes> getCode(final Address address, final Hash codeHash) {
    final DiffBasedValue<Bytes> localCode = codeToUpdate.get(address);
    if (localCode == null) {
      final Optional<Bytes> code = parentWorldView.getCode(address, codeHash);
      if (code.isEmpty() && !codeHash.equals(Hash.EMPTY)) {
        throw new MerkleTrieException(
            "invalid account code", Optional.of(address), codeHash, Bytes.EMPTY);
      }
      return code;
    } else {
      return Optional.ofNullable(localCode.getUpdated());
    }
  }

  /**
   * Retrieves the storage value for a given account at the specified slot.
   *
   * <p>The method converts the provided slot key (UInt256) into a StorageSlotKey by hashing it. It
   * then delegates to {@code getStorageValueByStorageSlotKey}. If no value is found, it defaults to
   * returning {@link UInt256#ZERO}.
   *
   * @param address the account address.
   * @param slotKey the storage slot key.
   * @return the storage value at the specified slot, or {@link UInt256#ZERO} if absent.
   */
  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 slotKey) {
    StorageSlotKey storageSlotKey =
        new StorageSlotKey(hashAndSaveSlotPreImage(slotKey), Optional.of(slotKey));
    return getStorageValueByStorageSlotKey(address, storageSlotKey).orElse(UInt256.ZERO);
  }

  /**
   * Retrieves the storage value for a given account using a specific StorageSlotKey.
   *
   * <p>The method first checks if there is a locally updated value in the accumulator. If not, it
   * attempts to fetch the value from the underlying world view and then caches it in the
   * accumulator.
   *
   * @param address the account address.
   * @param storageSlotKey the storage slot key (which includes the hashed key).
   * @return an Optional containing the storage value if present, otherwise an empty Optional.
   */
  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    final Map<StorageSlotKey, DiffBasedValue<UInt256>> localAccountStorage =
        storageToUpdate.get(address);
    if (localAccountStorage != null) {
      final DiffBasedValue<UInt256> value = localAccountStorage.get(storageSlotKey);
      if (value != null) {
        return Optional.ofNullable(value.getUpdated());
      }
    }
    try {
      final Optional<UInt256> valueUInt =
          parentWorldView.getStorageValueByStorageSlotKey(address, storageSlotKey);
      storageToUpdate
          .computeIfAbsent(
              address,
              key ->
                  new StorageConsumingMap<>(
                      address,
                      new ConcurrentHashMap<>(),
                      preloaderManager.getStoragePreloader(parentWorldView)))
          .put(
              storageSlotKey, new DiffBasedValue<>(valueUInt.orElse(null), valueUInt.orElse(null)));
      return valueUInt;
    } catch (MerkleTrieException e) {
      throw new MerkleTrieException(
          e.getMessage(), Optional.of(address), e.getHash(), e.getLocation());
    }
  }

  /**
   * Retrieves the prior (original) storage value for a given account at the specified slot.
   *
   * <p>The method first attempts to find a local value (either updated or prior) from the
   * accumulator. If the value was cleared or no such value exists, or if the account's storage is
   * marked for clearing, it returns {@link UInt256#ZERO}.
   *
   * @param address the account address.
   * @param storageKey the storage slot key as a {@link UInt256}.
   * @return the prior storage value at the given slot, or {@link UInt256#ZERO} if not present or
   *     cleared.
   */
  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    StorageSlotKey storageSlotKey =
        new StorageSlotKey(hashAndSaveSlotPreImage(storageKey), Optional.of(storageKey));
    final Map<StorageSlotKey, DiffBasedValue<UInt256>> localAccountStorage =
        storageToUpdate.get(address);
    if (localAccountStorage != null) {
      final DiffBasedValue<UInt256> value = localAccountStorage.get(storageSlotKey);
      if (value != null) {
        if (value.isLastStepCleared()) {
          return UInt256.ZERO;
        }
        final UInt256 updated = value.getUpdated();
        if (updated != null) {
          return updated;
        }
        final UInt256 original = value.getPrior();
        if (original != null) {
          return original;
        }
      }
    }
    if (storageToClear.contains(address)) {
      return UInt256.ZERO;
    }
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    final Map<Bytes32, Bytes> results = parentWorldView.getAllAccountStorage(address, rootHash);
    final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> diffBasedValueStorage =
        storageToUpdate.get(address);
    if (diffBasedValueStorage != null) {
      // Ensure keys are hashed to match the storage interface.
      diffBasedValueStorage.forEach(
          (key, value) -> results.put(key.getSlotHash(), value.getUpdated()));
    }
    return results;
  }

  @Override
  public DiffBasedWorldStateKeyValueStorage getWorldStateStorage() {
    return parentWorldView.getWorldStateStorage();
  }

  public EvmConfiguration getEvmConfiguration() {
    return evmConfiguration;
  }

  public PreloaderManager<ACCOUNT> getPreloaderManager() {
    return preloaderManager;
  }

  // STATE CHANGE TRACKERS
  // These methods provide access to the internal maps and sets used to track state changes.

  /**
   * Returns the map of accounts that have been modified and require updates.
   *
   * @return a map keyed by Address containing DiffBasedValue objects for accounts.
   */
  @Override
  public Map<Address, DiffBasedValue<ACCOUNT>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  /**
   * Returns the map of code updates.
   *
   * @return a map keyed by Address containing DiffBasedValue objects for code (as Bytes).
   */
  @Override
  public Map<Address, DiffBasedValue<Bytes>> getCodeToUpdate() {
    return codeToUpdate;
  }

  /**
   * Returns the set of addresses for which storage should be cleared.
   *
   * @return a set of Addresses.
   */
  public Set<Address> getStorageToClear() {
    return storageToClear;
  }

  /**
   * Returns the map of storage modifications for each account.
   *
   * @return a map keyed by Address containing StorageConsumingMap objects for storage updates.
   */
  @Override
  public Map<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
      getStorageToUpdate() {
    return storageToUpdate;
  }

  // COMMIT UPDATER & TRANSACTION BOUNDARY METHODS

  /**
   * Commits all accumulated changes by processing deletions and updates for accounts, code, and
   * storage.
   */
  @Override
  public void commit() {
    updater.commit();
  }

  @Override
  public WorldUpdater updater() {
    return updater;
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return updater.getTouchedAccounts();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return updater.getDeletedAccountAddresses();
  }

  @Override
  public void deleteAccount(final Address address) {
    updater.deleteAccount(address);
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.empty();
  }

  public DiffBasedWorldState getParentWorldView() {
    return parentWorldView;
  }

  @Override
  public boolean isModifyingHeadWorldState() {
    return true;
  }

  // STATE IMPORT METHODS

  /**
   * Clones state changes from another accumulator.
   *
   * @param source the source accumulator to clone from.
   */
  public void cloneFromUpdater(final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {
    accountsToUpdate.putAll(source.getAccountsToUpdate());
    codeToUpdate.putAll(source.codeToUpdate);
    storageToClear.addAll(source.storageToClear);
    storageToUpdate.putAll(source.storageToUpdate);
  }

  /**
   * Imports state modifications from an external source into the current state. Conflicting changes
   * are overridden by the source.
   *
   * @param source the source accumulator.
   */
  public void importStateChangesFromSource(
      final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {

    // Import account changes.
    source
        .getAccountsToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              ACCOUNT copyPrior =
                  diffBasedValue.getPrior() != null
                      ? copyAccount(diffBasedValue.getPrior(), this, false)
                      : null;
              ACCOUNT copyUpdated =
                  diffBasedValue.getUpdated() != null
                      ? copyAccount(diffBasedValue.getUpdated(), this, true)
                      : null;
              accountsToUpdate.put(
                  address,
                  new DiffBasedValue<>(copyPrior, copyUpdated, diffBasedValue.isLastStepCleared()));
            });

    // Import code changes.
    source
        .getCodeToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              codeToUpdate.put(
                  address,
                  new DiffBasedValue<>(
                      diffBasedValue.getPrior(),
                      diffBasedValue.getUpdated(),
                      diffBasedValue.isLastStepCleared()));
            });

    // Import storage changes.
    source
        .getStorageToUpdate()
        .forEach(
            (address, slots) -> {
              StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageConsumingMap =
                  storageToUpdate.computeIfAbsent(
                      address,
                      k ->
                          new StorageConsumingMap<>(
                              address,
                              new ConcurrentHashMap<>(),
                              preloaderManager.getStoragePreloader(parentWorldView)));
              slots.forEach(
                  (storageSlotKey, uInt256DiffBasedValue) -> {
                    storageConsumingMap.put(
                        storageSlotKey,
                        new DiffBasedValue<>(
                            uInt256DiffBasedValue.getPrior(),
                            uInt256DiffBasedValue.getUpdated(),
                            uInt256DiffBasedValue.isLastStepCleared()));
                  });
            });

    storageToClear.addAll(source.storageToClear);
    storageKeyHashLookup.putAll(source.storageKeyHashLookup);
  }

  /**
   * Imports prior (unchanged) state data from an external source. Only data that has not been
   * modified is imported.
   *
   * @param source the source accumulator.
   */
  public void importPriorStateFromSource(
      final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {

    // Import prior state for accounts.
    source
        .getAccountsToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              ACCOUNT copyPrior =
                  diffBasedValue.getPrior() != null
                      ? copyAccount(diffBasedValue.getPrior(), this, false)
                      : null;
              ACCOUNT copyUpdated =
                  diffBasedValue.getPrior() != null
                      ? copyAccount(diffBasedValue.getPrior(), this, true)
                      : null;
              accountsToUpdate.putIfAbsent(address, new DiffBasedValue<>(copyPrior, copyUpdated));
            });

    // Import prior state for code.
    source
        .getCodeToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              codeToUpdate.putIfAbsent(
                  address,
                  new DiffBasedValue<>(diffBasedValue.getPrior(), diffBasedValue.getPrior()));
            });

    // Import prior state for storage.
    source
        .getStorageToUpdate()
        .forEach(
            (address, slots) -> {
              StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageConsumingMap =
                  storageToUpdate.computeIfAbsent(
                      address,
                      k ->
                          new StorageConsumingMap<>(
                              address,
                              new ConcurrentHashMap<>(),
                              preloaderManager.getStoragePreloader(parentWorldView)));
              slots.forEach(
                  (storageSlotKey, uInt256DiffBasedValue) -> {
                    storageConsumingMap.putIfAbsent(
                        storageSlotKey,
                        new DiffBasedValue<>(
                            uInt256DiffBasedValue.getPrior(), uInt256DiffBasedValue.getPrior()));
                  });
            });

    storageKeyHashLookup.putAll(source.storageKeyHashLookup);
  }

  // ROLLBACK/ROLLFORWARD METHODS

  public void rollForward(final TrieLog layer) {
    layer
        .getAccountChanges()
        .forEach(
            (address, change) ->
                rollAccountChange(address, change.getPrior(), change.getUpdated()));
    layer
        .getCodeChanges()
        .forEach(
            (address, change) -> rollCodeChange(address, change.getPrior(), change.getUpdated()));
    layer
        .getStorageChanges()
        .forEach(
            (address, storage) ->
                storage.forEach(
                    (storageSlotKey, value) ->
                        rollStorageChange(
                            address, storageSlotKey, value.getPrior(), value.getUpdated())));
  }

  public void rollBack(final TrieLog layer) {
    layer
        .getAccountChanges()
        .forEach(
            (address, change) ->
                rollAccountChange(address, change.getUpdated(), change.getPrior()));
    layer
        .getCodeChanges()
        .forEach(
            (address, change) -> rollCodeChange(address, change.getUpdated(), change.getPrior()));
    layer
        .getStorageChanges()
        .forEach(
            (address, storage) ->
                storage.forEach(
                    (storageSlotKey, value) ->
                        rollStorageChange(
                            address, storageSlotKey, value.getUpdated(), value.getPrior())));
  }

  private void rollAccountChange(
      final Address address,
      final AccountValue expectedValue,
      final AccountValue replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    DiffBasedValue<ACCOUNT> accountValue = accountsToUpdate.get(address);
    if (accountValue == null) {
      accountValue = loadAccountFromParent(address, accountValue);
    }
    if (accountValue == null) {
      if (expectedValue == null && replacementValue != null) {
        accountsToUpdate.put(
            address,
            new DiffBasedValue<>(null, createAccount(this, address, replacementValue, true)));
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected to update account, but the account does not exist. Address=%s", address));
      }
    } else {
      if (expectedValue == null) {
        if (accountValue.getUpdated() != null) {
          throw new IllegalStateException(
              String.format(
                  "Expected to create account, but the account exists.  Address=%s", address));
        }
      } else {
        assertCloseEnoughForDiffing(
            accountValue.getUpdated(),
            expectedValue,
            "Address=" + address + " Prior Value in Rolling Change");
      }
      if (replacementValue == null) {
        if (accountValue.getPrior() == null) {
          // TODO: should we remove from the parent accumulated change also?  only if it is a
          // private copy
          accountsToUpdate.remove(address);
        } else {
          accountValue.setUpdated(null);
        }
      } else {
        accountValue.setUpdated(createAccount(parentWorldView, address, replacementValue, true));
      }
    }
  }

  private void rollCodeChange(
      final Address address, final Bytes expectedCode, final Bytes replacementCode) {
    if (Objects.equals(expectedCode, replacementCode)) {
      // non-change, a cached read.
      return;
    }
    DiffBasedValue<Bytes> codeValue = codeToUpdate.get(address);
    if (codeValue == null) {
      final Bytes storedCode =
          parentWorldView
              .getCode(
                  address, Optional.ofNullable(expectedCode).map(Hash::hash).orElse(Hash.EMPTY))
              .orElse(Bytes.EMPTY);
      if (!storedCode.isEmpty()) {
        codeValue = new DiffBasedValue<>(storedCode, storedCode);
        codeToUpdate.put(address, codeValue);
      }
    }

    if (codeValue == null) {
      if ((expectedCode == null || expectedCode.isEmpty()) && replacementCode != null) {
        codeToUpdate.put(address, new DiffBasedValue<>(null, replacementCode));
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected to update code, but the code does not exist.  Address=%s", address));
      }
    } else {
      final Bytes existingCode = codeValue.getUpdated();
      if ((expectedCode == null || expectedCode.isEmpty())
          && existingCode != null
          && !existingCode.isEmpty()) {
        LOG.warn("At Address={}, expected to create code, but code exists. Overwriting.", address);
      } else if (!Objects.equals(expectedCode, existingCode)) {
        throw new IllegalStateException(
            String.format(
                "Old value of code does not match expected value.  Address=%s ExpectedHash=%s ActualHash=%s",
                address,
                expectedCode == null ? "null" : Hash.hash(expectedCode),
                Hash.hash(codeValue.getUpdated())));
      }
      if (replacementCode == null && codeValue.getPrior() == null) {
        codeToUpdate.remove(address);
      } else {
        codeValue.setUpdated(replacementCode);
      }
    }
  }

  private void rollStorageChange(
      final Address address,
      final StorageSlotKey storageSlotKey,
      final UInt256 expectedValue,
      final UInt256 replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    if (replacementValue == null && expectedValue != null && expectedValue.isZero()) {
      // corner case on deletes, non-change
      return;
    }
    final Map<StorageSlotKey, DiffBasedValue<UInt256>> storageMap = storageToUpdate.get(address);
    DiffBasedValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(storageSlotKey);
    if (slotValue == null) {
      final Optional<UInt256> storageValue =
          parentWorldView.getStorageValueByStorageSlotKey(address, storageSlotKey);
      if (storageValue.isPresent()) {
        slotValue = new DiffBasedValue<>(storageValue.get(), storageValue.get());
        storageToUpdate
            .computeIfAbsent(
                address,
                k ->
                    new StorageConsumingMap<>(
                        address,
                        new ConcurrentHashMap<>(),
                        preloaderManager.getStoragePreloader(parentWorldView)))
            .put(storageSlotKey, slotValue);
      }
    }
    if (slotValue == null) {
      if ((expectedValue == null || expectedValue.isZero()) && replacementValue != null) {
        maybeCreateStorageMap(storageMap, address)
            .put(storageSlotKey, new DiffBasedValue<>(null, replacementValue));
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected to update storage value, but the slot does not exist. Account=%s SlotKey=%s",
                address, storageSlotKey));
      }
    } else {
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if ((expectedValue == null || expectedValue.isZero())
          && existingSlotValue != null
          && !existingSlotValue.isZero()) {
        throw new IllegalStateException(
            String.format(
                "Expected to create slot, but the slot exists. Account=%s SlotKey=%s expectedValue=%s existingValue=%s",
                address, storageSlotKey, expectedValue, existingSlotValue));
      }
      if (!isSlotEquals(expectedValue, existingSlotValue)) {
        throw new IllegalStateException(
            String.format(
                "Old value of slot does not match expected value. Account=%s SlotKey=%s Expected=%s Actual=%s",
                address,
                storageSlotKey,
                expectedValue == null ? "null" : expectedValue.toShortHexString(),
                existingSlotValue == null ? "null" : existingSlotValue.toShortHexString()));
      }
      if (replacementValue == null && slotValue.getPrior() == null) {
        final Map<StorageSlotKey, DiffBasedValue<UInt256>> thisStorageUpdate =
            maybeCreateStorageMap(storageMap, address);
        thisStorageUpdate.remove(storageSlotKey);
        if (thisStorageUpdate.isEmpty()) {
          storageToUpdate.remove(address);
        }
      } else {
        slotValue.setUpdated(replacementValue);
      }
    }
  }

  protected DiffBasedValue<ACCOUNT> loadAccountFromParent(
      final Address address, final DiffBasedValue<ACCOUNT> defaultValue) {
    try {
      final Account parentAccount = parentWorldView.get(address);
      if (parentAccount instanceof DiffBasedAccount account) {
        final DiffBasedValue<ACCOUNT> loadedAccountValue =
            new DiffBasedValue<>(copyAccount((ACCOUNT) account), ((ACCOUNT) account));
        accountsToUpdate.put(address, loadedAccountValue);
        return loadedAccountValue;
      } else {
        return defaultValue;
      }
    } catch (MerkleTrieException e) {
      // need to throw to trigger the heal
      throw new MerkleTrieException(
          e.getMessage(), Optional.of(address), e.getHash(), e.getLocation());
    }
  }

  private Map<StorageSlotKey, DiffBasedValue<UInt256>> maybeCreateStorageMap(
      final Map<StorageSlotKey, DiffBasedValue<UInt256>> storageMap, final Address address) {
    if (storageMap == null) {
      final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> newMap =
          new StorageConsumingMap<>(
              address,
              new ConcurrentHashMap<>(),
              preloaderManager.getStoragePreloader(parentWorldView));
      storageToUpdate.put(address, newMap);
      return newMap;
    } else {
      return storageMap;
    }
  }

  private boolean isSlotEquals(final UInt256 expectedValue, final UInt256 existingSlotValue) {
    final UInt256 sanitizedExpectedValue = (expectedValue == null) ? UInt256.ZERO : expectedValue;
    final UInt256 sanitizedExistingSlotValue =
        (existingSlotValue == null) ? UInt256.ZERO : existingSlotValue;
    return Objects.equals(sanitizedExpectedValue, sanitizedExistingSlotValue);
  }

  // STATE RESET & REVERT METHODS

  /**
   * Resets the accumulator by clearing all changes, including those that have been committed.
   *
   * <p>This method clears all internal maps and data structures that track changes. This includes
   * clearing the storage to clear, storage to update, code to update, accounts to update, and other
   * related data structures. This effectively removes all changes, even those that have been
   * committed in the accumulator.
   */
  public void reset() {
    storageToClear.clear();
    storageToUpdate.clear();
    codeToUpdate.clear();
    accountsToUpdate.clear();
    storageKeyHashLookup.clear();
    updater.revert();
  }

  /**
   * Reverts all changes that have not yet been committed.
   *
   * <p>This method calls the `reset` method of the superclass, which cancels all changes that have
   * not yet been committed. This effectively reverts the state to the last committed state.
   */
  @Override
  public void revert() {
    updater.revert();
  }

  @Override
  public void markTransactionBoundary() {
    updater.revert();
  }

  // PRE-IMAGE HASHING HELPERS

  protected Hash hashAndSaveAccountPreImage(final Address address) {
    // no need to save account preimage by default
    return Hash.hash(address);
  }

  protected Hash hashAndSaveSlotPreImage(final UInt256 slotKey) {
    Hash hash = storageKeyHashLookup.get(slotKey);
    if (hash == null) {
      hash = Hash.hash(slotKey);
      storageKeyHashLookup.put(slotKey, hash);
    }
    return hash;
  }

  // ABSTRACT METHODS TO BE IMPLEMENTED BY SUBCLASSES

  public abstract DiffBasedWorldStateUpdateAccumulator<ACCOUNT> copy();

  protected abstract ACCOUNT copyAccount(final ACCOUNT account);

  protected abstract ACCOUNT copyAccount(
      final ACCOUNT toCopy, final DiffBasedWorldView context, final boolean mutable);

  protected abstract ACCOUNT createAccount(
      final DiffBasedWorldView context,
      final Address address,
      final AccountValue stateTrieAccount,
      final boolean mutable);

  protected abstract ACCOUNT createAccount(
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final boolean mutable);

  protected abstract ACCOUNT createAccount(final UpdateTrackingAccount<ACCOUNT> tracked);

  protected abstract void assertCloseEnoughForDiffing(
      final ACCOUNT source, final AccountValue account, final String context);
}
