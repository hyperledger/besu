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
package org.hyperledger.besu.ethereum.trie.pathbased.common;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.internal.CodeCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public abstract class PathBasedAccount implements MutableAccount, AccountValue {
  protected final PathBasedWorldView context;
  protected boolean immutable;
  protected final Address address;
  protected final Hash addressHash;
  protected Hash codeHash;
  protected long nonce;
  protected Wei balance;
  protected Code code;
  protected final CodeCache codeCache;

  protected final Map<UInt256, UInt256> updatedStorage = new HashMap<>();

  /**
   * Constructs a new PathBasedAccount instance without the account's code. This constructor is used
   * when the account's code is not required or will not be read from the database. It initializes
   * the account with its context, address, address hash, nonce, balance, code hash, and mutability
   * status.
   *
   * @param context The PathBasedWorldView context in which this account exists.
   * @param address The Ethereum address of this account.
   * @param addressHash The hash of the account's address.
   * @param nonce The nonce of the account, representing the number of transactions sent from this
   *     account.
   * @param balance The balance of the account in Wei.
   * @param codeHash The hash of the account's code.
   * @param mutable A boolean indicating if the account is mutable. If false, the account is
   *     considered immutable.
   * @param codeCache The global cache used to store and retrieve the account's code.
   */
  public PathBasedAccount(
      final PathBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash codeHash,
      final boolean mutable,
      final CodeCache codeCache) {
    this.context = context;
    this.address = address;
    this.addressHash = addressHash;
    this.nonce = nonce;
    this.balance = balance;
    this.codeHash = codeHash;
    this.codeCache = codeCache;

    this.immutable = !mutable;

    if (codeHash.equals(Hash.EMPTY)) {
      this.code = CodeV0.EMPTY_CODE;
    }
  }

  /**
   * Constructs a new PathBasedAccount instance with the account's code. This constructor is used
   * when all account information, including its code, are available. It initializes the account
   * with its context, address, address hash, nonce, balance, code hash, the actual code, and
   * mutability status.
   *
   * @param context The PathBasedWorldView context in which this account exists.
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
  public PathBasedAccount(
      final PathBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash codeHash,
      final Code code,
      final boolean mutable,
      final CodeCache codeCache) {
    this.context = context;
    this.address = address;
    this.addressHash = addressHash;
    this.nonce = nonce;
    this.balance = balance;
    this.codeHash = codeHash;
    this.code = code;
    this.immutable = !mutable;
    this.codeCache = codeCache;
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
    if (code != null) {
      // code is cached in the object, but not in the cache
      Optional.ofNullable(codeCache).ifPresent(c -> c.put(codeHash, code));

      return code.getBytes();
    }

    // try to get the code from the cache
    final Code cachedCode =
        Optional.ofNullable(codeCache).map(c -> c.getIfPresent(codeHash)).orElse(null);
    if (cachedCode != null) {
      code = cachedCode;
      return code.getBytes();
    }

    // cache miss: get the code from the disk and put it in the cache
    final Bytes byteCode = context.getCode(address, codeHash).orElse(Bytes.EMPTY);
    code = new CodeV0(byteCode);
    Optional.ofNullable(codeCache).ifPresent(c -> c.put(codeHash, code));

    return byteCode;
  }

  @Override
  public Code getAnalyzedCode() {
    final Code cachedCode =
        Optional.ofNullable(codeCache).map(c -> c.getIfPresent(codeHash)).orElse(null);
    if (cachedCode != null) {
      if (code == null) {
        code = cachedCode; // cache hit, set the code if it was not set before
      }

      return cachedCode;
    }

    // if code is already set, put it in the cache and return it
    if (code != null) {
      Optional.ofNullable(codeCache).ifPresent(c -> c.put(codeHash, code));

      return code;
    }

    // cache miss: get the code from the disk and put it in the cache
    final Bytes bytecode = context.getCode(address, codeHash).orElse(Bytes.EMPTY);
    code = new CodeV0(bytecode, codeHash);
    Optional.ofNullable(codeCache).ifPresent(codeCache -> codeCache.put(codeHash, code));

    return code;
  }

  @Override
  public void setCode(final Bytes byteCode) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }

    if (byteCode == null || byteCode.isEmpty()) {
      this.code = CodeV0.EMPTY_CODE;
      this.codeHash = Hash.EMPTY;
      return;
    }

    this.codeHash = Hash.hash(byteCode);
    this.code = Optional.ofNullable(codeCache).map(c -> c.getIfPresent(codeHash)).orElse(null);

    if (this.code == null) {
      this.code = new CodeV0(byteCode, codeHash);
      Optional.ofNullable(codeCache).ifPresent(c -> c.put(codeHash, this.code));
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
