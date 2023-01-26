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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiAccount implements MutableAccount, EvmAccount {
  private final BonsaiWorldView context;
  private final boolean mutable;

  private final Address address;
  private final Hash addressHash;
  private Hash codeHash;
  private long nonce;
  private Wei balance;
  private Hash storageRoot;
  private Bytes code;

  private final Map<UInt256, UInt256> updatedStorage = new HashMap<>();

  BonsaiAccount(
      final BonsaiWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final boolean mutable) {
    this.context = context;
    this.address = address;
    this.addressHash = addressHash;
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.codeHash = codeHash;

    this.mutable = mutable;
  }

  BonsaiAccount(
      final BonsaiWorldView context,
      final Address address,
      final StateTrieAccountValue stateTrieAccount,
      final boolean mutable) {
    this(
        context,
        address,
        Hash.hash(address),
        stateTrieAccount.getNonce(),
        stateTrieAccount.getBalance(),
        stateTrieAccount.getStorageRoot(),
        stateTrieAccount.getCodeHash(),
        mutable);
  }

  BonsaiAccount(final BonsaiAccount toCopy) {
    this(toCopy, toCopy.context, false);
  }

  BonsaiAccount(final BonsaiAccount toCopy, final BonsaiWorldView context, final boolean mutable) {
    this.context = context;
    this.address = toCopy.address;
    this.addressHash = toCopy.addressHash;
    this.nonce = toCopy.nonce;
    this.balance = toCopy.balance;
    this.storageRoot = toCopy.storageRoot;
    this.codeHash = toCopy.codeHash;
    this.code = toCopy.code;
    updatedStorage.putAll(toCopy.updatedStorage);

    this.mutable = mutable;
  }

  BonsaiAccount(final BonsaiWorldView context, final UpdateTrackingAccount<BonsaiAccount> tracked) {
    this.context = context;
    this.address = tracked.getAddress();
    this.addressHash = tracked.getAddressHash();
    this.nonce = tracked.getNonce();
    this.balance = tracked.getBalance();
    this.storageRoot = Hash.EMPTY_TRIE_HASH;
    this.codeHash = tracked.getCodeHash();
    this.code = tracked.getCode();
    updatedStorage.putAll(tracked.getUpdatedStorage());

    this.mutable = true;
  }

  static BonsaiAccount fromRLP(
      final BonsaiWorldView context,
      final Address address,
      final Bytes encoded,
      final boolean mutable)
      throws RLPException {
    final RLPInput in = RLP.input(encoded);
    in.enterList();

    final long nonce = in.readLongScalar();
    final Wei balance = Wei.of(in.readUInt256Scalar());
    final Hash storageRoot = Hash.wrap(in.readBytes32());
    final Hash codeHash = Hash.wrap(in.readBytes32());

    in.leaveList();

    return new BonsaiAccount(
        context, address, Hash.hash(address), nonce, balance, storageRoot, codeHash, mutable);
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
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
    }
    nonce = value;
  }

  @Override
  public Wei getBalance() {
    return balance;
  }

  @Override
  public void setBalance(final Wei value) {
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
    }
    balance = value;
  }

  @Override
  public Bytes getCode() {
    if (code == null) {
      code = context.getCode(address).orElse(Bytes.EMPTY);
    }
    return code;
  }

  @Override
  public void setCode(final Bytes code) {
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
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

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries does not currently support enumerating storage");
  }

  Bytes serializeAccount() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();

    out.writeLongScalar(nonce);
    out.writeUInt256Scalar(balance);
    out.writeBytes(storageRoot);
    out.writeBytes(codeHash);

    out.endList();
    return out.encoded();
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
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
  public MutableAccount getMutable() throws ModificationNotAllowedException {
    if (mutable) {
      return this;
    } else {
      throw new ModificationNotAllowedException();
    }
  }

  public Hash getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(final Hash storageRoot) {
    if (!mutable) {
      throw new UnsupportedOperationException("Account is immutable");
    }
    this.storageRoot = storageRoot;
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
        + ", storageRoot="
        + storageRoot
        + ", codeHash="
        + codeHash
        + '}';
  }

  /**
   * Throws an exception if the two accounts represent different stored states
   *
   * @param source The bonsai account to compare
   * @param account The State Trie account to compare
   * @param context a description to be added to the thrown exceptions
   * @throws IllegalStateException if the stored values differ
   */
  static void assertCloseEnoughForDiffing(
      final BonsaiAccount source, final StateTrieAccountValue account, final String context) {
    if (source == null) {
      throw new IllegalStateException(context + ": source is null but target isn't");
    } else {
      if (source.nonce != account.getNonce()) {
        throw new IllegalStateException(context + ": nonces differ");
      }
      if (!Objects.equals(source.balance, account.getBalance())) {
        throw new IllegalStateException(context + ": balances differ");
      }
      if (!Objects.equals(source.storageRoot, account.getStorageRoot())) {
        throw new IllegalStateException(context + ": Storage Roots differ");
      }
    }
  }
}
