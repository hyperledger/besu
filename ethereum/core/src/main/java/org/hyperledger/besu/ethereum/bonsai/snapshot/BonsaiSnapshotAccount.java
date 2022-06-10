package org.hyperledger.besu.ethereum.bonsai.snapshot;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiSnapshotAccount implements MutableAccount {
  private final Address address;
  private long nonce;
  private Wei balance;
  private Bytes code;
  private final NavigableMap<Bytes32, AccountStorageEntry> storage;
  private final Map<Bytes32, AccountStorageEntry> originalValues = new HashMap<>();

  public BonsaiSnapshotAccount(
      final Address address,
      final long nonce,
      final Wei balance,
      final Bytes code,
      final NavigableMap<Bytes32, AccountStorageEntry> storage) {
    this.address = address;
    this.nonce = nonce;
    this.balance = balance;
    this.code = code;
    this.storage = storage;
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
    return code;
  }

  @Override
  public Hash getCodeHash() {
    return Hash.hash(code);
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    return Optional.ofNullable(storage.get(key)).map(AccountStorageEntry::getValue).orElse(null);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    return Optional.ofNullable(
            Optional.ofNullable(originalValues.get(key)).orElse(storage.get(key)))
        .map(AccountStorageEntry::getValue)
        .orElse(null);
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    return null;
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
    this.code = code;
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    originalValues.computeIfAbsent(key, k -> storage.get(key));
    storage.put(key, AccountStorageEntry.create(value, Hash.hash(key), key));
  }

  @Override
  public void clearStorage() {
    storage.forEach((k, v) -> originalValues.computeIfAbsent(k, __ -> v));
  }

  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    // TODO: check to see if this is the expected implementation, how do we return cleared storage?
    return originalValues.keySet().stream()
        .map(k -> new AbstractMap.SimpleEntry<>(UInt256.fromBytes(k), storage.get(k).getValue()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
