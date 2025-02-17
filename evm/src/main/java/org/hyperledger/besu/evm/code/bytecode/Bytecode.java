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
package org.hyperledger.besu.evm.code.bytecode;

import java.util.Arrays;

import org.apache.tuweni.bytes.AbstractBytes;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * An abstract class representing bytecode . This class provides common methods for working with
 * bytecode, including slicing and copying the bytecode.
 */
public abstract class Bytecode extends AbstractBytes {

  /** Constructs a Bytecode */
  public Bytecode() {}

  /**
   * Creates a sliced version of the bytecode starting at the given index.
   *
   * @param i the index to start slicing from
   * @return a new Bytecode object representing the sliced portion
   */
  @Override
  public Bytecode slice(final int i) {
    if (i == 0) {
      return this;
    } else {
      int size = this.size();
      return i >= size ? FullBytecode.EMPTY : this.slice(i, size - i);
    }
  }

  /**
   * Creates a sliced version of the bytecode starting at the given index and with the specified
   * length.
   *
   * @param i the index to start slicing from
   * @param length the length of the slice
   * @return a new Bytecode object representing the sliced portion
   */
  @Override
  public abstract Bytecode slice(int i, int length);

  /**
   * Returns the raw bytecode as a RawByteArray.
   *
   * @return the raw bytecode as a RawByteArray
   */
  public abstract RawByteArray getRawByteArray();

  /**
   * Creates a copy of the current bytecode.
   *
   * @return a new Bytecode object that is a copy of the current bytecode
   */
  @Override
  public abstract Bytecode copy();

  /**
   * Throws an UnsupportedOperationException since bytecode cannot have a mutable copy.
   *
   * @throws UnsupportedOperationException if called
   */
  @Override
  public MutableBytes mutableCopy() {
    throw new UnsupportedOperationException("cannot create a mutable copy of bytecode");
  }

  /**
   * A helper class that wraps raw bytecode as an array of bytes, providing methods to access raw
   * bytecode.
   */
  public static class RawByteArray {
    private final byte[] rawBytecode;

    /**
     * Constructs a RawByteArray with the given raw bytecode array.
     *
     * @param rawBytecode the byte array representing the raw bytecode
     */
    public RawByteArray(final byte[] rawBytecode) {
      this.rawBytecode = rawBytecode;
    }

    /**
     * Gets the byte at the specified offset.
     *
     * @param offset the offset at which to retrieve the byte
     * @return the byte at the specified offset
     */
    public byte get(final int offset) {
      return rawBytecode[offset];
    }

    /**
     * Gets a subarray of bytes from the specified offset and length.
     *
     * @param offset the starting offset
     * @param length the number of bytes to retrieve
     * @return a byte array containing the requested bytes
     */
    public byte[] get(final int offset, final int length) {
      return get(offset, length, length);
    }

    /**
     * Gets a subarray of bytes from the specified offset and length.
     *
     * @param offset the starting offset
     * @param length the number of bytes to retrieve
     * @param arraySize the size of the array to return
     * @return a byte array containing the requested bytes
     */
    public byte[] get(final int offset, final int length, final int arraySize) {
      var bytecodeLocal = new byte[arraySize];
      System.arraycopy(rawBytecode, offset, bytecodeLocal, 0, length);
      return bytecodeLocal;
    }

    /**
     * Returns the size of the raw bytecode array.
     *
     * @return the size of the raw bytecode
     */
    public int size() {
      return rawBytecode.length;
    }

    /**
     * Checks if two RawByteArray objects are equal by comparing their byte arrays.
     *
     * @param o the object to compare to
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      RawByteArray that = (RawByteArray) o;
      return Arrays.equals(rawBytecode, that.rawBytecode);
    }

    /**
     * Returns the hash code for this RawByteArray.
     *
     * @return the hash code of the raw bytecode array
     */
    @Override
    public int hashCode() {
      return Arrays.hashCode(rawBytecode);
    }
  }
}
