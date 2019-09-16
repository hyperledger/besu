/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Map;
import java.util.NavigableMap;

public class ReadOnlyMutableAccount implements MutableAccount {

  private MutableAccount mutableAccount;

  public ReadOnlyMutableAccount(final MutableAccount publicAccount) {
    this.mutableAccount = publicAccount;
  }

  @Override
  public void setNonce(final long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBalance(final Wei value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCode(final BytesValue code) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVersion(final int version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearStorage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    return mutableAccount.getUpdatedStorage();
  }

  @Override
  public Address getAddress() {
    return mutableAccount.getAddress();
  }

  @Override
  public Hash getAddressHash() {
    return mutableAccount.getAddressHash();
  }

  @Override
  public long getNonce() {
    return mutableAccount.getNonce();
  }

  @Override
  public Wei getBalance() {
    return mutableAccount.getBalance();
  }

  @Override
  public BytesValue getCode() {
    return mutableAccount.getCode();
  }

  @Override
  public Hash getCodeHash() {
    return mutableAccount.getCodeHash();
  }

  @Override
  public int getVersion() {
    return mutableAccount.getVersion();
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    return mutableAccount.getStorageValue(key);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    return mutableAccount.getOriginalStorageValue(key);
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    throw new UnsupportedOperationException();
  }
}
