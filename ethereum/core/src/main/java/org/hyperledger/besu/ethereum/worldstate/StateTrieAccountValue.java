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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/** Represents the raw values associated with an account in the world state trie. */
public class StateTrieAccountValue {
  // Have the indicator above the maximum likely nonce value.
  // Note that for RLP encoding, the value must be positive.
  private static final long CONTAINS_CROSSCHAIN_EXTENDED_STATE = 0x4000000000000000L;
  private static final long CONTAINS_CROSSCHAIN_EXTENDED_STATE_MASK = 0x3FFFFFFFFFFFFFFFL;
  // When the lockability bit is set, it indicates that the contract is lockable.
  private static final long LOCKABLE_BIT_FLAG = 0x01;
  private static final long VERSION_PRESENT_FLAG = 0x02;

  private final long nonce;
  private final Wei balance;
  private boolean lockable;
  private final Hash storageRoot;
  private final Hash codeHash;
  private final int version;

  private StateTrieAccountValue(
      final long nonce,
      final Wei balance,
      final boolean lockable,
      final Hash storageRoot,
      final Hash codeHash) {
    this(nonce, balance, lockable, storageRoot, codeHash, Account.DEFAULT_VERSION);
  }

  public StateTrieAccountValue(
      final long nonce,
      final Wei balance,
      final boolean lockable,
      final Hash storageRoot,
      final Hash codeHash,
      final int version) {
    this.nonce = nonce;
    this.balance = balance;
    this.lockable = lockable;
    this.storageRoot = storageRoot;
    this.codeHash = codeHash;
    this.version = version;
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
   * Indicates whether this contract can be locked.
   *
   * @return true if the contract can be locked.
   */
  public boolean isLockable() {
    return this.lockable;
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

  /**
   * The version of the EVM bytecode associated with this account.
   *
   * @return the version of the account code.
   */
  public int getVersion() {
    return version;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    boolean useCrosschainExtendedState = false;
    boolean writeVersion = version != Account.DEFAULT_VERSION;
    long flags = 0;
    if (this.lockable) {
      flags |= LOCKABLE_BIT_FLAG;
    }
    if (flags != 0) {
      useCrosschainExtendedState = true;
    }

    long nonceField = this.nonce;
    if (useCrosschainExtendedState) {
      nonceField |= CONTAINS_CROSSCHAIN_EXTENDED_STATE;
    }

    out.writeLongScalar(nonceField);
    out.writeUInt256Scalar(balance);
    out.writeBytesValue(storageRoot);
    out.writeBytesValue(codeHash);

    if (!useCrosschainExtendedState) {
      // MainNet Compatible.
      if (writeVersion) {
        // version of zero is never written out.
        out.writeIntScalar(version);
      }
    } else {
      if (writeVersion) {
        flags |= VERSION_PRESENT_FLAG;
      }
      out.writeLongScalar(flags);
      if (writeVersion) {
        // version of zero is never written out.
        out.writeIntScalar(version);
      }
    }
    out.endList();
  }

  public static StateTrieAccountValue readFrom(final RLPInput in) {
    in.enterList();

    long nonce = in.readLongScalar();
    final Wei balance = in.readUInt256Scalar(Wei::wrap);
    final Hash storageRoot = Hash.wrap(in.readBytes32());
    final Hash codeHash = Hash.wrap(in.readBytes32());
    int version = Account.DEFAULT_VERSION;

    boolean extendedState =
        (nonce & CONTAINS_CROSSCHAIN_EXTENDED_STATE) == CONTAINS_CROSSCHAIN_EXTENDED_STATE;

    boolean isLockable = false;
    if (!extendedState) {
      // MainNet compatible state read.
      if (!in.isEndOfCurrentList()) {
        version = in.readIntScalar();
      }
    } else {
      // Extended state read.
      // Remove the extended state indicator.
      nonce &= CONTAINS_CROSSCHAIN_EXTENDED_STATE_MASK;
      // Only read in the flags if they exist. By default, all flags are "false".
      if (!in.isEndOfCurrentList()) {
        final long flags = in.readLongScalar();
        isLockable = (flags & LOCKABLE_BIT_FLAG) == LOCKABLE_BIT_FLAG;
        boolean versionExists = (flags & VERSION_PRESENT_FLAG) == VERSION_PRESENT_FLAG;
        if (versionExists) {
          version = in.readIntScalar();
        }
      }
    }
    in.leaveList();

    return new StateTrieAccountValue(nonce, balance, isLockable, storageRoot, codeHash, version);
  }
}
