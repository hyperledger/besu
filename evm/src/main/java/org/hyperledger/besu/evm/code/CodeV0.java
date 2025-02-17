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
import org.hyperledger.besu.evm.code.bytecode.Bytecode;
import org.hyperledger.besu.evm.code.bytecode.FullBytecode;
import org.hyperledger.besu.evm.internal.Words;

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/** The CodeV0. */
public class CodeV0 implements Code {

  /** The constant EMPTY_CODE. */
  public static final CodeV0 EMPTY_CODE = new CodeV0(FullBytecode.EMPTY);

  /** The bytes representing the code. */
  private final Bytecode bytes;

  /** The hash of the code, needed for accessing metadata about the bytecode */
  private final Supplier<Hash> codeHash;

  /** Used to cache valid jump destinations. */
  private final JumpDestinationChecker jumpDestinationChecker;

  /** Code section info for the legacy code */
  private final CodeSection codeSectionZero;

  /**
   * Public constructor.
   *
   * @param bytes The byte representation of the code.
   */
  CodeV0(final Bytecode bytes) {
    this.bytes = bytes;
    this.jumpDestinationChecker = new JumpDestinationChecker(bytes);
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
  public Bytecode getBytes() {
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
    return jumpDestinationChecker.isJumpDestInvalid(jumpDestination);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  public JumpDestinationChecker getJumpDestinationChecker() {
    return jumpDestinationChecker;
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

  @Override
  public int readBigEndianI16(final int index) {
    return Words.readBigEndianI16(index, bytes.getRawByteArray());
  }

  @Override
  public int readBigEndianU16(final int index) {
    return Words.readBigEndianU16(index, bytes.getRawByteArray());
  }

  @Override
  public int readU8(final int index) {
    return bytes.getRawByteArray().get(index) & 0xff;
  }

  @Override
  public String prettyPrint() {
    return bytes.toHexString();
  }
}
