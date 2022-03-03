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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.PushOperation;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.RestrictedApi;
import org.apache.tuweni.bytes.Bytes;

/** Represents EVM code associated with an account. */
public class Code {

  public static final Code EMPTY_CODE = new Code(Bytes.EMPTY, Hash.EMPTY);

  /** The bytes representing the code. */
  private final Bytes bytes;

  /** The hash of the code, needed for accessing metadata about the bytecode */
  private final Hash codeHash;

  /** Used to cache valid jump destinations. */
  long[] validJumpDestinations;

  /** Syntactic sugar for an empty contract */
  public static Code EMPTY = new Code(Bytes.EMPTY, Hash.EMPTY);

  /**
   * Public constructor.
   *
   * @param bytes The byte representation of the code.
   * @param codeHash the Hash of the bytes in the code.
   */
  protected Code(final Bytes bytes, final Hash codeHash) {
    this.bytes = bytes;
    this.codeHash = codeHash;
  }

  @RestrictedApi(
      explanation = "To be used for testing purpose only",
      link = "",
      allowedOnPath = ".*/src/test/.*")
  public static Code createLegacyCode(final Bytes bytes, final Hash codeHash) {
    return new Code(bytes, codeHash);
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

  public long[] calculateJumpDests() {
    final int size = getSize();
    final long[] bitmap = new long[(size >> 6) + 1];
    final byte[] rawCode = getBytes().toArrayUnsafe();
    final int length = rawCode.length;
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      final int entryPos = i >> 6;
      final int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3F;
      for (; j < max; i++, j++) {
        final byte operationNum = rawCode[i];
        if (operationNum == JumpDestOperation.OPCODE) {
          thisEntry |= 1L << j;
        } else if (operationNum > PushOperation.PUSH_BASE) {
          // not needed - && operationNum <= PushOperation.PUSH_MAX
          // Java quirk, all bytes are signed, and PUSH32 is 127, which is Byte.MAX_VALUE
          // so we don't need to check the upper bound as it will never be violated
          final int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
          j += multiByteDataLen;
          i += multiByteDataLen;
        }
      }
      bitmap[entryPos] = thisEntry;
    }
    this.validJumpDestinations = bitmap;
    return bitmap;
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

  public boolean isJumpDestInvalid(final int jumpDestination) {
    if (jumpDestination < 0 || jumpDestination >= getSize()) {
      return true;
    }
    if (validJumpDestinations == null || validJumpDestinations.length == 0) {
      validJumpDestinations = calculateJumpDests();
    }

    final long targetLong = validJumpDestinations[jumpDestination >>> 6];
    final long targetBit = 1L << (jumpDestination & 0x3F);
    return (targetLong & targetBit) == 0L;
  }
}
