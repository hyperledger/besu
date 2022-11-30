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

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class CodeV1 implements Code {
  private final Hash codeHash;
  private final Bytes container;
  private final Bytes code;

  private final long[] validJumpDestinations;

  CodeV1(final Hash codeHash, final EOFLayout layout, final long[] validJumpDestinations) {
    this.codeHash = codeHash;
    this.container = layout.getContainer();
    this.code = layout.getSections()[EOFLayout.SECTION_CODE];
    this.validJumpDestinations = validJumpDestinations;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final CodeV1 codeV1 = (CodeV1) o;
    return codeHash.equals(codeV1.codeHash)
        && container.equals(codeV1.container)
        && code.equals(codeV1.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(codeHash, container, code);
  }

  @Override
  public int getSize() {
    return container.size();
  }

  @Override
  public Bytes getCodeBytes() {
    return code;
  }

  @Override
  public Bytes getContainerBytes() {
    return container;
  }

  @Override
  public Hash getCodeHash() {
    return codeHash;
  }

  @Override
  public boolean isJumpDestInvalid(final int jumpDestination) {
    if (jumpDestination < 0 || jumpDestination >= getSize()) {
      return true;
    }
    if (validJumpDestinations == null || validJumpDestinations.length == 0) {
      return true;
    }

    final long targetLong = validJumpDestinations[jumpDestination >>> 6];
    final long targetBit = 1L << (jumpDestination & 0x3F);
    return (targetLong & targetBit) == 0L;
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
