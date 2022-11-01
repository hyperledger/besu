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

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiWorldStateUpdater extends AbstractWorldUpdater<BonsaiWorldView, BonsaiAccount>
    implements BonsaiWorldView {

  private Map<Address, BonsaiValue<BonsaiAccount>> accountsToUpdate = new HashMap<>();
  private Map<Address, BonsaiValue<Bytes>> codeToUpdate = new HashMap<>();
  private Set<Address> storageToClear = new HashSet<>();

  // storage sub mapped by _hashed_ key.  This is because in self_destruct calls we need to
  // enumerate the old storage and delete it.  Those are trie stored by hashed key by spec and the
  // alternative was to keep a giant pre-image cache of the entire trie.
  private Map<Address, Map<Hash, BonsaiValue<UInt256>>> storageToUpdate = new ConcurrentHashMap<>();

  BonsaiWorldStateUpdater(final BonsaiWorldView world) {
    super(world);
  }

  public BonsaiWorldStateUpdater copy() {
    final BonsaiWorldStateUpdater copy = new BonsaiWorldStateUpdater(wrappedWorldView());
    copy.accountsToUpdate = new HashMap<>(accountsToUpdate);
    copy.codeToUpdate = new HashMap<>(codeToUpdate);
    copy.storageToClear = new HashSet<>(storageToClear);
    copy.storageToUpdate = new ConcurrentHashMap<>(storageToUpdate);
    copy.updatedAccounts = new HashMap<>(updatedAccounts);
    copy.deletedAccounts = new HashSet<>(deletedAccounts);
    return copy;
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
  public EvmAccount getAccount(final Address address) {
    return super.getAccount(address);
  }

  @Override
  public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
    BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
    if (bonsaiValue == null) {
      bonsaiValue = new BonsaiValue<>(null, null);
      accountsToUpdate.put(address, bonsaiValue);
    } else if (bonsaiValue.getUpdated() != null) {
      throw new IllegalStateException("Cannot create an account when one already exists");
    }
    final BonsaiAccount newAccount =
        new BonsaiAccount(
            this,
            address,
            Hash.hash(address),
            nonce,
            balance,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    bonsaiValue.setUpdated(newAccount);
    return new WrappedEvmAccount(track(new UpdateTrackingAccount<>(newAccount)));
  }

  Map<Address, BonsaiValue<BonsaiAccount>> getAccountsToUpdate() {
    return accountsToUpdate;
  }

  Map<Address, BonsaiValue<Bytes>> getCodeToUpdate() {
    return codeToUpdate;
  }

  public Set<Address> getStorageToClear() {
    return storageToClear;
  }

  Map<Address, Map<Hash, BonsaiValue<UInt256>>> getStorageToUpdate() {
    return storageToUpdate;
  }

  @Override
  protected BonsaiAccount getForMutation(final Address address) {
    final BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
    if (bonsaiValue == null) {
      final Account account = wrappedWorldView().get(address);
      if (account instanceof BonsaiAccount) {
        final BonsaiAccount mutableAccount = new BonsaiAccount((BonsaiAccount) account, this, true);
        accountsToUpdate.put(address, new BonsaiValue<>((BonsaiAccount) account, mutableAccount));
        return mutableAccount;
      } else {
        return null;
      }
    } else {
      return bonsaiValue.getUpdated();
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
    for (final Address deletedAddress : getDeletedAccounts()) {
      final BonsaiValue<BonsaiAccount> accountValue =
          accountsToUpdate.computeIfAbsent(
              deletedAddress,
              __ -> loadAccountFromParent(deletedAddress, new BonsaiValue<>(null, null)));
      storageToClear.add(deletedAddress);
      final BonsaiValue<Bytes> codeValue = codeToUpdate.get(deletedAddress);
      if (codeValue != null) {
        codeValue.setUpdated(null);
      } else {
        wrappedWorldView()
            .getCode(deletedAddress)
            .ifPresent(
                deletedCode ->
                    codeToUpdate.put(deletedAddress, new BonsaiValue<>(deletedCode, null)));
      }

      // mark all updated storage as to be cleared
      final Map<Hash, BonsaiValue<UInt256>> deletedStorageUpdates =
          storageToUpdate.computeIfAbsent(deletedAddress, k -> new HashMap<>());
      final Iterator<Map.Entry<Hash, BonsaiValue<UInt256>>> iter =
          deletedStorageUpdates.entrySet().iterator();
      while (iter.hasNext()) {
        final Map.Entry<Hash, BonsaiValue<UInt256>> updateEntry = iter.next();
        final BonsaiValue<UInt256> updatedSlot = updateEntry.getValue();
        if (updatedSlot.getPrior() == null || updatedSlot.getPrior().isZero()) {
          iter.remove();
        } else {
          updatedSlot.setUpdated(null);
        }
      }

      final BonsaiAccount originalValue = accountValue.getPrior();
      if (originalValue != null) {
        // Enumerate and delete addresses not updated
        wrappedWorldView()
            .getAllAccountStorage(deletedAddress, originalValue.getStorageRoot())
            .forEach(
                (keyHash, entryValue) -> {
                  final Hash slotHash = Hash.wrap(keyHash);
                  if (!deletedStorageUpdates.containsKey(slotHash)) {
                    final UInt256 value = UInt256.fromBytes(RLP.decodeOne(entryValue));
                    deletedStorageUpdates.put(slotHash, new BonsaiValue<>(value, null, true));
                  }
                });
      }
      if (deletedStorageUpdates.isEmpty()) {
        storageToUpdate.remove(deletedAddress);
      }
      accountValue.setUpdated(null);
    }

    for (final UpdateTrackingAccount<BonsaiAccount> tracked : getUpdatedAccounts()) {
      final Address updatedAddress = tracked.getAddress();
      BonsaiAccount updatedAccount = tracked.getWrappedAccount();
      if (updatedAccount == null) {
        final BonsaiValue<BonsaiAccount> updatedAccountValue = accountsToUpdate.get(updatedAddress);
        updatedAccount = new BonsaiAccount(this, tracked);
        tracked.setWrappedAccount(updatedAccount);
        if (updatedAccountValue == null) {
          accountsToUpdate.put(updatedAddress, new BonsaiValue<>(null, updatedAccount));
          codeToUpdate.put(updatedAddress, new BonsaiValue<>(null, updatedAccount.getCode()));
        } else {
          updatedAccountValue.setUpdated(updatedAccount);
        }
      } else {
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
                addr -> new BonsaiValue<>(wrappedWorldView().getCode(addr).orElse(null), null));
        pendingCode.setUpdated(updatedAccount.getCode());
      }

      final Map<Hash, BonsaiValue<UInt256>> pendingStorageUpdates =
          storageToUpdate.computeIfAbsent(updatedAddress, __ -> new HashMap<>());
      if (tracked.getStorageWasCleared()) {
        storageToClear.add(updatedAddress);
        pendingStorageUpdates.clear();
      }

      final TreeSet<Map.Entry<UInt256, UInt256>> entries =
          new TreeSet<>(
              Comparator.comparing(
                  (Function<Map.Entry<UInt256, UInt256>, UInt256>) Map.Entry::getKey));
      entries.addAll(updatedAccount.getUpdatedStorage().entrySet());

      for (final Map.Entry<UInt256, UInt256> storageUpdate : entries) {
        final UInt256 keyUInt = storageUpdate.getKey();
        final Hash slotHash = Hash.hash(keyUInt);
        final UInt256 value = storageUpdate.getValue();
        final BonsaiValue<UInt256> pendingValue = pendingStorageUpdates.get(slotHash);
        if (pendingValue == null) {
          pendingStorageUpdates.put(
              slotHash, new BonsaiValue<>(updatedAccount.getOriginalStorageValue(keyUInt), value));
        } else {
          pendingValue.setUpdated(value);
        }
      }
      updatedAccount.getUpdatedStorage().clear();

      if (pendingStorageUpdates.isEmpty()) {
        storageToUpdate.remove(updatedAddress);
      }

      if (tracked.getStorageWasCleared()) {
        tracked.setStorageWasCleared(false); // storage already cleared for this transaction
      }

      // TODO maybe add address preimage?
    }
  }

  @Override
  public Optional<Bytes> getCode(final Address address) {
    final BonsaiValue<Bytes> localCode = codeToUpdate.get(address);
    if (localCode == null) {
      return wrappedWorldView().getCode(address);
    } else {
      return Optional.ofNullable(localCode.getUpdated());
    }
  }

  @Override
  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    // updater doesn't track trie nodes.  Always a miss.
    return Optional.empty();
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    // TODO maybe log the read into the trie layer?
    final Hash slotHashBytes = Hash.hash(storageKey);
    return getStorageValueBySlotHash(address, slotHashBytes).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    final Map<Hash, BonsaiValue<UInt256>> localAccountStorage = storageToUpdate.get(address);
    if (localAccountStorage != null) {
      final BonsaiValue<UInt256> value = localAccountStorage.get(slotHash);
      if (value != null) {
        return Optional.ofNullable(value.getUpdated());
      }
    }
    final Optional<UInt256> valueUInt =
        wrappedWorldView().getStorageValueBySlotHash(address, slotHash);
    valueUInt.ifPresent(
        v ->
            storageToUpdate
                .computeIfAbsent(address, key -> new HashMap<>())
                .put(slotHash, new BonsaiValue<>(v, v)));
    return valueUInt;
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 storageKey) {
    // TODO maybe log the read into the trie layer?
    final Map<Hash, BonsaiValue<UInt256>> localAccountStorage = storageToUpdate.get(address);
    final Hash slotHash = Hash.hash(storageKey);
    if (localAccountStorage != null) {
      final BonsaiValue<UInt256> value = localAccountStorage.get(slotHash);
      if (value != null) {
        if (value.isCleared()) {
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
    storageToUpdate.get(address).forEach((key, value) -> results.put(key, value.getUpdated()));
    return results;
  }

  public TrieLogLayer generateTrieLog(final Hash blockHash) {
    final TrieLogLayer layer = new TrieLogLayer();
    importIntoTrieLog(layer, blockHash);
    return layer;
  }

  private void importIntoTrieLog(final TrieLogLayer layer, final Hash blockHash) {
    layer.setBlockHash(blockHash);
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> updatedAccount :
        accountsToUpdate.entrySet()) {
      final BonsaiValue<BonsaiAccount> bonsaiValue = updatedAccount.getValue();
      final BonsaiAccount oldValue = bonsaiValue.getPrior();
      final StateTrieAccountValue oldAccount =
          oldValue == null
              ? null
              : new StateTrieAccountValue(
                  oldValue.getNonce(),
                  oldValue.getBalance(),
                  oldValue.getStorageRoot(),
                  oldValue.getCodeHash());
      final BonsaiAccount newValue = bonsaiValue.getUpdated();
      final StateTrieAccountValue newAccount =
          newValue == null
              ? null
              : new StateTrieAccountValue(
                  newValue.getNonce(),
                  newValue.getBalance(),
                  newValue.getStorageRoot(),
                  newValue.getCodeHash());
      layer.addAccountChange(updatedAccount.getKey(), oldAccount, newAccount);
    }

    for (final Map.Entry<Address, BonsaiValue<Bytes>> updatedCode : codeToUpdate.entrySet()) {
      layer.addCodeChange(
          updatedCode.getKey(),
          updatedCode.getValue().getPrior(),
          updatedCode.getValue().getUpdated(),
          blockHash);
    }

    for (final Map.Entry<Address, Map<Hash, BonsaiValue<UInt256>>> updatesStorage :
        storageToUpdate.entrySet()) {
      final Address address = updatesStorage.getKey();
      for (final Map.Entry<Hash, BonsaiValue<UInt256>> slotUpdate :
          updatesStorage.getValue().entrySet()) {
        layer.addStorageChange(
            address,
            slotUpdate.getKey(),
            slotUpdate.getValue().getPrior(),
            slotUpdate.getValue().getUpdated());
      }
    }
  }

  public void rollForward(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getPrior(), entry.getValue().getUpdated()));
    layer
        .streamCodeChanges()
        .forEach(
            entry ->
                rollCodeChange(
                    entry.getKey(), entry.getValue().getPrior(), entry.getValue().getUpdated()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (key, value) ->
                            rollStorageChange(
                                entry.getKey(), key, value.getPrior(), value.getUpdated())));
  }

  public void rollBack(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getUpdated(), entry.getValue().getPrior()));
    layer
        .streamCodeChanges()
        .forEach(
            entry ->
                rollCodeChange(
                    entry.getKey(), entry.getValue().getUpdated(), entry.getValue().getPrior()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (slotHash, value) ->
                            rollStorageChange(
                                entry.getKey(), slotHash, value.getUpdated(), value.getPrior())));
  }

  private void rollAccountChange(
      final Address address,
      final StateTrieAccountValue expectedValue,
      final StateTrieAccountValue replacementValue) {
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
    final Account parentAccount = wrappedWorldView().get(address);
    if (parentAccount instanceof BonsaiAccount) {
      final BonsaiAccount account = (BonsaiAccount) parentAccount;
      final BonsaiValue<BonsaiAccount> loadedAccountValue =
          new BonsaiValue<>(new BonsaiAccount(account), account);
      accountsToUpdate.put(address, loadedAccountValue);
      return loadedAccountValue;
    } else {
      return defaultValue;
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
      final Bytes storedCode = wrappedWorldView().getCode(address).orElse(Bytes.EMPTY);
      if (!storedCode.isEmpty()) {
        codeValue = new BonsaiValue<>(storedCode, storedCode);
        codeToUpdate.put(address, codeValue);
      }
    }

    if (codeValue == null) {
      if ((expectedCode == null || expectedCode.size() == 0) && replacementCode != null) {
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
        throw new IllegalStateException(
            String.format("Expected to create code, but the code exists.  Address=%s", address));
      }
      if (!Objects.equals(expectedCode, existingCode)) {
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

  private Map<Hash, BonsaiValue<UInt256>> maybeCreateStorageMap(
      final Map<Hash, BonsaiValue<UInt256>> storageMap, final Address address) {
    if (storageMap == null) {
      final Map<Hash, BonsaiValue<UInt256>> newMap = new HashMap<>();
      storageToUpdate.put(address, newMap);
      return newMap;
    } else {
      return storageMap;
    }
  }

  private void rollStorageChange(
      final Address address,
      final Hash slotHash,
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
    final Map<Hash, BonsaiValue<UInt256>> storageMap = storageToUpdate.get(address);
    BonsaiValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(slotHash);
    if (slotValue == null) {
      final Optional<UInt256> storageValue =
          wrappedWorldView().getStorageValueBySlotHash(address, slotHash);
      if (storageValue.isPresent()) {
        slotValue = new BonsaiValue<>(storageValue.get(), storageValue.get());
        storageToUpdate.computeIfAbsent(address, k -> new HashMap<>()).put(slotHash, slotValue);
      }
    }
    if (slotValue == null) {
      if ((expectedValue == null || expectedValue.isZero()) && replacementValue != null) {
        maybeCreateStorageMap(storageMap, address)
            .put(slotHash, new BonsaiValue<>(null, replacementValue));
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected to update storage value, but the slot does not exist. Account=%s SlotHash=%s",
                address, slotHash));
      }
    } else {
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if ((expectedValue == null || expectedValue.isZero())
          && existingSlotValue != null
          && !existingSlotValue.isZero()) {
        throw new IllegalStateException(
            String.format(
                "Expected to create slot, but the slot exists. Account=%s SlotHash=%s expectedValue=%s existingValue=%s",
                address, slotHash, expectedValue, existingSlotValue));
      }
      if (!isSlotEquals(expectedValue, existingSlotValue)) {
        throw new IllegalStateException(
            String.format(
                "Old value of slot does not match expected value. Account=%s SlotHash=%s Expected=%s Actual=%s",
                address,
                slotHash,
                expectedValue == null ? "null" : expectedValue.toShortHexString(),
                existingSlotValue == null ? "null" : existingSlotValue.toShortHexString()));
      }
      if (replacementValue == null && slotValue.getPrior() == null) {
        final Map<Hash, BonsaiValue<UInt256>> thisStorageUpdate =
            maybeCreateStorageMap(storageMap, address);
        thisStorageUpdate.remove(slotHash);
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

  @Override
  public void reset() {
    storageToClear.clear();
    storageToUpdate.clear();
    codeToUpdate.clear();
    accountsToUpdate.clear();
    super.reset();
  }
}
