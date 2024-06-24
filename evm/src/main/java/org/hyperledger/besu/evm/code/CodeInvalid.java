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

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/**
 * For code versions where code can be deemed "invalid" this represents a cacheable instance of
 * invalid code. Note that EXTCODE operations can still access invalid code.
 */
public class CodeInvalid implements Code {

  private final Supplier<Hash> codeHash;
  private final Bytes codeBytes;

  private final String invalidReason;

  /**
   * Instantiates a new Code invalid.
   *
   * @param codeBytes the code bytes
   * @param invalidReason the invalid reason
   */
  public CodeInvalid(final Bytes codeBytes, final String invalidReason) {
    this.codeBytes = codeBytes;
    this.codeHash = Suppliers.memoize(() -> Hash.hash(codeBytes));
    this.invalidReason = invalidReason;
  }

  /**
   * Gets invalid reason.
   *
   * @return the invalid reason
   */
  public String getInvalidReason() {
    return invalidReason;
  }

  @Override
  public int getSize() {
    return codeBytes.size();
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
    return codeBytes;
  }

  @Override
  public Hash getCodeHash() {
    return codeHash.get();
  }

  @Override
  public boolean isJumpDestInvalid(final int jumpDestination) {
    return false;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public CodeSection getCodeSection(final int section) {
    return null;
  }

  @Override
  public int getCodeSectionCount() {
    return 0;
  }

  @Override
  public int getEofVersion() {
    return Integer.MAX_VALUE;
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
    return Words.readBigEndianI16(index, codeBytes.toArrayUnsafe());
  }

  @Override
  public int readBigEndianU16(final int index) {
    return Words.readBigEndianU16(index, codeBytes.toArrayUnsafe());
  }

  @Override
  public int readU8(final int index) {
    return codeBytes.toArrayUnsafe()[index] & 0xff;
  }

  @Override
  public String prettyPrint() {
    return codeBytes.toHexString();
  }
}
