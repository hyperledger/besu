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
package org.hyperledger.besu.evm.account;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** Wraps an EOA account and includes delegated code to be run on behalf of it. */
public class MutableCodeDelegationDelegationAccount extends AbstractCodeDelegationAccount
    implements MutableAccount {

  private final MutableAccount wrappedAccount;

  /**
   * Creates a new MutableCodeDelegationDelegationAccount.
   *
   * @param worldUpdater the world updater.
   * @param wrappedAccount the account that has delegated code to be loaded into it.
   * @param codeDelegationAddress the address of the delegated code.
   * @param gasCalculator the gas calculator to check for precompiles.
   */
  public MutableCodeDelegationDelegationAccount(
      final WorldUpdater worldUpdater,
      final MutableAccount wrappedAccount,
      final Address codeDelegationAddress,
      final GasCalculator gasCalculator) {
    super(worldUpdater, codeDelegationAddress, gasCalculator);
    this.wrappedAccount = wrappedAccount;
  }

  @Override
  public Address getAddress() {
    return wrappedAccount.getAddress();
  }

  @Override
  public boolean isStorageEmpty() {
    return wrappedAccount.isStorageEmpty();
  }

  @Override
  public Optional<Address> codeDelegationAddress() {
    return super.codeDelegationAddress();
  }

  @Override
  public Hash getAddressHash() {
    return wrappedAccount.getAddressHash();
  }

  @Override
  public long getNonce() {
    return wrappedAccount.getNonce();
  }

  @Override
  public Wei getBalance() {
    return wrappedAccount.getBalance();
  }

  @Override
  public Bytes getCode() {
    return wrappedAccount.getCode();
  }

  @Override
  public Hash getCodeHash() {
    return wrappedAccount.getCodeHash();
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    return wrappedAccount.getStorageValue(key);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    return wrappedAccount.getOriginalStorageValue(key);
  }

  @Override
  public boolean isEmpty() {
    return wrappedAccount.isEmpty();
  }

  @Override
  public boolean hasCode() {
    return !getCode().isEmpty();
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    return wrappedAccount.storageEntriesFrom(startKeyHash, limit);
  }

  @Override
  public void setNonce(final long value) {
    wrappedAccount.setNonce(value);
  }

  @Override
  public void setBalance(final Wei value) {
    wrappedAccount.setBalance(value);
  }

  @Override
  public void setCode(final Bytes code) {
    wrappedAccount.setCode(code);
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    wrappedAccount.setStorageValue(key, value);
  }

  @Override
  public void clearStorage() {
    wrappedAccount.clearStorage();
  }

  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    return wrappedAccount.getUpdatedStorage();
  }

  @Override
  public void becomeImmutable() {
    wrappedAccount.becomeImmutable();
  }
}
