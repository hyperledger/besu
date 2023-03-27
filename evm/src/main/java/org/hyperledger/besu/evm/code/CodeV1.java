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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/** The CodeV1. */
public class CodeV1 implements Code {

  private final Supplier<Hash> codeHash;
  EOFLayout eofLayout;

  /**
   * Instantiates a new CodeV1.
   *
   * @param layout the layout
   */
  CodeV1(final EOFLayout layout) {
    this.eofLayout = layout;
    this.codeHash = Suppliers.memoize(() -> Hash.hash(eofLayout.container()));
  }

  @Override
  public int getSize() {
    return eofLayout.container().size();
  }

  @Override
  public CodeSection getCodeSection(final int section) {
    checkArgument(section >= 0, "Section number is positive");
    checkArgument(section < eofLayout.getCodeSectionCount(), "Section index is valid");
    return eofLayout.getCodeSection(section);
  }

  @Override
  public int getCodeSectionCount() {
    return eofLayout.getCodeSectionCount();
  }

  @Override
  public Bytes getBytes() {
    return eofLayout.container();
  }

  @Override
  public Hash getCodeHash() {
    return codeHash.get();
  }

  @Override
  public boolean isJumpDestInvalid(final int jumpDestination) {
    return true; // code validation ensures this
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public int getEofVersion() {
    return eofLayout.version();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final CodeV1 codeV1 = (CodeV1) o;
    return codeHash.equals(codeV1.codeHash) && Objects.equals(eofLayout, codeV1.eofLayout);
  }

  @Override
  public int hashCode() {
    return Objects.hash(codeHash, eofLayout);
  }
}
