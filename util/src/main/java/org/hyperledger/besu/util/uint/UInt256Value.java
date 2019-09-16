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
package org.hyperledger.besu.util.uint;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.util.bytes.Bytes32Backed;

/**
 * Represents a 256-bits (32 bytes) unsigned integer value.
 *
 * <p>A {@link UInt256Value} is an unsigned integer value stored with 32 bytes, so whose value can
 * range between 0 and 2^256-1. It extends {@link Bytes32Backed} and so the underlying bytes can be
 * accessed through {@link #getBytes()}. It also expose the {@link #asSigned()} method that allow to
 * "cast" the number as signed, interpreting the same underlying bytes in two's complement.
 *
 * <p>This interface is a base for such 256-bits precision value and is meant to be implemented to
 * represent a (potentially large) quantity of a particular unit (it is however strongly advised to
 * extend {@link BaseUInt256Value} rather than reimplement this interface from scratch). Doing so
 * provides type safety in that quantities of different units cannot be mixed accidentally.
 *
 * <p>For non-specific needs of a 256-bits value however, {@link UInt256} should be used, which
 * simply represent a raw 32 bytes unsigned number, not attached to a particular unit (in exactly
 * the same way that {@code long} represents a 8 bytes signed number). Implementations of {@link
 * UInt256Value} other than {@link UInt256} should be thought of as strongly-typed type aliases for
 * {@link UInt256}, and can always be "type-casted" to {@link UInt256} explicitly with {@link
 * #asUInt256()}.
 *
 * @param <T> The concrete type of the value.
 * @see UInt256 for <b>the</b> generic implementation of an integer value with 256-bits precision.
 * @see BaseUInt256Value for a base class to extend in order to implement a {@link UInt256Value}.
 * @see Counter to obtain a mutable variant of a 256-bits integer.
 */
public interface UInt256Value<T extends UInt256Value<T>>
    extends Bytes32Backed, Comparable<T>, Quantity {

  int SIZE = 32;

  /** @return An immutable copy of this value. */
  T copy();

  /** @return True if this is the value 0. */
  default boolean isZero() {
    return getBytes().isZero();
  }

  /**
   * @return True if this value fits a java {@code int} (i.e. is less or equal to {@code
   *     Integer.MAX_VALUE}).
   */
  default boolean fitsInt() {
    return UInt256Bytes.fitsInt(getBytes());
  }

  /**
   * @return This value as a java {@code int} assuming it is small enough to fit an {@code int}.
   * @throws IllegalStateException if the value does not fit an {@code int}, that is if {@code
   *     !fitsInt()}.
   */
  default int toInt() {
    checkState(fitsInt(), "This scalar value does not fit a 4 byte int");
    return getBytes().getInt(SIZE - 4);
  }

  /**
   * @return True if this value fits a java {@code long} (i.e. is less or equal to {@code
   *     Long.MAX_VALUE}).
   */
  default boolean fitsLong() {
    return UInt256Bytes.fitsLong(getBytes());
  }

  /**
   * @return This value as a java {@code long} assuming it is small enough to fit a {@code long}.
   * @throws IllegalStateException if the value does not fit a {@code long}, that is if {@code
   *     !fitsLong()}.
   */
  default long toLong() {
    checkState(fitsLong(), "This scalar value does not fit a 8 byte long");
    return getBytes().getLong(SIZE - 8);
  }

  T plus(T value);

  T plus(long value);

  T plusModulo(T value, UInt256 modulo);

  T minus(T value);

  T minus(long value);

  T times(T value);

  T times(long value);

  T timesModulo(T value, UInt256 modulo);

  T dividedBy(T value);

  T dividedBy(long value);

  default T dividedCeilBy(final long value) {
    final T res = dividedBy(value);
    return mod(value).isZero() ? res : res.plus(1);
  }

  T pow(T value);

  T mod(T value);

  T mod(long value);

  Int256 signExtent(UInt256 value);

  T and(T value);

  T or(T value);

  T xor(T value);

  T not();

  /** @return The number of bits in the minimal (in term of bits) representation of this value. */
  default int bitLength() {
    return UInt256Bytes.bitLength(getBytes());
  }

  /** @return A view of the bytes of this number as signed (two's complement). */
  default Int256 asSigned() {
    return new DefaultInt256(getBytes());
  }

  /**
   * This value represented as an hexadecimal string.
   *
   * <p>Note that this representation includes all the 32 underlying bytes, no matter what the
   * integer actually represents (in other words, it can have many leading zeros). For a shorter
   * representation that don't include leading zeros, use {@link #toShortHexString}.
   *
   * @return This value represented as an hexadecimal string.
   */
  default String toHexString() {
    return getBytes().toString();
  }

  /** @return This value represented as a minimal hexadecimal string (without any leading zero). */
  default String toShortHexString() {
    final String hex = toHexString();
    // Skipping '0x'
    if (hex.charAt(2) != '0') return hex;

    int i = 3;
    while (i < hex.length() - 1 && hex.charAt(i) == '0') {
      i++;
    }
    return "0x" + hex.substring(i);
  }

  /**
   * @return This value represented as a byte-minimal hexadecimal string (without leading zero
   *     pairs).
   */
  default String toStrictShortHexString() {
    final String hex = toHexString();
    // Skipping '0x'
    if (hex.charAt(2) != '0') return hex;

    int i = 3;
    while (i < hex.length() - 1 && hex.charAt(i) == '0') {
      i++;
    }
    // Align the trim so we get full bytes, not stray nybbles.
    i = i & 0xFFFFFFFE;

    return "0x" + hex.substring(i);
  }

  /** @return This value represented as an hexadecimal string without a 0x prefix. */
  default String toUnprefixedHexString() {
    return toHexString().substring(2);
  }

  /**
   * Type-cast this value as a {@link UInt256}.
   *
   * <p>Note that the returned {@link UInt256} is a type-casted "view" of this value, and so if this
   * value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
   *
   * @return This value as a {@link UInt256}.
   */
  UInt256 asUInt256();
}
