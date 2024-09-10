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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.internal.Words;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

/** The CodeV1. */
public class CodeV1 implements Code {

  private final Supplier<Hash> codeHash;
  EOFLayout eofLayout;

  /**
   * Instantiates a new CodeV1.
   *
   * @param eofLayout the layout
   */
  CodeV1(final EOFLayout eofLayout) {
    this.eofLayout = eofLayout;
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
  public int getSubcontainerCount() {
    return eofLayout.getSubcontainerCount();
  }

  @Override
  public Optional<Code> getSubContainer(final int index, final Bytes auxData, final EVM evm) {
    EOFLayout subcontainerLayout = eofLayout.getSubcontainer(index);
    Bytes codeToLoad;
    if (auxData != null && !auxData.isEmpty()) {
      codeToLoad = subcontainerLayout.writeContainer(auxData);
      if (codeToLoad == null) {
        return Optional.empty();
      }
    } else {
      // if no auxdata is added, we must validate data is not truncated separately
      if (subcontainerLayout.dataLength() != subcontainerLayout.data().size()) {
        return Optional.empty();
      }
      codeToLoad = subcontainerLayout.container();
    }

    Code subContainerCode = evm.getCodeUncached(codeToLoad);

    return subContainerCode.isValid() && subContainerCode.getEofVersion() > 0
        ? Optional.of(subContainerCode)
        : Optional.empty();
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

  @Override
  public Bytes getData(final int offset, final int length) {
    Bytes data = eofLayout.data();
    int dataLen = data.size();
    if (offset > dataLen) {
      return Bytes.EMPTY;
    } else if ((offset + length) > dataLen) {
      byte[] result = new byte[length];
      MutableBytes mbytes = MutableBytes.wrap(result);
      data.slice(offset).copyTo(mbytes, 0);
      return Bytes.wrap(result);
    } else {
      return data.slice(offset, length);
    }
  }

  @Override
  public int getDataSize() {
    return eofLayout.data().size();
  }

  @Override
  public int getDeclaredDataSize() {
    return eofLayout.dataLength();
  }

  @Override
  public int readBigEndianI16(final int index) {
    return Words.readBigEndianI16(index, eofLayout.container().toArrayUnsafe());
  }

  @Override
  public int readBigEndianU16(final int index) {
    return Words.readBigEndianU16(index, eofLayout.container().toArrayUnsafe());
  }

  @Override
  public int readU8(final int index) {
    return eofLayout.container().toArrayUnsafe()[index] & 0xff;
  }

  @Override
  public String prettyPrint() {
    StringWriter sw = new StringWriter();
    eofLayout.prettyPrint(new PrintWriter(sw, true), "", "");
    return sw.toString();
  }

  /**
   * The EOFLayout object for the code
   *
   * @return the EOFLayout object for the parsed code
   */
  public EOFLayout getEofLayout() {
    return eofLayout;
  }
}
