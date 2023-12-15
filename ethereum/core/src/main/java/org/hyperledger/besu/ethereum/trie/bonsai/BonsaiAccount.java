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

package org.hyperledger.besu.ethereum.trie.bonsai;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldView;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiAccount implements MutableAccount, AccountValue {
  private final BonsaiWorldView context;
  private boolean immutable;

  private final Address address;
  private final Hash addressHash;
  private Hash codeHash;
  private long nonce;
  private Wei balance;
  private Hash storageRoot;
  private Bytes code;

  private final Map<UInt256, UInt256> updatedStorage = new HashMap<>();

  public BonsaiAccount(
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

    this.immutable = !mutable;
  }

  public BonsaiAccount(
      final BonsaiWorldView context,
      final Address address,
      final AccountValue stateTrieAccount,
      final boolean mutable) {
    this(
        context,
        address,
        address.addressHash(),
        stateTrieAccount.getNonce(),
        stateTrieAccount.getBalance(),
        stateTrieAccount.getStorageRoot(),
        stateTrieAccount.getCodeHash(),
        mutable);
  }

  public BonsaiAccount(final BonsaiAccount toCopy) {
    this(toCopy, toCopy.context, false);
  }

  public BonsaiAccount(
      final BonsaiAccount toCopy, final BonsaiWorldView context, final boolean mutable) {
    this.context = context;
    this.address = toCopy.address;
    this.addressHash = toCopy.addressHash;
    this.nonce = toCopy.nonce;
    this.balance = toCopy.balance;
    this.storageRoot = toCopy.storageRoot;
    this.codeHash = toCopy.codeHash;
    this.code = toCopy.code;
    updatedStorage.putAll(toCopy.updatedStorage);

    this.immutable = !mutable;
  }

  public BonsaiAccount(
      final BonsaiWorldView context, final UpdateTrackingAccount<BonsaiAccount> tracked) {
    this.context = context;
    this.address = tracked.getAddress();
    this.addressHash = tracked.getAddressHash();
    this.nonce = tracked.getNonce();
    this.balance = tracked.getBalance();
    this.storageRoot = Hash.EMPTY_TRIE_HASH;
    this.codeHash = tracked.getCodeHash();
    this.code = tracked.getCode();
    updatedStorage.putAll(tracked.getUpdatedStorage());

    this.immutable = false;
  }

  public static BonsaiAccount fromRLP(
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
        context, address, address.addressHash(), nonce, balance, storageRoot, codeHash, mutable);
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

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    return context.getWorldStateStorage().storageEntriesFrom(this.addressHash, startKeyHash, limit);
  }

  public Bytes serializeAccount() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    writeTo(out);
    return out.encoded();
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeLongScalar(nonce);
    out.writeUInt256Scalar(balance);
    out.writeBytes(storageRoot);
    out.writeBytes(codeHash);

    out.endList();
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
  public Hash getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(final Hash storageRoot) {
    if (immutable) {
      throw new ModificationNotAllowedException();
    }
    this.storageRoot = storageRoot;
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
  public static void assertCloseEnoughForDiffing(
      final BonsaiAccount source, final AccountValue account, final String context) {
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
