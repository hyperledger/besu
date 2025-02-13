/*
 * Copyright contributors to Besu.
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

import java.util.Arrays;

import org.apache.tuweni.bytes.AbstractBytes;
import org.apache.tuweni.bytes.MutableBytes;

public abstract class Bytecode extends AbstractBytes {

  @Override
  public Bytecode slice(final int i) {
    if (i == 0) {
      return this;
    } else {
      int size = this.size();
      return i >= size ? FullBytecode.EMPTY : this.slice(i, size - i);
    }
  }

  @Override
  public abstract Bytecode slice(int i, int length);

  public abstract RawByteArray getRawByteArray();

  @Override
  public Bytecode copy() {
    throw new UnsupportedOperationException("cannot create a copy of bytecode");
  }

  @Override
  public MutableBytes mutableCopy() {
    throw new UnsupportedOperationException("cannot create a mutable copy of bytecode");
  }

  public static class RawByteArray {
    byte[] rawBytecode;

    public RawByteArray(final byte[] rawBytecode) {
      this.rawBytecode = rawBytecode;
    }

    public byte get(final int offset) {
      return rawBytecode[offset];
    }

    public byte[] get(final int offset, final int length) {
      return get(offset, length, length);
    }

    public byte[] get(final int offset, final int length, final int arraySize) {
      var bytecodeLocal = new byte[arraySize];
      System.arraycopy(rawBytecode, offset, bytecodeLocal, 0, length);
      return bytecodeLocal;
    }

    public int size() {
      return rawBytecode.length;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      RawByteArray that = (RawByteArray) o;
      return Arrays.equals(rawBytecode, that.rawBytecode);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(rawBytecode);
    }
  }
}
