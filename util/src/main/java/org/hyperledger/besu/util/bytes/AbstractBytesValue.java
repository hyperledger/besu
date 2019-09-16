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

import java.security.MessageDigest;

/**
 * A bare bone abstract {@link BytesValue} that implements basic methods likely common to all
 * implementations, and default variants of some methods.
 */
public abstract class AbstractBytesValue implements BytesValue {

  private static final char[] hexCode = "0123456789abcdef".toCharArray();

  @Override
  public BytesValue slice(final int index) {
    final int size = size();
    if (index >= size) {
      return EMPTY;
    }
    return slice(index, size - index);
  }

  @Override
  public void copyTo(final MutableBytesValue dest) {
    checkArgument(
        dest.size() == size(),
        "Cannot copy %s bytes to destination of non-equal size %s",
        size(),
        dest.size());

    copyTo(dest, 0);
  }

  @Override
  public void copyTo(final MutableBytesValue destination, final int destinationOffset) {
    // Special casing an empty source or the following checks might throw (even though we have
    // nothing to copy anyway) and this gets inconvenient for generic methods using copyTo() as
    // they may have to special case empty values because of this. As an example,
    // concatenate(EMPTY, EMPTY) would need to be special cased without this.
    if (size() == 0) return;

    checkElementIndex(destinationOffset, destination.size());
    checkArgument(
        destination.size() - destinationOffset >= size(),
        "Cannot copy %s bytes, destination has only %s bytes from index %s",
        size(),
        destination.size() - destinationOffset,
        destinationOffset);

    for (int i = 0; i < size(); i++) destination.set(destinationOffset + i, get(i));
  }

  @Override
  public BytesValue copy() {
    return BytesValue.wrap(extractArray());
  }

  @Override
  public int commonPrefixLength(final BytesValue other) {
    final int ourSize = size();
    final int otherSize = other.size();
    int i = 0;
    while (i < ourSize && i < otherSize && get(i) == other.get(i)) {
      i++;
    }
    return i;
  }

  @Override
  public BytesValue commonPrefix(final BytesValue other) {
    return slice(0, commonPrefixLength(other));
  }

  @Override
  public void update(final MessageDigest digest) {
    for (int i = 0; i < size(); i++) {
      digest.update(get(i));
    }
  }

  @Override
  public boolean isZero() {
    for (int i = 0; i < size(); i++) {
      if (get(i) != 0) return false;
    }
    return true;
  }

  @Override
  public MutableBytesValue mutableCopy() {
    return MutableBytesValue.wrap(extractArray());
  }

  /**
   * Compare this value and the provided one for equality.
   *
   * <p>Two {@link BytesValue} are equal is they have the same time and contain the exact same bytes
   * in order.
   *
   * @param other The other value to test for equality.
   * @return Whether this value and {@code other} are equal.
   */
  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof BytesValue)) return false;

    final BytesValue that = (BytesValue) other;
    if (this.size() != that.size()) return false;

    for (int i = 0; i < size(); i++) {
      if (this.get(i) != that.get(i)) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i = 0; i < size(); i++) result = 31 * result + get(i);
    return result;
  }

  @Override
  public String toString() {
    final int size = size();
    final StringBuilder r = new StringBuilder(2 + size * 2);
    r.append("0x");

    for (int i = 0; i < size; i++) {
      final byte b = get(i);
      r.append(hexCode[b >> 4 & 15]);
      r.append(hexCode[b & 15]);
    }

    return r.toString();
  }
}
