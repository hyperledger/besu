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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

  private final Supplier<Integer> sizeSupplier;

  /** The hash of the code, needed for accessing metadata about the bytecode */
  private final Supplier<Hash> codeHashSupplier;

  /** Used to cache valid jump destinations. */
  private long[] validJumpDestinationsBitMask;

  /** Code section info for the legacy code */
  private final CodeSection codeSectionZero;

  /**
   * Public constructor.
   *
   * @param byteCode The byte representation of the code.
   */
  public CodeV0(final Bytes byteCode) {
    this(
        byteCode,
        byteCode.isEmpty() ? () -> Hash.EMPTY : Suppliers.memoize(() -> Hash.hash(byteCode)));
  }

  /**
   * Public constructor.
   *
   * @param byteCode The byte representation of the code.
   * @param codeHashSupplier the hash of the bytecode
   */
  public CodeV0(final Bytes byteCode, final Supplier<Hash> codeHashSupplier) {
    this.bytes = byteCode;
    this.sizeSupplier = Suppliers.memoize(byteCode::size);
    this.codeHashSupplier = codeHashSupplier;
    this.codeSectionZero = new CodeSection(this.sizeSupplier, 0, -1, -1, 0);
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

    return this.codeHashSupplier.get().equals(that.codeHashSupplier.get());
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
    return sizeSupplier.get();
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
    return codeHashSupplier.get();
  }

  @Override
  public boolean isJumpDestInvalid(final int jumpDestination) {
    if (jumpDestination < 0 || jumpDestination >= getSize()) {
      return true;
    }

    if (validJumpDestinationsBitMask == null) {
      validJumpDestinationsBitMask = calculateJumpDestBitMask();
    }

    // This selects which long in the array holds the bit for the given offset:
    //	1)	>>> 6 is equivalent to jumpDestination / 64
    //	2)	Each long holds 64 bits, so this finds the correct chunk
    final long targetLong = validJumpDestinationsBitMask[jumpDestination >>> 6];

    // 1) & 0x3F is jumpDestination % 64
    // 2)	1L << ... gives a mask for the specific bit in that long
    final long targetBit = 1L << (jumpDestination & 0x3F);

    // If the bit is not set, then it is an invalid jump destination
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
  long[] calculateJumpDestBitMask() {
    final byte[] code = bytes.toArrayUnsafe();
    final int length = code.length;

    // Allocate enough longs: one bit per byte, 64 bits per long
    final long[] bitmap = new long[(length + 63) >>> 6];

    for (int i = 0; i < length; ) {
      final int byteIndex = i;
      final int unsignedOpcode = code[i] & 0xFF;

      // JUMPDEST opcode is 0x5B
      if (unsignedOpcode == (byte) 0x5B) {
        int wordIndex = byteIndex >>> 6; // index into long[] bitmap
        int bitIndex = byteIndex & 0x3F; // bit position within the long
        bitmap[wordIndex] |= 1L << bitIndex; // mark this offset as JUMPDEST
        i += 1;
      } else if (unsignedOpcode >= 0x60 && unsignedOpcode <= 0x7F) {
        // PUSH1 (0x60) to PUSH32 (0x7F) — skip over immediate bytes
        int pushDataLength = unsignedOpcode - 0x5F; // PUSHn pushes n bytes
        i += 1 + pushDataLength;
      } else {
        // Other instructions: just advance 1 byte
        i += 1;
      }
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
    int i = 0;
    int len = bytes.size();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(out);
    ps.println("0x # Legacy EVM Code");
    while (i < len) {
      i += printInstruction(i, ps);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /**
   * Prints an individual instruction, including immediate data
   *
   * @param offset Offset within the code
   * @param out the print stream to write to
   * @return the number of bytes to advance the PC (includes consideration of immediate arguments)
   */
  public int printInstruction(final int offset, final PrintStream out) {
    int codeByte = bytes.get(offset) & 0xff;
    OpcodeInfo info = OpcodeInfo.getLegacyOpcode(codeByte);
    String push = "";
    String decimalPush = "";
    if (info.pcAdvance() > 1) {
      int start = Math.min(bytes.size(), offset + 1);
      int end = Math.min(bytes.size(), info.pcAdvance() - 1);
      Bytes slice = bytes.slice(start, end);
      push = slice.toUnprefixedHexString();
      if (info.pcAdvance() < 5) {
        decimalPush = "(" + slice.toLong() + ")";
      }
    }
    String name = info.name();
    if (codeByte == 0x5b) {
      name = "JUMPDEST";
    }
    out.printf("%02x%s # [ %d ] %s%s%n", codeByte, push, offset, name, decimalPush);
    return Math.max(1, info.pcAdvance());
  }
}
