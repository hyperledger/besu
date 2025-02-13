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

public class SlicedBytecode extends Bytecode {

  private final Bytecode parent;
  private final int sliceOffset;
  private final int sliceLength;

  public SlicedBytecode(final Bytecode parent, final int offset, final int length) {
    // Validate range within parent
    if (offset < 0 || length < 0 || offset + length > parent.size()) {
      throw new IndexOutOfBoundsException("Slice out of parent bounds");
    }
    this.parent = parent;
    this.sliceOffset = offset;
    this.sliceLength = length;
  }

  @Override
  public RawByteArray getRawByteArray() {
    return new RawByteArray(parent.getRawByteArray().get(sliceOffset, sliceLength));
  }

  @Override
  public int size() {
    return sliceLength;
  }

  @Override
  public byte get(final int index) {
    if (index < 0 || index >= sliceLength) {
      throw new IndexOutOfBoundsException("Index out of slice range");
    }
    // Delegate to the parent, adding the slice offset
    return parent.get(sliceOffset + index);
  }

  @Override
  public Bytecode slice(final int offset, final int length) {
    // Just compose the offset again relative to the parent
    // offset is relative to this sliced view
    return new SlicedBytecode(parent, sliceOffset + offset, length);
  }
}
