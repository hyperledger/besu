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
package org.hyperledger.besu.ethereum.trie.diffbased.common;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public abstract class DiffBasedAccount implements MutableAccount, AccountValue {
  protected final DiffBasedWorldView context;
  protected boolean immutable;
  protected final Address address;
  protected final Hash addressHash;
  protected Hash codeHash;
  protected long nonce;
  protected Wei balance;
  protected Bytes code;

  protected final Map<UInt256, UInt256> updatedStorage = new HashMap<>();

  /**
   * Constructs a new DiffBasedAccount instance without the account's code. This constructor is used
   * when the account's code is not required or will not be read from the database. It initializes
   * the account with its context, address, address hash, nonce, balance, code hash, and mutability
   * status.
   *
   * @param context The DiffBasedWorldView context in which this account exists.
   * @param address The Ethereum address of this account.
   * @param addressHash The hash of the account's address.
   * @param nonce The nonce of the account, representing the number of transactions sent from this
   *     account.
   * @param balance The balance of the account in Wei.
   * @param codeHash The hash of the account's code.
   * @param mutable A boolean indicating if the account is mutable. If false, the account is
   *     considered immutable.
   */
  public DiffBasedAccount(
      final DiffBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash codeHash,
      final boolean mutable) {
    this.context = context;
    this.address = address;
    this.addressHash = addressHash;
    this.nonce = nonce;
    this.balance = balance;
    this.codeHash = codeHash;

    this.immutable = !mutable;
  }

  /**
   * Constructs a new DiffBasedAccount instance with the account's code. This constructor is used
   * when all account information, including its code, are available. It initializes the account
   * with its context, address, address hash, nonce, balance, code hash, the actual code, and
   * mutability status.
   *
   * @param context The DiffBasedWorldView context in which this account exists.
   * @param address The Ethereum address of this account.
   * @param addressHash The hash of the account's address.
   * @param nonce The nonce of the account, representing the number of transactions sent from this
   *     account.
   * @param balance The balance of the account in Wei.
   * @param codeHash The hash of the account's code.
   * @param code The actual bytecode of the account's smart contract. This is provided when the code
   *     is known and needs to be associated with the account.
   * @param mutable A boolean indicating if the account is mutable. If false, the account is
   *     considered immutable.
   */
  public DiffBasedAccount(
      final DiffBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash codeHash,
      final Bytes code,
      final boolean mutable) {
    this.context = context;
    this.address = address;
    this.addressHash = addressHash;
    this.nonce = nonce;
    this.balance = balance;
    this.codeHash = codeHash;
    this.code = code;
    this.immutable = !mutable;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Hash getAddressHash() {
    return addressHash;
  }

  @Override
  public long getNonce() {
    return nonce;
  }

  @Override
  public void setNonce(final long value) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }
    nonce = value;
  }

  @Override
  public Wei getBalance() {
    return balance;
  }

  @Override
  public void setBalance(final Wei value) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }
    balance = value;
  }

  @Override
  public Bytes getCode() {
    if (code == null) {
      code = context.getCode(address, codeHash).orElse(Bytes.EMPTY);
    }
    return code;
  }

  @Override
  public void setCode(final Bytes code) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }
    this.code = code;
    if (code == null || code.isEmpty()) {
      this.codeHash = Hash.EMPTY;
    } else {
      this.codeHash = Hash.hash(code);
    }
  }

  @Override
  public Hash getCodeHash() {
    return codeHash;
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    return context.getStorageValue(address, key);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    return context.getPriorStorageValue(address, key);
  }

  public Bytes serializeAccount() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    writeTo(out);
    return out.encoded();
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }
    updatedStorage.put(key, value);
  }

  @Override
  public void clearStorage() {
    updatedStorage.clear();
  }

  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    return updatedStorage;
  }

  @Override
  public void becomeImmutable() {
    immutable = true;
  }

  @Override
  public String toString() {
    return "AccountState{"
        + "address="
        + address
        + ", nonce="
        + nonce
        + ", balance="
        + balance
        + ", codeHash="
        + codeHash
        + '}';
  }
}
