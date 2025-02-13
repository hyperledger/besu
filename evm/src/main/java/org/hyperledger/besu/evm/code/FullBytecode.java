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

import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a full bytecode. This class provides methods for accessing the bytecode, slicing it,
 * and converting it from various formats.
 */
public class FullBytecode extends Bytecode {

  /** Constant representing an empty bytecode . */
  public static final FullBytecode EMPTY = new FullBytecode(Bytes.EMPTY);

  private final Bytes bytes;

  /**
   * Constructs a FullBytecode object from Bytes .
   *
   * @param bytes the Bytes object containing the bytecode data
   */
  public FullBytecode(final Bytes bytes) {
    this.bytes = bytes;
  }

  /**
   * Constructs a FullBytecode from a byte array.
   *
   * @param bytes the byte array containing the bytecode data
   */
  public FullBytecode(final byte[] bytes) {
    this.bytes = Bytes.wrap(bytes);
  }

  /**
   * Returns a RawByteArray representation of the bytecode.
   *
   * @return the RawByteArray representation of the bytecode
   */
  @Override
  public RawByteArray getRawByteArray() {
    return new RawByteArray(bytes.toArrayUnsafe());
  }

  /**
   * Returns the size of the bytecode.
   *
   * @return the size of the bytecode
   */
  @Override
  public int size() {
    return bytes.size();
  }

  /**
   * Returns the byte at the specified index in the bytecode.
   *
   * @param i the index of the byte to retrieve
   * @return the byte at the specified index
   * @throws IndexOutOfBoundsException if the index is out of bounds
   */
  @Override
  public byte get(final int i) {
    return bytes.get(i);
  }

  /**
   * Creates a copy of the FullBytecode object.
   *
   * @return a new FullBytecode object with the same bytecode data
   */
  @Override
  public Bytecode copy() {
    return new FullBytecode(bytes.copy());
  }

  /**
   * Slices the bytecode from a specified index and length.
   *
   * @param i the starting index of the slice
   * @param length the length of the slice
   * @return a new SlicedBytecode object representing the sliced portion
   */
  @Override
  public Bytecode slice(final int i, final int length) {
    return new SlicedBytecode(this, i, length);
  }

  /**
   * Converts a hex string representation of bytecode into a FullBytecode object.
   *
   * @param hex the hex string representing the bytecode
   * @return a FullBytecode object containing the bytecode represented by the hex string
   */
  public static Bytecode fromHexString(final String hex) {
    return new FullBytecode(Bytes.fromHexString(hex));
  }

  /**
   * Creates a FullBytecode object from a sequence of integers, where each integer represents a
   * byte.
   *
   * @param bytes the sequence of integers representing the bytecode
   * @return a FullBytecode object containing the bytecode
   */
  public static Bytecode of(final int... bytes) {
    return new FullBytecode(Bytes.of(bytes));
  }

  /**
   * Creates a FullBytecode object from a sequence of bytes.
   *
   * @param bytes the sequence of bytes representing the bytecode
   * @return a FullBytecode object containing the bytecode
   */
  public static Bytecode of(final byte... bytes) {
    return new FullBytecode(Bytes.of(bytes));
  }

  /**
   * Wraps an existing byte array into a FullBytecode object.
   *
   * @param bytes the byte array to wrap
   * @return a FullBytecode object containing the bytecode
   */
  public static Bytecode wrap(final byte[] bytes) {
    return new FullBytecode(bytes);
  }

  /**
   * Wraps an existing Bytes object into a FullBytecode object.
   *
   * @param bytes the Bytes object to wrap
   * @return a FullBytecode object containing the bytecode
   */
  public static Bytecode wrap(final Bytes bytes) {
    return new FullBytecode(bytes);
  }
}
