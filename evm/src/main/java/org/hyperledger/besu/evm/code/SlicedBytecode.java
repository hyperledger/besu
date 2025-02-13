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

/**
 * Represents a partial view (slice) of a larger bytecode. The slice is defined by a starting offset
 * and length within the parent bytecode. This class allows accessing and manipulating a specific
 * portion of the bytecode without modifying the parent bytecode.
 */
public class SlicedBytecode extends Bytecode {

  private final Bytecode parent;
  private final int sliceOffset;
  private final int sliceLength;

  /**
   * Constructs a SlicedBytecode object, which represents a portion (slice) of a larger parent
   * bytecode. The slice is defined by an offset and a length within the parent bytecode.
   *
   * @param parent the parent bytecode from which the slice is derived
   * @param offset the starting offset of the slice within the parent bytecode
   * @param length the length of the slice
   * @throws IndexOutOfBoundsException if the slice's offset or length is out of bounds within the
   *     parent bytecode
   */
  public SlicedBytecode(final Bytecode parent, final int offset, final int length) {
    // Validate range within parent
    if (offset < 0 || length < 0 || offset + length > parent.size()) {
      throw new IndexOutOfBoundsException("Slice out of parent bounds");
    }
    this.parent = parent;
    this.sliceOffset = offset;
    this.sliceLength = length;
  }

  /**
   * Returns the raw bytecode of the slice as a RawByteArray, which is a portion of the parent
   * bytecode.
   *
   * @return the raw bytecode of the slice
   */
  @Override
  public RawByteArray getRawByteArray() {
    return new RawByteArray(parent.getRawByteArray().get(sliceOffset, sliceLength));
  }

  /**
   * Returns the size of the sliced portion of bytecode.
   *
   * @return the size of the slice
   */
  @Override
  public int size() {
    return sliceLength;
  }

  /**
   * Returns the byte at the specified index within the slice. The index is relative to the slice,
   * not to the parent bytecode.
   *
   * @param index the index within the slice
   * @return the byte at the specified index
   * @throws IndexOutOfBoundsException if the index is outside the slice's range
   */
  @Override
  public byte get(final int index) {
    if (index < 0 || index >= sliceLength) {
      throw new IndexOutOfBoundsException("Index out of slice range");
    }
    // Delegate to the parent bytecode, adding the slice offset
    return parent.get(sliceOffset + index);
  }

  /**
   * Creates a new copy of the sliced bytecode. The copy is a new instance representing the same
   * slice from the parent bytecode.
   *
   * @return a new SlicedBytecode object that is a copy of the current slice
   */
  @Override
  public Bytecode copy() {
    return new SlicedBytecode(parent.copy(), sliceOffset, sliceLength);
  }

  /**
   * Creates a new slice from the current slice, defined by a new offset and length. The offset is
   * relative to the current sliced view, not the parent bytecode.
   *
   * @param offset the new offset relative to the current slice
   * @param length the length of the new slice
   * @return a new SlicedBytecode object representing the new slice
   */
  @Override
  public Bytecode slice(final int offset, final int length) {
    // Create a new slice with the specified offset and length, relative to the parent bytecode
    return new SlicedBytecode(parent, sliceOffset + offset, length);
  }
}
