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
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.AccountConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.Consumer;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
    extends AbstractWorldUpdater<DiffBasedWorldView, ACCOUNT>
    implements DiffBasedWorldView, TrieLogAccumulator {
  private static final Logger LOG =
      LoggerFactory.getLogger(DiffBasedWorldStateUpdateAccumulator.class);
  protected final Consumer<DiffBasedValue<ACCOUNT>> accountPreloader;
  protected final Consumer<StorageSlotKey> storagePreloader;

  private final AccountConsumingMap<DiffBasedValue<ACCOUNT>> accountsToUpdate;
  private final Map<Address, DiffBasedValue<Bytes>> codeToUpdate = new ConcurrentHashMap<>();
  private final Set<Address> storageToClear = Collections.synchronizedSet(new HashSet<>());
  protected final EvmConfiguration evmConfiguration;

  // storage sub mapped by _hashed_ key.  This is because in self_destruct calls we need to
  // enumerate the old storage and delete it.  Those are trie stored by hashed key by spec and the
  // alternative was to keep a giant pre-image cache of the entire trie.
  private final Map<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
      storageToUpdate = new ConcurrentHashMap<>();

  private final Map<UInt256, Hash> storageKeyHashLookup = new ConcurrentHashMap<>();
  protected boolean isAccumulatorStateChanged;

  public DiffBasedWorldStateUpdateAccumulator(
      final DiffBasedWorldView world,
      final Consumer<DiffBasedValue<ACCOUNT>> accountPreloader,
      final Consumer<StorageSlotKey> storagePreloader,
      final EvmConfiguration evmConfiguration) {
    super(world, evmConfiguration);
    this.accountsToUpdate = new AccountConsumingMap<>(new ConcurrentHashMap<>(), accountPreloader);
    this.accountPreloader = accountPreloader;
    this.storagePreloader = storagePreloader;
    this.isAccumulatorStateChanged = false;
    this.evmConfiguration = evmConfiguration;
  }

  public void cloneFromUpdater(final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {
    accountsToUpdate.putAll(source.getAccountsToUpdate());
    codeToUpdate.putAll(source.codeToUpdate);
    storageToClear.addAll(source.storageToClear);
    storageToUpdate.putAll(source.storageToUpdate);
    updatedAccounts.putAll(source.updatedAccounts);
    deletedAccounts.addAll(source.deletedAccounts);
    this.isAccumulatorStateChanged = true;
  }

  /**
   * Integrates prior state changes from an external source into the current state. This method
   * retrieves state modifications from the specified source and adds them to the current state's
   * list of modifications. It does not remove any existing elements in the current state's
   * modification list. If a modification has been made in both the current state and the source,
   * the modification from the source will be taken. This approach ensures that the source's state
   * changes are prioritized and overrides any conflicting changes in the current state.
   *
   * @param source The source accumulator
   */
  public void importStateChangesFromSource(
      final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {
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
              accountsToUpdate.put(address, new DiffBasedValue<>(copyPrior, copyUpdated));
            });
    source
        .getCodeToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              codeToUpdate.put(
                  address,
                  new DiffBasedValue<>(diffBasedValue.getPrior(), diffBasedValue.getUpdated()));
            });
    source
        .getStorageToUpdate()
        .forEach(
            (address, slots) -> {
              StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageConsumingMap =
                  storageToUpdate.computeIfAbsent(
                      address,
                      k ->
                          new StorageConsumingMap<>(
                              address, new ConcurrentHashMap<>(), storagePreloader));
              slots.forEach(
                  (storageSlotKey, uInt256DiffBasedValue) -> {
                    storageConsumingMap.put(
                        storageSlotKey,
                        new DiffBasedValue<>(
                            uInt256DiffBasedValue.getPrior(), uInt256DiffBasedValue.getUpdated()));
                  });
            });
    storageToClear.addAll(source.storageToClear);

    this.isAccumulatorStateChanged = true;
  }

  /**
   * Imports unchanged state data from an external source into the current state. This method
   * focuses on integrating state data from the specified source that has been read but not
   * modified.
   *
   * <p>The method ensures that only new, unmodified data from the source is added to the current
   * state. If a state data has already been read or modified in the current state, it will not be
   * added again to avoid overwriting any existing modifications.
   *
   * @param source The source accumulator
   */
  public void importPriorStateFromSource(
      final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> source) {

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
    source
        .getCodeToUpdate()
        .forEach(
            (address, diffBasedValue) -> {
              codeToUpdate.putIfAbsent(
                  address,
                  new DiffBasedValue<>(diffBasedValue.getPrior(), diffBasedValue.getPrior()));
            });
    source
        .getStorageToUpdate()
        .forEach(
            (address, slots) -> {
              StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageConsumingMap =
                  storageToUpdate.computeIfAbsent(
                      address,
                      k ->
                          new StorageConsumingMap<>(
                              address, new ConcurrentHashMap<>(), storagePreloader));
              slots.forEach(
                  (storageSlotKey, uInt256DiffBasedValue) -> {
                    storageConsumingMap.putIfAbsent(
                        storageSlotKey,
                        new DiffBasedValue<>(
                            uInt256DiffBasedValue.getPrior(), uInt256DiffBasedValue.getPrior()));
                  });
            });
    this.isAccumulatorStateChanged = true;
  }

  protected Consumer<DiffBasedValue<ACCOUNT>> getAccountPreloader() {
    return accountPreloader;
  }

  protected Consumer<StorageSlotKey> getStoragePreloader() {
    return storagePreloader;
  }

  public EvmConfiguration getEvmConfiguration() {
    return evmConfiguration;
  }

  @Override
  public Account get(final Address address) {
    return super.get(address);
  }

  @Override
  protected UpdateTrackingAccount<ACCOUNT> track(final UpdateTrackingAccount<ACCOUNT> account) {
    return super.track(account);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    return super.getAccount(address);
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    DiffBasedValue<ACCOUNT> diffBasedValue = accountsToUpdate.get(address);

    if (diffBasedValue == null) {
      diffBasedValue = new DiffBasedValue<>(null, null);
      accountsToUpdate.put(address, diffBasedValue);
    } else if (diffBasedValue.getUpdated() != null) {
      if (diffBasedValue.getUpdated().isEmpty()) {
        return track(new UpdateTrackingAccount<>(diffBasedValue.getUpdated()));
      } else {
        throw new IllegalStateException("Cannot create an account when one already exists");
      }
    }

    final ACCOUNT newAccount =
        createAccount(
            this,
            address,
            hashAndSaveAccountPreImage(address),
            nonce,
            balance,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    diffBasedValue.setUpdated(newAccount);
    return track(new UpdateTrackingAccount<>(newAccount));
  }

  @Override
  public Map<Address, DiffBasedValue<ACCOUNT>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  @Override
  public Map<Address, DiffBasedValue<Bytes>> getCodeToUpdate() {
    return codeToUpdate;
  }

  public Set<Address> getStorageToClear() {
    return storageToClear;
  }

  @Override
  public Map<Address, StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>>>
      getStorageToUpdate() {
    return storageToUpdate;
  }

  @Override
  protected ACCOUNT getForMutation(final Address address) {
    return loadAccount(address, DiffBasedValue::getUpdated);
  }

  protected ACCOUNT loadAccount(
      final Address address, final Function<DiffBasedValue<ACCOUNT>, ACCOUNT> accountFunction) {
    try {
      final DiffBasedValue<ACCOUNT> diffBasedValue = accountsToUpdate.get(address);
      if (diffBasedValue == null) {
        final Account account;
        if (wrappedWorldView() instanceof DiffBasedWorldStateUpdateAccumulator) {
          final DiffBasedWorldStateUpdateAccumulator<ACCOUNT> worldStateUpdateAccumulator =
              (DiffBasedWorldStateUpdateAccumulator<ACCOUNT>) wrappedWorldView();
          account = worldStateUpdateAccumulator.loadAccount(address, accountFunction);
        } else {
          account = wrappedWorldView().get(address);
        }
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

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return getUpdatedAccounts();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return getDeletedAccounts();
  }

  @Override
  public void revert() {
    super.reset();
  }

  @Override
  public void commit() {
    this.isAccumulatorStateChanged = true;

    for (final Address deletedAddress : getDeletedAccounts()) {
      final DiffBasedValue<ACCOUNT> accountValue =
          accountsToUpdate.computeIfAbsent(
              deletedAddress,
              __ -> loadAccountFromParent(deletedAddress, new DiffBasedValue<>(null, null, true)));
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

      // mark all updated storage as to be cleared
      final Map<StorageSlotKey, DiffBasedValue<UInt256>> deletedStorageUpdates =
          storageToUpdate.computeIfAbsent(
              deletedAddress,
              k ->
                  new StorageConsumingMap<>(
                      deletedAddress, new ConcurrentHashMap<>(), storagePreloader));
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

    getUpdatedAccounts().parallelStream()
        .forEach(
            tracked -> {
              final Address updatedAddress = tracked.getAddress();
              final ACCOUNT updatedAccount;
              final DiffBasedValue<ACCOUNT> updatedAccountValue =
                  accountsToUpdate.get(updatedAddress);
              final Map<StorageSlotKey, DiffBasedValue<UInt256>> pendingStorageUpdates =
                  storageToUpdate.computeIfAbsent(
                      updatedAddress,
                      k ->
                          new StorageConsumingMap<>(
                              updatedAddress, new ConcurrentHashMap<>(), storagePreloader));

              if (tracked.getWrappedAccount() == null) {
                updatedAccount = createAccount(this, tracked);
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
                                hashAndSaveSlotPreImage(keyUInt), Optional.of(keyUInt));
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
    getUpdatedAccounts().clear();
    getDeletedAccounts().clear();
  }

  @Override
  public Optional<Bytes> getCode(final Address address, final Hash codeHash) {
    final DiffBasedValue<Bytes> localCode = codeToUpdate.get(address);
    if (localCode == null) {
      final Optional<Bytes> code = wrappedWorldView().getCode(address, codeHash);
      if (code.isEmpty() && !codeHash.equals(Hash.EMPTY)) {
        throw new MerkleTrieException(
            "invalid account code", Optional.of(address), codeHash, Bytes.EMPTY);
      }
      return code;
    } else {
      return Optional.ofNullable(localCode.getUpdated());
    }
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 slotKey) {
    StorageSlotKey storageSlotKey =
        new StorageSlotKey(hashAndSaveSlotPreImage(slotKey), Optional.of(slotKey));
    return getStorageValueByStorageSlotKey(address, storageSlotKey).orElse(UInt256.ZERO);
  }

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
          (wrappedWorldView() instanceof DiffBasedWorldState worldState)
              ? worldState.getStorageValueByStorageSlotKey(address, storageSlotKey)
              : wrappedWorldView().getStorageValueByStorageSlotKey(address, storageSlotKey);
      storageToUpdate
          .computeIfAbsent(
              address,
              key ->
                  new StorageConsumingMap<>(address, new ConcurrentHashMap<>(), storagePreloader))
          .put(
              storageSlotKey, new DiffBasedValue<>(valueUInt.orElse(null), valueUInt.orElse(null)));
      return valueUInt;
    } catch (MerkleTrieException e) {
      // need to throw to trigger the heal
      throw new MerkleTrieException(
          e.getMessage(), Optional.of(address), e.getHash(), e.getLocation());
    }
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    // TODO maybe log the read into the trie layer?
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
    final Map<Bytes32, Bytes> results = wrappedWorldView().getAllAccountStorage(address, rootHash);
    final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> diffBasedValueStorage =
        storageToUpdate.get(address);
    if (diffBasedValueStorage != null) {
      // hash the key to match the implied storage interface of hashed slotKey
      diffBasedValueStorage.forEach(
          (key, value) -> results.put(key.getSlotHash(), value.getUpdated()));
    }
    return results;
  }

  @Override
  public boolean isPersisted() {
    return true;
  }

  @Override
  public DiffBasedWorldStateKeyValueStorage getWorldStateStorage() {
    return wrappedWorldView().getWorldStateStorage();
  }

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
        accountValue.setUpdated(createAccount(wrappedWorldView(), address, replacementValue, true));
      }
    }
  }

  private DiffBasedValue<ACCOUNT> loadAccountFromParent(
      final Address address, final DiffBasedValue<ACCOUNT> defaultValue) {
    try {
      final Account parentAccount = wrappedWorldView().get(address);
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

  private void rollCodeChange(
      final Address address, final Bytes expectedCode, final Bytes replacementCode) {
    if (Objects.equals(expectedCode, replacementCode)) {
      // non-change, a cached read.
      return;
    }
    DiffBasedValue<Bytes> codeValue = codeToUpdate.get(address);
    if (codeValue == null) {
      final Bytes storedCode =
          wrappedWorldView()
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

  private Map<StorageSlotKey, DiffBasedValue<UInt256>> maybeCreateStorageMap(
      final Map<StorageSlotKey, DiffBasedValue<UInt256>> storageMap, final Address address) {
    if (storageMap == null) {
      final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> newMap =
          new StorageConsumingMap<>(address, new ConcurrentHashMap<>(), storagePreloader);
      storageToUpdate.put(address, newMap);
      return newMap;
    } else {
      return storageMap;
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
          wrappedWorldView().getStorageValueByStorageSlotKey(address, storageSlotKey);
      if (storageValue.isPresent()) {
        slotValue = new DiffBasedValue<>(storageValue.get(), storageValue.get());
        storageToUpdate
            .computeIfAbsent(
                address,
                k ->
                    new StorageConsumingMap<>(address, new ConcurrentHashMap<>(), storagePreloader))
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

  private boolean isSlotEquals(final UInt256 expectedValue, final UInt256 existingSlotValue) {
    final UInt256 sanitizedExpectedValue = (expectedValue == null) ? UInt256.ZERO : expectedValue;
    final UInt256 sanitizedExistingSlotValue =
        (existingSlotValue == null) ? UInt256.ZERO : existingSlotValue;
    return Objects.equals(sanitizedExpectedValue, sanitizedExistingSlotValue);
  }

  public boolean isAccumulatorStateChanged() {
    return isAccumulatorStateChanged;
  }

  public void resetAccumulatorStateChanged() {
    isAccumulatorStateChanged = false;
  }

  @Override
  public void reset() {
    storageToClear.clear();
    storageToUpdate.clear();
    codeToUpdate.clear();
    accountsToUpdate.clear();
    resetAccumulatorStateChanged();
    updatedAccounts.clear();
    deletedAccounts.clear();
    storageKeyHashLookup.clear();
  }

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
      final DiffBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final boolean mutable);

  protected abstract ACCOUNT createAccount(
      final DiffBasedWorldView context, final UpdateTrackingAccount<ACCOUNT> tracked);

  protected abstract void assertCloseEnoughForDiffing(
      final ACCOUNT source, final AccountValue account, final String context);
}
