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
 */
package org.hyperledger.besu.ethereum.worldstate;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes32;

/** Represents the raw values associated with an account in the world state trie. */
public class StateTrieAccountValue {

  private final long nonce;
  private final Wei balance;
  private final Hash storageRoot;
  private final Hash codeHash;

  public StateTrieAccountValue(
      final long nonce, final Wei balance, final Hash storageRoot, final Hash codeHash) {
    checkNotNull(balance, "balance cannot be null");
    checkNotNull(storageRoot, "storageRoot cannot be null");
    checkNotNull(codeHash, "codeHash cannot be null");
    this.nonce = nonce;
    this.balance = balance;
    this.storageRoot = storageRoot;
    this.codeHash = codeHash;
  }

  /**
   * The account nonce, that is the number of transactions sent from that account.
   *
   * @return the account nonce.
   */
  public long getNonce() {
    return nonce;
  }

  /**
   * The available balance of that account.
   *
   * @return the balance, in Wei, of the account.
   */
  public Wei getBalance() {
    return balance;
  }

  /**
   * The hash of the root of the storage trie associated with this account.
   *
   * @return the hash of the root node of the storage trie.
   */
  public Hash getStorageRoot() {
    return storageRoot;
  }

  /**
   * The hash of the EVM bytecode associated with this account.
   *
   * @return the hash of the account code (which may be {@link Hash#EMPTY}).
   */
  public Hash getCodeHash() {
    return codeHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final StateTrieAccountValue that = (StateTrieAccountValue) o;
    return nonce == that.nonce
        && balance.equals(that.balance)
        && storageRoot.equals(that.storageRoot)
        && codeHash.equals(that.codeHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nonce, balance, storageRoot, codeHash);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeLongScalar(nonce);
    out.writeUInt256Scalar(balance);
    out.writeBytes(storageRoot);
    out.writeBytes(codeHash);

    out.endList();
  }

  public static StateTrieAccountValue readFrom(final RLPInput in) {
    in.enterList();

    final long nonce = in.readLongScalar();
    final Wei balance = Wei.of(in.readUInt256Scalar());
    Bytes32 storageRoot;
    Bytes32 codeHash;
    if (in.nextIsNull()) {
      storageRoot = Hash.EMPTY_TRIE_HASH;
      in.skipNext();
    } else {
      storageRoot = in.readBytes32();
    }
    if (in.nextIsNull()) {
      codeHash = Hash.EMPTY;
      in.skipNext();
    } else {
      codeHash = in.readBytes32();
    }

    in.leaveList();

    return new StateTrieAccountValue(nonce, balance, Hash.wrap(storageRoot), Hash.wrap(codeHash));
  }
}
