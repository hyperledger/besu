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
 *
 */

package org.hyperledger.besu.ethereum.trie.bonsai.worldview;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.google.common.collect.ForwardingMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiWorldStateUpdateAccumulator
    extends AbstractWorldUpdater<BonsaiWorldView, BonsaiAccount>
    implements BonsaiWorldView, TrieLogAccumulator {
  private static final Logger LOG =
      LoggerFactory.getLogger(BonsaiWorldStateUpdateAccumulator.class);
  private final Consumer<BonsaiValue<BonsaiAccount>> accountPreloader;
  private final Consumer<StorageSlotKey> storagePreloader;

  private final AccountConsumingMap<BonsaiValue<BonsaiAccount>> accountsToUpdate;
  private final Map<Address, BonsaiValue<Bytes>> codeToUpdate = new ConcurrentHashMap<>();
  private final Set<Address> storageToClear = Collections.synchronizedSet(new HashSet<>());
  private final EvmConfiguration evmConfiguration;

  // storage sub mapped by _hashed_ key.  This is because in self_destruct calls we need to
  // enumerate the old storage and delete it.  Those are trie stored by hashed key by spec and the
  // alternative was to keep a giant pre-image cache of the entire trie.
  private final Map<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>
      storageToUpdate = new ConcurrentHashMap<>();

  private final Map<UInt256, Hash> slotKeyToHashCache = new ConcurrentHashMap<>();
  private boolean isAccumulatorStateChanged;

  public BonsaiWorldStateUpdateAccumulator(
      final BonsaiWorldView world,
      final Consumer<BonsaiValue<BonsaiAccount>> accountPreloader,
      final Consumer<StorageSlotKey> storagePreloader,
      final EvmConfiguration evmConfiguration) {
    super(world, evmConfiguration);
    this.accountsToUpdate = new AccountConsumingMap<>(new ConcurrentHashMap<>(), accountPreloader);
    this.accountPreloader = accountPreloader;
    this.storagePreloader = storagePreloader;
    this.isAccumulatorStateChanged = false;
    this.evmConfiguration = evmConfiguration;
  }

  public BonsaiWorldStateUpdateAccumulator copy() {
    final BonsaiWorldStateUpdateAccumulator copy =
        new BonsaiWorldStateUpdateAccumulator(
            wrappedWorldView(), accountPreloader, storagePreloader, evmConfiguration);
    copy.cloneFromUpdater(this);
    return copy;
  }

  void cloneFromUpdater(final BonsaiWorldStateUpdateAccumulator source) {
    accountsToUpdate.putAll(source.getAccountsToUpdate());
    codeToUpdate.putAll(source.codeToUpdate);
    storageToClear.addAll(source.storageToClear);
    storageToUpdate.putAll(source.storageToUpdate);
    updatedAccounts.putAll(source.updatedAccounts);
    deletedAccounts.addAll(source.deletedAccounts);
    this.isAccumulatorStateChanged = true;
  }

  @Override
  public Account get(final Address address) {
    return super.get(address);
  }

  @Override
  protected UpdateTrackingAccount<BonsaiAccount> track(
      final UpdateTrackingAccount<BonsaiAccount> account) {
    return super.track(account);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    return super.getAccount(address);
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);

    if (bonsaiValue == null) {
      bonsaiValue = new BonsaiValue<>(null, null);
      accountsToUpdate.put(address, bonsaiValue);
    } else if (bonsaiValue.getUpdated() != null) {
      if (bonsaiValue.getUpdated().isEmpty()) {
        return track(new UpdateTrackingAccount<>(bonsaiValue.getUpdated()));
      } else {
        throw new IllegalStateException("Cannot create an account when one already exists");
      }
    }

    final BonsaiAccount newAccount =
        new BonsaiAccount(
            this,
            address,
            hashAndSaveAccountPreImage(address),
            nonce,
            balance,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    bonsaiValue.setUpdated(newAccount);
    return track(new UpdateTrackingAccount<>(newAccount));
  }

  @Override
  public Map<Address, BonsaiValue<BonsaiAccount>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  @Override
  public Map<Address, BonsaiValue<Bytes>> getCodeToUpdate() {
    return codeToUpdate;
  }

  public Set<Address> getStorageToClear() {
    return storageToClear;
  }

  @Override
  public Map<Address, StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>>>
      getStorageToUpdate() {
    return storageToUpdate;
  }

  @Override
  protected BonsaiAccount getForMutation(final Address address) {
    return loadAccount(address, BonsaiValue::getUpdated);
  }

  protected BonsaiAccount loadAccount(
      final Address address,
      final Function<BonsaiValue<BonsaiAccount>, BonsaiAccount> bonsaiAccountFunction) {
    try {
      final BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
      if (bonsaiValue == null) {
        final Account account;
        if (wrappedWorldView()
            instanceof BonsaiWorldStateUpdateAccumulator bonsaiWorldStateUpdateAccumulator) {
          account = bonsaiWorldStateUpdateAccumulator.loadAccount(address, bonsaiAccountFunction);
        } else {
          account = wrappedWorldView().get(address);
        }
        if (account instanceof BonsaiAccount bonsaiAccount) {
          BonsaiAccount mutableAccount = new BonsaiAccount(bonsaiAccount, this, true);
          accountsToUpdate.put(address, new BonsaiValue<>(bonsaiAccount, mutableAccount));
          return mutableAccount;
        } else {
          // add the empty read in accountsToUpdate
          accountsToUpdate.put(address, new BonsaiValue<>(null, null));
          return null;
        }
      } else {
        return bonsaiAccountFunction.apply(bonsaiValue);
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
      final BonsaiValue<BonsaiAccount> accountValue =
          accountsToUpdate.computeIfAbsent(
              deletedAddress,
              __ -> loadAccountFromParent(deletedAddress, new BonsaiValue<>(null, null, true)));
      storageToClear.add(deletedAddress);
      final BonsaiValue<Bytes> codeValue = codeToUpdate.get(deletedAddress);
      if (codeValue != null) {
        codeValue.setUpdated(null).setCleared();
      } else {
        wrappedWorldView()
            .getCode(
                deletedAddress,
                Optional.ofNullable(accountValue)
                    .map(BonsaiValue::getPrior)
                    .map(BonsaiAccount::getCodeHash)
                    .orElse(Hash.EMPTY))
            .ifPresent(
                deletedCode ->
                    codeToUpdate.put(deletedAddress, new BonsaiValue<>(deletedCode, null, true)));
      }

      // mark all updated storage as to be cleared
      final Map<StorageSlotKey, BonsaiValue<UInt256>> deletedStorageUpdates =
          storageToUpdate.computeIfAbsent(
              deletedAddress,
              k ->
                  new StorageConsumingMap<>(
                      deletedAddress, new ConcurrentHashMap<>(), storagePreloader));
      final Iterator<Map.Entry<StorageSlotKey, BonsaiValue<UInt256>>> iter =
          deletedStorageUpdates.entrySet().iterator();
      while (iter.hasNext()) {
        final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> updateEntry = iter.next();
        final BonsaiValue<UInt256> updatedSlot = updateEntry.getValue();
        if (updatedSlot.getPrior() == null || updatedSlot.getPrior().isZero()) {
          iter.remove();
        } else {
          updatedSlot.setUpdated(null).setCleared();
        }
      }

      final BonsaiAccount originalValue = accountValue.getPrior();
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
                    deletedStorageUpdates.put(storageSlotKey, new BonsaiValue<>(value, null, true));
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
              final BonsaiAccount updatedAccount;
              final BonsaiValue<BonsaiAccount> updatedAccountValue =
                  accountsToUpdate.get(updatedAddress);

              final Map<StorageSlotKey, BonsaiValue<UInt256>> pendingStorageUpdates =
                  storageToUpdate.computeIfAbsent(
                      updatedAddress,
                      k ->
                          new StorageConsumingMap<>(
                              updatedAddress, new ConcurrentHashMap<>(), storagePreloader));

              if (tracked.getStorageWasCleared()) {
                storageToClear.add(updatedAddress);
                pendingStorageUpdates.clear();
              }

              if (tracked.getWrappedAccount() == null) {
                updatedAccount = new BonsaiAccount(this, tracked);
                tracked.setWrappedAccount(updatedAccount);
                if (updatedAccountValue == null) {
                  accountsToUpdate.put(updatedAddress, new BonsaiValue<>(null, updatedAccount));
                  codeToUpdate.put(
                      updatedAddress, new BonsaiValue<>(null, updatedAccount.getCode()));
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
                final BonsaiValue<Bytes> pendingCode =
                    codeToUpdate.computeIfAbsent(
                        updatedAddress,
                        addr ->
                            new BonsaiValue<>(
                                wrappedWorldView()
                                    .getCode(
                                        addr,
                                        Optional.ofNullable(updatedAccountValue)
                                            .map(BonsaiValue::getPrior)
                                            .map(BonsaiAccount::getCodeHash)
                                            .orElse(Hash.EMPTY))
                                    .orElse(null),
                                null));
                pendingCode.setUpdated(updatedAccount.getCode());
              }

              // This is especially to avoid unnecessary computation for withdrawals and
              // self-destruct beneficiaries
              if (updatedAccount.getUpdatedStorage().isEmpty()) {
                return;
              }

              final TreeSet<Map.Entry<UInt256, UInt256>> entries =
                  new TreeSet<>(Map.Entry.comparingByKey());
              entries.addAll(updatedAccount.getUpdatedStorage().entrySet());

              // parallel stream here may cause database corruption
              entries.forEach(
                  storageUpdate -> {
                    final UInt256 keyUInt = storageUpdate.getKey();
                    final StorageSlotKey slotKey =
                        new StorageSlotKey(
                            hashAndSaveSlotPreImage(updatedAddress, keyUInt),
                            Optional.of(keyUInt)); // no compute Hash in this case
                    final UInt256 value = storageUpdate.getValue();
                    final BonsaiValue<UInt256> pendingValue = pendingStorageUpdates.get(slotKey);

                    if (pendingValue == null) {
                      pendingStorageUpdates.put(
                          slotKey,
                          new BonsaiValue<>(
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
  public Optional<Bytes> getCode(final Address address, final Hash codeHash) {
    final BonsaiValue<Bytes> localCode = codeToUpdate.get(address);
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
        new StorageSlotKey(hashAndSaveSlotPreImage(address, slotKey), Optional.of(slotKey));
    return getStorageValueByStorageSlotKey(address, storageSlotKey).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    final Map<StorageSlotKey, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.get(address);
    if (localAccountStorage != null) {
      final BonsaiValue<UInt256> value = localAccountStorage.get(storageSlotKey);
      if (value != null) {
        return Optional.ofNullable(value.getUpdated());
      }
    }
    try {
      final Optional<UInt256> valueUInt =
          (wrappedWorldView() instanceof BonsaiWorldState bonsaiWorldState)
              ? bonsaiWorldState.getStorageValueByStorageSlotKey(
                  () ->
                      Optional.ofNullable(loadAccount(address, BonsaiValue::getPrior))
                          .map(BonsaiAccount::getStorageRoot),
                  address,
                  storageSlotKey)
              : wrappedWorldView().getStorageValueByStorageSlotKey(address, storageSlotKey);
      storageToUpdate
          .computeIfAbsent(
              address,
              key ->
                  new StorageConsumingMap<>(address, new ConcurrentHashMap<>(), storagePreloader))
          .put(storageSlotKey, new BonsaiValue<>(valueUInt.orElse(null), valueUInt.orElse(null)));

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
        new StorageSlotKey(hashAndSaveSlotPreImage(address, storageKey), Optional.of(storageKey));
    final Map<StorageSlotKey, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.get(address);
    if (localAccountStorage != null) {
      final BonsaiValue<UInt256> value = localAccountStorage.get(storageSlotKey);
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
    final StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>> bonsaiValueStorage =
        storageToUpdate.get(address);
    if (bonsaiValueStorage != null) {
      // hash the key to match the implied storage interface of hashed slotKey
      bonsaiValueStorage.forEach(
          (key, value) -> results.put(key.getSlotHash(), value.getUpdated()));
    }
    return results;
  }

  @Override
  public boolean isPersisted() {
    return true;
  }

  @Override
  public BonsaiWorldStateKeyValueStorage getWorldStateStorage() {
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
    BonsaiValue<BonsaiAccount> accountValue = accountsToUpdate.get(address);
    if (accountValue == null) {
      accountValue = loadAccountFromParent(address, accountValue);
    }
    if (accountValue == null) {
      if (expectedValue == null && replacementValue != null) {
        accountsToUpdate.put(
            address,
            new BonsaiValue<>(null, new BonsaiAccount(this, address, replacementValue, true)));
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
        BonsaiAccount.assertCloseEnoughForDiffing(
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
        accountValue.setUpdated(
            new BonsaiAccount(wrappedWorldView(), address, replacementValue, true));
      }
    }
  }

  private BonsaiValue<BonsaiAccount> loadAccountFromParent(
      final Address address, final BonsaiValue<BonsaiAccount> defaultValue) {
    try {
      final Account parentAccount = wrappedWorldView().get(address);
      if (parentAccount instanceof BonsaiAccount account) {
        final BonsaiValue<BonsaiAccount> loadedAccountValue =
            new BonsaiValue<>(new BonsaiAccount(account), account);
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
    BonsaiValue<Bytes> codeValue = codeToUpdate.get(address);
    if (codeValue == null) {
      final Bytes storedCode =
          wrappedWorldView()
              .getCode(
                  address, Optional.ofNullable(expectedCode).map(Hash::hash).orElse(Hash.EMPTY))
              .orElse(Bytes.EMPTY);
      if (!storedCode.isEmpty()) {
        codeValue = new BonsaiValue<>(storedCode, storedCode);
        codeToUpdate.put(address, codeValue);
      }
    }

    if (codeValue == null) {
      if ((expectedCode == null || expectedCode.isEmpty()) && replacementCode != null) {
        codeToUpdate.put(address, new BonsaiValue<>(null, replacementCode));
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

  private Map<StorageSlotKey, BonsaiValue<UInt256>> maybeCreateStorageMap(
      final Map<StorageSlotKey, BonsaiValue<UInt256>> storageMap, final Address address) {
    if (storageMap == null) {
      final StorageConsumingMap<StorageSlotKey, BonsaiValue<UInt256>> newMap =
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
    final Map<StorageSlotKey, BonsaiValue<UInt256>> storageMap = storageToUpdate.get(address);
    BonsaiValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(storageSlotKey);
    if (slotValue == null) {
      final Optional<UInt256> storageValue =
          wrappedWorldView().getStorageValueByStorageSlotKey(address, storageSlotKey);
      if (storageValue.isPresent()) {
        slotValue = new BonsaiValue<>(storageValue.get(), storageValue.get());
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
            .put(storageSlotKey, new BonsaiValue<>(null, replacementValue));
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
        final Map<StorageSlotKey, BonsaiValue<UInt256>> thisStorageUpdate =
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
    slotKeyToHashCache.clear();
  }

  public static class AccountConsumingMap<T> extends ForwardingMap<Address, T> {

    private final ConcurrentMap<Address, T> accounts;
    private final Consumer<T> consumer;

    public AccountConsumingMap(
        final ConcurrentMap<Address, T> accounts, final Consumer<T> consumer) {
      this.accounts = accounts;
      this.consumer = consumer;
    }

    @Override
    public T put(@NotNull final Address address, @NotNull final T value) {
      consumer.process(address, value);
      return accounts.put(address, value);
    }

    public Consumer<T> getConsumer() {
      return consumer;
    }

    @Override
    protected Map<Address, T> delegate() {
      return accounts;
    }
  }

  public static class StorageConsumingMap<K, T> extends ForwardingMap<K, T> {

    private final Address address;

    private final ConcurrentMap<K, T> storages;
    private final Consumer<K> consumer;

    public StorageConsumingMap(
        final Address address, final ConcurrentMap<K, T> storages, final Consumer<K> consumer) {
      this.address = address;
      this.storages = storages;
      this.consumer = consumer;
    }

    @Override
    public T put(@NotNull final K slotKey, @NotNull final T value) {
      consumer.process(address, slotKey);
      return storages.put(slotKey, value);
    }

    public Consumer<K> getConsumer() {
      return consumer;
    }

    @Override
    protected Map<K, T> delegate() {
      return storages;
    }
  }

  public interface Consumer<T> {
    void process(final Address address, T value);
  }

  protected Hash hashAndSaveAccountPreImage(final Address address) {
    return Hash.hash(address);
  }

  protected Hash hashAndSaveSlotPreImage(final Address address, final UInt256 slotKey) {
    Hash hash = slotKeyToHashCache.get(slotKey);
    if (hash == null) {
      hash = Hash.hash(slotKey);
      slotKeyToHashCache.put(slotKey, hash);
    }
    return hash;
  }
}
