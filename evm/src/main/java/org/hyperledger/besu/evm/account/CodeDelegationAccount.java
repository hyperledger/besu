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

import java.util.NavigableMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** Wraps an EOA account and includes delegated code to be run on behalf of it. */
public class CodeDelegationAccount implements Account {

  private final Account wrappedAccount;
  private final Bytes targetCode;
  private final Address targetAddress;
  private Hash targetCodeHash;

  /**
   * Creates a new CodeDelegationAccount.
   *
   * @param wrappedAccount the account that has delegated code to be loaded into it.
   * @param targetAddress the address of the target of the code delegation.
   * @param targetCode the code of the target of the code delegation.
   */
  public CodeDelegationAccount(
      final Account wrappedAccount, final Address targetAddress, final Bytes targetCode) {
    this.wrappedAccount = wrappedAccount;
    this.targetCode = targetCode;
    this.targetAddress = targetAddress;
  }

  /**
   * Returns the target address of the delegated code.
   *
   * @return the target address
   */
  public Address getTargetAddress() {
    return targetAddress;
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
    return targetCode;
  }

  @Override
  public Hash getCodeHash() {
    if (targetCodeHash == null) {
      targetCodeHash = Hash.hash(targetCode);
    }

    return targetCodeHash;
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
}
