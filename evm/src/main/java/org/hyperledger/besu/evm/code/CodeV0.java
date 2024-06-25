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
package org.hyperledger.besu.evm.code;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.JumpDestOperation;

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/** The CodeV0. */
public class CodeV0 implements Code {

  /** The constant EMPTY_CODE. */
  public static final CodeV0 EMPTY_CODE = new CodeV0(Bytes.EMPTY);

  /** The bytes representing the code. */
  private final Bytes bytes;

  /** The hash of the code, needed for accessing metadata about the bytecode */
  private final Supplier<Hash> codeHash;

  /** Used to cache valid jump destinations. */
  private long[] validJumpDestinations;

  /** Code section info for the legacy code */
  private final CodeSection codeSectionZero;

  /**
   * Public constructor.
   *
   * @param bytes The byte representation of the code.
   */
  CodeV0(final Bytes bytes) {
    this.bytes = bytes;
    this.codeHash = Suppliers.memoize(() -> Hash.hash(bytes));
    this.codeSectionZero = new CodeSection(bytes.size(), 0, -1, -1, 0);
  }

  /**
   * Returns true if the object is equal to this; otherwise false.
   *
   * @param other The object to compare this with.
   * @return True if the object is equal to this, otherwise false.
   */
  @Override
  public boolean equals(final Object other) {
    if (other == null) return false;
    if (other == this) return true;
    if (!(other instanceof CodeV0 that)) return false;

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
  @Override
  public int getSize() {
    return bytes.size();
  }

  @Override
  public int getDataSize() {
    return 0;
  }

  @Override
  public int getDeclaredDataSize() {
    return 0;
  }

  @Override
  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bytes", bytes).toString();
  }

  @Override
  public Hash getCodeHash() {
    return codeHash.get();
  }

  @Override
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

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public CodeSection getCodeSection(final int section) {
    if (section == 0) {
      return codeSectionZero;
    } else {
      return null;
    }
  }

  @Override
  public int getCodeSectionCount() {
    return 1;
  }

  @Override
  public int getEofVersion() {
    return 0;
  }

  @Override
  public int getSubcontainerCount() {
    return 0;
  }

  @Override
  public Optional<Code> getSubContainer(final int index, final Bytes auxData, final EVM evm) {
    return Optional.empty();
  }

  @Override
  public Bytes getData(final int offset, final int length) {
    return Bytes.EMPTY;
  }

  /**
   * Calculate jump destination.
   *
   * @return the long [ ]
   */
  long[] calculateJumpDests() {
    final int size = getSize();
    final long[] bitmap = new long[(size >> 6) + 1];
    final byte[] rawCode = bytes.toArrayUnsafe();
    final int length = rawCode.length;
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      final int entryPos = i >> 6;
      final int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3F;
      for (; j < max; i++, j++) {
        final byte operationNum = rawCode[i];
        if (operationNum >= JumpDestOperation.OPCODE) {
          switch (operationNum) {
            case JumpDestOperation.OPCODE:
              thisEntry |= 1L << j;
              break;
            case 0x60:
              i += 1;
              j += 1;
              break;
            case 0x61:
              i += 2;
              j += 2;
              break;
            case 0x62:
              i += 3;
              j += 3;
              break;
            case 0x63:
              i += 4;
              j += 4;
              break;
            case 0x64:
              i += 5;
              j += 5;
              break;
            case 0x65:
              i += 6;
              j += 6;
              break;
            case 0x66:
              i += 7;
              j += 7;
              break;
            case 0x67:
              i += 8;
              j += 8;
              break;
            case 0x68:
              i += 9;
              j += 9;
              break;
            case 0x69:
              i += 10;
              j += 10;
              break;
            case 0x6a:
              i += 11;
              j += 11;
              break;
            case 0x6b:
              i += 12;
              j += 12;
              break;
            case 0x6c:
              i += 13;
              j += 13;
              break;
            case 0x6d:
              i += 14;
              j += 14;
              break;
            case 0x6e:
              i += 15;
              j += 15;
              break;
            case 0x6f:
              i += 16;
              j += 16;
              break;
            case 0x70:
              i += 17;
              j += 17;
              break;
            case 0x71:
              i += 18;
              j += 18;
              break;
            case 0x72:
              i += 19;
              j += 19;
              break;
            case 0x73:
              i += 20;
              j += 20;
              break;
            case 0x74:
              i += 21;
              j += 21;
              break;
            case 0x75:
              i += 22;
              j += 22;
              break;
            case 0x76:
              i += 23;
              j += 23;
              break;
            case 0x77:
              i += 24;
              j += 24;
              break;
            case 0x78:
              i += 25;
              j += 25;
              break;
            case 0x79:
              i += 26;
              j += 26;
              break;
            case 0x7a:
              i += 27;
              j += 27;
              break;
            case 0x7b:
              i += 28;
              j += 28;
              break;
            case 0x7c:
              i += 29;
              j += 29;
              break;
            case 0x7d:
              i += 30;
              j += 30;
              break;
            case 0x7e:
              i += 31;
              j += 31;
              break;
            case 0x7f:
              i += 32;
              j += 32;
              break;
            default:
          }
        }
      }
      bitmap[entryPos] = thisEntry;
    }
    return bitmap;
  }

  @Override
  public int readBigEndianI16(final int index) {
    return Words.readBigEndianI16(index, bytes.toArrayUnsafe());
  }

  @Override
  public int readBigEndianU16(final int index) {
    return Words.readBigEndianU16(index, bytes.toArrayUnsafe());
  }

  @Override
  public int readU8(final int index) {
    return bytes.toArrayUnsafe()[index] & 0xff;
  }

  @Override
  public String prettyPrint() {
    return bytes.toHexString();
  }
}
