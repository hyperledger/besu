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

package org.hyperledger.besu.ethereum.bonsai.trielog;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * This class encapsulates the changes that are done to transition one block to the next. This
 * includes serialization and deserialization tasks for storing this log to off-memory storage.
 *
 * <p>In this particular formulation only the "Leaves" are tracked" Future layers may track patrica
 * trie changes as well.
 */
public class TrieLogLayer {

  protected Hash blockHash;

  Map<Address, BonsaiValue<StateTrieAccountValue>> getAccounts() {
    return accounts;
  }

  Map<Address, BonsaiValue<Bytes>> getCode() {
    return code;
  }

  Map<Address, Map<StorageSlotKey, BonsaiValue<UInt256>>> getStorage() {
    return storage;
  }

  protected final Map<Address, BonsaiValue<StateTrieAccountValue>> accounts;
  protected final Map<Address, BonsaiValue<Bytes>> code;
  protected final Map<Address, Map<StorageSlotKey, BonsaiValue<UInt256>>> storage;
  protected boolean frozen = false;

  public TrieLogLayer() {
    // TODO when tuweni fixes zero length byte comparison consider TreeMap
    this.accounts = new HashMap<>();
    this.code = new HashMap<>();
    this.storage = new HashMap<>();
  }

  /** Locks the layer so no new changes can be added; */
  void freeze() {
    frozen = true; // The code never bothered me anyway
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public TrieLogLayer setBlockHash(final Hash blockHash) {
    checkState(!frozen, "Layer is Frozen");
    this.blockHash = blockHash;
    return this;
  }

  public TrieLogLayer addAccountChange(
      final Address address,
      final StateTrieAccountValue oldValue,
      final StateTrieAccountValue newValue) {
    checkState(!frozen, "Layer is Frozen");
    accounts.put(address, new BonsaiValue<>(oldValue, newValue));
    return this;
  }

  public TrieLogLayer addCodeChange(
      final Address address, final Bytes oldValue, final Bytes newValue, final Hash blockHash) {
    checkState(!frozen, "Layer is Frozen");
    code.put(address, new BonsaiValue<>(oldValue, newValue, newValue == null));
    return this;
  }

  public TrieLogLayer addStorageChange(
      final Address address,
      final StorageSlotKey slot,
      final UInt256 oldValue,
      final UInt256 newValue) {
    checkState(!frozen, "Layer is Frozen");
    storage
        .computeIfAbsent(address, a -> new TreeMap<>())
        .put(slot, new BonsaiValue<>(oldValue, newValue));
    return this;
  }

  public Stream<Map.Entry<Address, BonsaiValue<StateTrieAccountValue>>> streamAccountChanges() {
    return accounts.entrySet().stream();
  }

  public Stream<Map.Entry<Address, BonsaiValue<Bytes>>> streamCodeChanges() {
    return code.entrySet().stream();
  }

  public Stream<Map.Entry<Address, Map<StorageSlotKey, BonsaiValue<UInt256>>>>
      streamStorageChanges() {
    return storage.entrySet().stream();
  }

  public boolean hasStorageChanges(final Address address) {
    return storage.containsKey(address);
  }

  public Stream<Map.Entry<StorageSlotKey, BonsaiValue<UInt256>>> streamStorageChanges(
      final Address address) {
    return storage.getOrDefault(address, Map.of()).entrySet().stream();
  }

  public Optional<Bytes> getPriorCode(final Address address) {
    return Optional.ofNullable(code.get(address)).map(BonsaiValue::getPrior);
  }

  public Optional<Bytes> getCode(final Address address) {
    return Optional.ofNullable(code.get(address)).map(BonsaiValue::getUpdated);
  }

  Optional<UInt256> getPriorStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storage.get(address))
        .map(i -> i.get(storageSlotKey))
        .map(BonsaiValue::getPrior);
  }

  Optional<UInt256> getStorageByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    return Optional.ofNullable(storage.get(address))
        .map(i -> i.get(storageSlotKey))
        .map(BonsaiValue::getUpdated);
  }

  public Optional<StateTrieAccountValue> getPriorAccount(final Address address) {
    return Optional.ofNullable(accounts.get(address)).map(BonsaiValue::getPrior);
  }

  public Optional<StateTrieAccountValue> getAccount(final Address address) {
    return Optional.ofNullable(accounts.get(address)).map(BonsaiValue::getUpdated);
  }

  public String dump() {
    final StringBuilder sb = new StringBuilder();
    sb.append("TrieLogLayer{" + "blockHash=").append(blockHash).append(frozen).append('}');
    sb.append("accounts\n");
    for (final Map.Entry<Address, BonsaiValue<StateTrieAccountValue>> account :
        accounts.entrySet()) {
      sb.append(" : ").append(account.getKey()).append("\n");
      if (Objects.equals(account.getValue().getPrior(), account.getValue().getUpdated())) {
        sb.append("   = ").append(account.getValue().getUpdated()).append("\n");
      } else {
        sb.append("   - ").append(account.getValue().getPrior()).append("\n");
        sb.append("   + ").append(account.getValue().getUpdated()).append("\n");
      }
    }
    sb.append("code").append("\n");
    for (final Map.Entry<Address, BonsaiValue<Bytes>> code : code.entrySet()) {
      sb.append(" : ").append(code.getKey()).append("\n");
      if (Objects.equals(code.getValue().getPrior(), code.getValue().getUpdated())) {
        sb.append("   = ").append(code.getValue().getPrior()).append("\n");
      } else {
        sb.append("   - ").append(code.getValue().getPrior()).append("\n");
        sb.append("   + ").append(code.getValue().getUpdated()).append("\n");
      }
    }
    sb.append("Storage").append("\n");
    for (final Map.Entry<Address, Map<StorageSlotKey, BonsaiValue<UInt256>>> storage :
        storage.entrySet()) {
      sb.append(" : ").append(storage.getKey()).append("\n");
      for (final Map.Entry<StorageSlotKey, BonsaiValue<UInt256>> slot :
          storage.getValue().entrySet()) {
        final UInt256 originalValue = slot.getValue().getPrior();
        final UInt256 updatedValue = slot.getValue().getUpdated();
        sb.append("   : ").append(slot.getKey()).append("\n");
        if (Objects.equals(originalValue, updatedValue)) {
          sb.append("     = ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
        } else {
          sb.append("     - ")
              .append((originalValue == null) ? "null" : originalValue.toShortHexString())
              .append("\n");
          sb.append("     + ")
              .append((updatedValue == null) ? "null" : updatedValue.toShortHexString())
              .append("\n");
        }
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TrieLogLayer that = (TrieLogLayer) o;
    return new EqualsBuilder()
        .append(frozen, that.frozen)
        .append(blockHash, that.blockHash)
        .append(accounts, that.accounts)
        .append(code, that.code)
        .append(storage, that.storage)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(blockHash)
        .append(frozen)
        .append(accounts)
        .append(code)
        .append(storage)
        .toHashCode();
  }
}
