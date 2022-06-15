/*
 * Copyright Hyperledger Besu contributors.
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
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiSnapshotAccount implements MutableAccount, EvmAccount {
  private final Address address;
  private long nonce;
  private Wei balance;
  private Optional<Bytes> code;
  private final Map<Bytes32, Optional<BonsaiValue<UInt256>>> mutatedStorage = new HashMap<>();
  private Boolean hasMutated = false;

  private final Function<Address, Optional<Bytes>> codeFetcher;
  private final BiFunction<Address, UInt256, Optional<BonsaiValue<UInt256>>> storageSlotFetcher;

  public BonsaiSnapshotAccount(
    final Address address,
    final long nonce,
    final Wei balance,
    final Function<Address, Optional<Bytes>> codeFetcher,
    final BiFunction<Address, UInt256, Optional<BonsaiValue<UInt256>>> storageFetcher) {
    this.address = address;
    this.nonce = nonce;
    this.balance = balance;
    this.codeFetcher = codeFetcher;
    this.storageSlotFetcher = storageFetcher;
  }

  public BonsaiSnapshotAccount(
    final Address address,
    final long nonce,
    final Wei balance,
    final Function<Address, Optional<Bytes>> codeFetcher,
    final BiFunction<Address, UInt256, Optional<BonsaiValue<UInt256>>> storageFetcher,
    final boolean hasMutated) {
    this.address = address;
    this.nonce = nonce;
    this.balance = balance;
    this.codeFetcher = codeFetcher;
    this.storageSlotFetcher = storageFetcher;
    this.hasMutated = hasMutated;
  }

  public boolean hasMutated() {
    return hasMutated;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Hash getAddressHash() {
    return Hash.hash(address);
  }

  @Override
  public long getNonce() {
    return nonce;
  }

  @Override
  public Wei getBalance() {
    return balance;
  }

  @Override
  public Bytes getCode() {
    if (code == null) {
      code = codeFetcher.apply(address);
    }
    return code.orElse(Bytes.EMPTY);
  }

  @Override
  public Hash getCodeHash() {
    return Hash.hash(getCode());
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    mutatedStorage.computeIfAbsent(key, k -> storageSlotFetcher.apply(address, key));

    return mutatedStorage.get(key).map(BonsaiValue::getUpdated).orElse(null);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    mutatedStorage.computeIfAbsent(key, k -> storageSlotFetcher.apply(address, key));

    return mutatedStorage.get(key).map(BonsaiValue::getPrior).orElse(null);
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException(
        "Bonsai Snapshot Account does not currently support enumerating storage");
  }

  @Override
  public void setNonce(final long nonce) {
    this.nonce = nonce;
  }

  @Override
  public void setBalance(final Wei balance) {
    this.balance = balance;
  }

  @Override
  public void setCode(final Bytes code) {
    this.code = Optional.of(code);
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    mutatedStorage.put(
        key,
        Optional.ofNullable(mutatedStorage.get(key))
            .orElseGet(
                () -> Optional.of(new BonsaiValue<UInt256>(null, null, false).setUpdated(value))));
  }

  @Override
  public void clearStorage() {
    // TODO: this is wrong, we need to clear all values, not just the ones we have mutated.
    //
    mutatedStorage.forEach((k, v) -> v.ifPresent(val -> val.setUpdated(null)));
  }

  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    return mutatedStorage.entrySet().stream()
        .filter(e -> e.getValue().isPresent())
        .filter(e -> !e.getValue().get().isUnchanged())
        .map(
            entry ->
                new AbstractMap.SimpleEntry<>(
                    UInt256.fromBytes(entry.getKey()), entry.getValue().get().getUpdated()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public MutableAccount getMutable() throws ModificationNotAllowedException {
    return this;
  }
}
