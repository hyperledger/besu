/*
 * Copyright contributors to Hyperledger Besu
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

package org.hyperledger.besu.evm.code;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;

/**
 * For code versions where code can be deemed "invalid" this represents a cachable instance of
 * invalid code. Note that EXTCODE operations can still access invalid code.
 */
public class CodeInvalid implements Code {

  private final Hash codeHash;
  private final Bytes codeBytes;

  private final String invalidReason;

  public CodeInvalid(final Hash codeHash, final Bytes codeBytes, final String invalidReason) {
    this.codeHash = codeHash;
    this.codeBytes = codeBytes;
    this.invalidReason = invalidReason;
  }

  public String getInvalidReason() {
    return invalidReason;
  }

  @Override
  public int getSize() {
    return codeBytes.size();
  }

  @Override
  public Bytes getCodeBytes(final int function) {
    return null;
  }

  @Override
  public Bytes getContainerBytes() {
    return codeBytes;
  }

  @Override
  public Hash getCodeHash() {
    return codeHash;
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
}
