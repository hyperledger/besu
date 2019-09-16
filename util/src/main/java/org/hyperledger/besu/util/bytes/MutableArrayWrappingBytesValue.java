/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.Arrays;

/** An implementation of {@link MutableBytesValue} backed by a byte array ({@code byte[]}). */
class MutableArrayWrappingBytesValue extends ArrayWrappingBytesValue implements MutableBytesValue {

  MutableArrayWrappingBytesValue(final byte[] bytes) {
    super(bytes);
  }

  MutableArrayWrappingBytesValue(final byte[] bytes, final int offset, final int length) {
    super(bytes, offset, length);
  }

  @Override
  public void set(final int i, final byte b) {
    // Check bounds because while the array access would throw, the error message would be confusing
    // for the caller.
    checkElementIndex(i, size());
    this.bytes[offset + i] = b;
  }

  @Override
  public MutableBytesValue mutableSlice(final int i, final int length) {
    if (i == 0 && length == size()) return this;
    if (length == 0) return MutableBytesValue.EMPTY;

    checkElementIndex(i, size());
    checkArgument(
        i + length <= size(),
        "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
        length,
        size(),
        size() - i,
        i);
    return length == Bytes32.SIZE
        ? new MutableArrayWrappingBytes32(bytes, offset + i)
        : new MutableArrayWrappingBytesValue(bytes, offset + i, length);
  }

  @Override
  public void fill(final byte b) {
    Arrays.fill(bytes, offset, offset + length, b);
  }

  @Override
  public BytesValue copy() {
    // We *must* override this method because ArrayWrappingBytesValue assumes that it is the case.
    return new ArrayWrappingBytesValue(arrayCopy());
  }
}
