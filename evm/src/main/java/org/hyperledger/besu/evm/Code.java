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
package org.hyperledger.besu.evm;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.contract.JumpDestCache;

import java.util.BitSet;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import static org.apache.logging.log4j.LogManager.getLogger;

/** Represents EVM code associated with an account. */
public class Code {

  /** The bytes representing the code. */
  private final Bytes bytes;

  /** The hash of the code, needed for accessing metadata about the bytecode */
  private final Hash codeHash;

  /** Used to cache valid jump destinations. */
  //  private BitSet validJumpDestinations;
  long[] validJumpDestinations;

  /** Syntactic sugar for an empty contract */
  public static Code EMPTY = new Code(Bytes.EMPTY, Hash.EMPTY);

  private static final Logger LOG = getLogger();

  /**
   * Public constructor.
   *
   * @param bytes The byte representation of the code.
   */
  public Code(final Bytes bytes, final Hash codeHash) {
    this.bytes = bytes;
    this.codeHash = codeHash;
  }

  public Code(final Bytes bytecode, final Hash codeHash, final long[] validJumpDestinations) {
    this.bytes = bytecode;
    this.validJumpDestinations = validJumpDestinations;
    this.codeHash = codeHash;
    JumpDestCache.getInstance().put(codeHash, validJumpDestinations);
  }

  public Code() {
    this(Bytes.EMPTY, Hash.EMPTY);
  }

  /**
   * Returns true if the object is equal to this; otherwise false.
   *
   * @param other The object to compare this with.
   * @return True if the object is equal to this; otherwise false.
   */
  @Override
  public boolean equals(final Object other) {
    if (other == null) return false;
    if (other == this) return true;
    if (!(other instanceof Code)) return false;

    final Code that = (Code) other;
    return this.bytes.equals(that.bytes);
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  /**
   * Size of the Code, in bytes
   *
   * @return The number of bytes in the code.
   */
  public int getSize() {
    return bytes.size();
  }

  /**
   * Determine whether a specified destination is a valid jump target.
   *
   * @param evm the EVM executing this code
   * @param destination The destination we're checking for validity.
   * @return Whether or not this location is a valid jump destination.
   */
  public boolean isValidJumpDestination(
      final EVM evm,  final UInt256 destination) {
    if (!destination.fitsInt()) return false;

    final int jumpDestination = destination.intValue();
    if (jumpDestination >= getSize()) return false;

    if (validJumpDestinations == null) {
      validJumpDestinations = JumpDestCache.getInstance().getIfPresent(this.codeHash);
      if (validJumpDestinations == null) {
        validJumpDestinations = calculateJumpDests();
        if (this.codeHash != null && !this.codeHash.equals(Hash.EMPTY)) {
          JumpDestCache.getInstance().put(this.codeHash, validJumpDestinations);
        } else {
          LOG.debug("not caching jumpdest for unhashed contract code");
        }
      }

    }
    long targetLong = validJumpDestinations[jumpDestination >> 6];
    long targetBit = 1L << (jumpDestination & 0x3F);
    return (targetLong & targetBit) != 0L;
  }

  private long[] calculateJumpDests() {
    // Calculate valid jump destinations
    int size = getSize();
    long [] validJumps = new long[(size >> 6) + 1];
    byte[] rawCode = getBytes().toArrayUnsafe();
    int length = rawCode.length;
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      int entryPos = i >> 6;
      int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3F;
      for (; j < max; i++, j++) {
        byte operationNum = rawCode[i];
        if (operationNum == JumpDestOperation.OPCODE) {
          thisEntry |= 1L << j;
        } else if (operationNum > PushOperation.PUSH_BASE) {
          // not needed - && operationNum <= PushOperation.PUSH_MAX
          // Java quirk, all bytes are signed, and PUSH32 is 127, which is Byte.MAX_VALUE
          // so we don't need to check the upper bound as it will never be violated
          int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
          j += multiByteDataLen;
          i += multiByteDataLen;
        }
      }
      validJumps[entryPos] = thisEntry;
    }
    return validJumps;
  }
  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bytes", bytes).toString();
  }

  public Hash getCodeHash() {
    return codeHash;
  }

  public long[] getValidJumpDestinations() {
    return validJumpDestinations;
  }
}
