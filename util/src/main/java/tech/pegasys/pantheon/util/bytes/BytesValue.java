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
package tech.pegasys.pantheon.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import tech.pegasys.pantheon.plugin.data.UnformattedData;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.buffer.Buffer;

/**
 * A value made of bytes.
 *
 * <p>This class essentially represents an immutable view (the view is immutable, the underlying
 * value may not be) over an array of bytes, but the backing may not be an array in practice.
 *
 * <p>This interface makes no thread-safety guarantee, and a {@link BytesValue} is generally not
 * thread safe. Specific implementations may be thread-safe however (for instance, the value
 * returned by {@link #copy} is guaranteed to be thread-safe since deeply immutable).
 *
 * @see BytesValues for static methods to create and work with {@link BytesValue}.
 */
public interface BytesValue extends Comparable<BytesValue>, UnformattedData {

  /** The empty value (with 0 bytes). */
  BytesValue EMPTY = wrap(new byte[0]);

  /**
   * Wraps the provided byte array as a {@link BytesValue}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * will be reflected in the returned value.
   *
   * @param value The value to wrap.
   * @return A {@link BytesValue} wrapping {@code value}.
   */
  static BytesValue wrap(final byte[] value) {
    return wrap(value, 0, value.length);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link BytesValue}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param value The value to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, o, l).get(0) == value[o]}.
   * @param length The length of the resulting value.
   * @return A {@link BytesValue} that expose the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + length} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length > 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + length > value.length}.
   */
  static BytesValue wrap(final byte[] value, final int offset, final int length) {
    return new ArrayWrappingBytesValue(value, offset, length);
  }

  /**
   * Wraps two other value into a concatenated view.
   *
   * <p>Note that the values are not copied, only wrapped, and thus any future update to {@code v1}
   * or {@code v2} will be reflected in the returned value. If copying the inputs is desired
   * instead, please use {@link BytesValues#concatenate(BytesValue...)}.
   *
   * @param v1 The first value to wrap.
   * @param v2 The second value to wrap.
   * @return A value representing a view over the concatenation of {@code v1} and {@code v2}.
   */
  static BytesValue wrap(final BytesValue v1, final BytesValue v2) {
    return new AbstractBytesValue() {
      @Override
      public int size() {
        return v1.size() + v2.size();
      }

      @Override
      public byte get(final int i) {
        checkElementIndex(i, size());
        return i < v1.size() ? v1.get(i) : v2.get(i - v1.size());
      }

      @Override
      public BytesValue slice(final int i, final int length) {
        if (i == 0 && length == size()) return this;
        if (length == 0) return BytesValue.EMPTY;

        checkElementIndex(i, size());
        checkArgument(
            i + length <= size(),
            "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
            length,
            size(),
            size() - i,
            i);

        if (i + length < v1.size()) return v1.slice(i, length);

        if (i >= v1.size()) return v2.slice(i - v1.size(), length);

        final MutableBytesValue res = MutableBytesValue.create(length);
        final int lengthInV1 = v1.size() - i;
        v1.slice(i, lengthInV1).copyTo(res, 0);
        v2.slice(0, length - lengthInV1).copyTo(res, lengthInV1);
        return res;
      }
    };
  }

  default BytesValue concat(final BytesValue value) {
    return wrap(this, value);
  }

  /**
   * Wraps a full Vert.x {@link Buffer} as a {@link BytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value.
   *
   * @param buffer The buffer to wrap.
   * @return A {@link BytesValue} that exposes the bytes of {@code buffer}.
   */
  static BytesValue wrapBuffer(final Buffer buffer) {
    return wrapBuffer(buffer, 0, buffer.length());
  }

  /**
   * Wraps a full Vert.x {@link Buffer} as a {@link BytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @return A {@link BytesValue} that exposes the bytes of {@code buffer}.
   */
  static BytesValue wrapBuffer(final Buffer buffer, final int offset) {
    return wrapBuffer(buffer, offset, buffer.length() - offset);
  }

  /**
   * Wraps a slice of a Vert.x {@link Buffer} as a {@link BytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @param size The size of the returned value.
   * @return A {@link BytesValue} that exposes the equivalent of {@code buffer.getBytes(offset,
   *     offset + size)} (but without copying said bytes).
   */
  static BytesValue wrapBuffer(final Buffer buffer, final int offset, final int size) {
    return MutableBytesValue.wrapBuffer(buffer, offset, size);
  }

  /**
   * Wraps a {@link ByteBuffer} as a {@link BytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value.
   *
   * @param buffer The buffer to wrap.
   * @return A {@link BytesValue} that exposes the bytes of {@code buffer}.
   */
  static BytesValue wrapBuffer(final ByteBuffer buffer) {
    return MutableBytesValue.wrapBuffer(buffer, 0, buffer.capacity());
  }

  /**
   * Wraps a {@link ByteBuffer} as a {@link BytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @param size The size of the returned value.
   * @return A {@link BytesValue} that exposes the equivalent of {@code buffer.getBytes(offset,
   *     offset + size)} (but without copying said bytes).
   */
  static BytesValue wrapBuffer(final ByteBuffer buffer, final int offset, final int size) {
    return MutableBytesValue.wrapBuffer(buffer, offset, size);
  }

  /**
   * Creates a newly allocated value that contains the provided bytes in their provided order.
   *
   * @param bytes The bytes that must compose the returned value.
   * @return A newly allocated value whose bytes are the one from {@code bytes}.
   */
  static BytesValue of(final byte... bytes) {
    return wrap(bytes);
  }

  /**
   * Creates a newly allocated value that contains the provided bytes in their provided order.
   *
   * @param bytes The bytes that must compose the returned value.
   * @return A newly allocated value whose bytes are the one from {@code bytes}.
   * @throws IllegalArgumentException if any of {@code bytes} would be truncated when storing as a
   *     byte.
   */
  @VisibleForTesting
  static BytesValue of(final int... bytes) {
    final MutableBytesValue res = MutableBytesValue.create(bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      final int b = bytes[i];
      checkArgument(b == (((byte) b) & 0xff), "%sth value %s does not fit a byte", i + 1, b);
      res.set(i, (byte) b);
    }
    return res;
  }

  /**
   * Parse an hexadecimal string into a {@link BytesValue}.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation.
   */
  static BytesValue fromHexStringLenient(final String str) {
    return BytesValues.fromHexString(str, -1, true);
  }

  /**
   * Parse an hexadecimal string into a {@link BytesValue} of the provided size.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @param destinationSize The size of the returned value, which must be big enough to hold the
   *     bytes represented by {@code str}. If it is strictly bigger those bytes from {@code str},
   *     the returned value will be left padded with zeros.
   * @return A value of size {@code destinationSize} corresponding to {@code str} potentially
   *     left-padded.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, represents more bytes than {@code destinationSize} or {@code
   *     destinationSize &lt; 0}.
   */
  static BytesValue fromHexStringLenient(final String str, final int destinationSize) {
    checkArgument(destinationSize >= 0, "Invalid negative destination size %s", destinationSize);
    return BytesValues.fromHexString(str, destinationSize, true);
  }

  /**
   * Parse an hexadecimal string into a {@link BytesValue}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, or is of an odd length.
   */
  static BytesValue fromHexString(final String str) {
    return BytesValues.fromHexString(str, -1, false);
  }

  /**
   * Parse an hexadecimal string into a {@link BytesValue}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @param destinationSize The size of the returned value, which must be big enough to hold the
   *     bytes represented by {@code str}. If it is strictly bigger those bytes from {@code str},
   *     the returned value will be left padded with zeros.
   * @return A value of size {@code destinationSize} corresponding to {@code str} potentially
   *     left-padded.
   * @throws IllegalArgumentException if {@code str} does correspond to valid hexadecimal
   *     representation, or is of an odd length.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, or is of an odd length, or represents more bytes than {@code
   *     destinationSize} or {@code destinationSize &lt; 0}.
   */
  static BytesValue fromHexString(final String str, final int destinationSize) {
    checkArgument(destinationSize >= 0, "Invalid negative destination size %s", destinationSize);
    return BytesValues.fromHexString(str, destinationSize, false);
  }

  /** @return The number of bytes this value represents. */
  @Override
  int size();

  /**
   * Retrieves a byte in this value.
   *
   * @param i The index of the byte to fetch within the value (0-indexed).
   * @return The byte at index {@code i} in this value.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   */
  byte get(int i);

  /**
   * Retrieves a byte in this value.
   *
   * @param i The index of the byte to fetch within the value (0-indexed).
   * @return The byte at index {@code i} in this value.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   */
  default byte get(final long i) {
    return get(Math.toIntExact(i));
  }

  /**
   * Retrieves the 4 bytes starting at the provided index in this value as an integer.
   *
   * @param i The index from which to get the int, which must less than or equal to {@code size() -
   *     4}.
   * @return An integer whose value is the 4 bytes from this value starting at index {@code i}.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   * @throws IllegalArgumentException if {@code i &gt; size() - 4}.
   */
  default int getInt(final int i) {
    checkElementIndex(i, size());
    checkArgument(
        i <= size() - 4,
        "Value of size %s has not enough bytes to read a 4 bytes int from index %s",
        size(),
        i);

    int value = 0;
    value |= (get(i) & 0xFF) << 24;
    value |= (get(i + 1) & 0xFF) << 16;
    value |= (get(i + 2) & 0xFF) << 8;
    value |= (get(i + 3) & 0xFF);
    return value;
  }

  /**
   * Retrieves the 8 bytes starting at the provided index in this value as a long.
   *
   * @param i The index from which to get the long, which must less than or equal to {@code size() -
   *     8}.
   * @return A long whose value is the 8 bytes from this value starting at index {@code i}.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   * @throws IllegalArgumentException if {@code i &gt; size() - 8}.
   */
  default long getLong(final int i) {
    checkElementIndex(i, size());
    checkArgument(
        i <= size() - 8,
        "Value of size %s has not enough bytes to read a 8 bytes long from index %s",
        size(),
        i);

    return (((long) getInt(i)) << 32) | (((getInt(i + 4))) & 0xFFFFFFFFL);
  }

  /**
   * Creates a new value representing (a view of) a slice of the bytes of this value.
   *
   * <p>Please note that the resulting slice is only a view and as such maintains a link to the
   * underlying full value. So holding a reference to the returned slice may hold more memory than
   * the slide represents. Use {@link #copy} on the returned slice if that is not what you want.
   *
   * @param index The start index for the slice.
   * @return A new value providing a view over the bytes from index {@code index} (included) to the
   *     end.
   * @throws IndexOutOfBoundsException if {@code index &lt; 0}.
   */
  BytesValue slice(int index);

  /**
   * Creates a new value representing (a view of) a slice of the bytes of this value.
   *
   * <p>Please note that the resulting slice is only a view and as such maintains a link to the
   * underlying full value. So holding a reference to the returned slice may hold more memory than
   * the slide represents. Use {@link #copy} on the returned slice if that is not what you want.
   *
   * @param index The start index for the slice.
   * @param length The length of the resulting value.
   * @return A new value providing a view over the bytes from index {@code index} (included) to
   *     {@code index + length} (excluded).
   * @throws IllegalArgumentException if {@code length &lt; 0}.
   * @throws IndexOutOfBoundsException if {@code index &lt; 0} or {index &gt;= size()} or {index +
   *     length &gt; size()} .
   */
  BytesValue slice(int index, int length);

  /**
   * Returns a value equivalent to this one but that is guaranteed to 1) be deeply immutable (that
   * is, the underlying value will be immutable) and 2) to not retain more bytes than exposed by the
   * value.
   *
   * @return A value, equals to this one, but deeply immutable and that doesn't retain any
   *     "unreachable" bytes. For performance reasons, this is allowed to return this value however
   *     if it already fit those constraints.
   */
  BytesValue copy();

  /**
   * Returns a new mutable value initialized with the content of this value.
   *
   * @return A mutable copy of this value. This will copy bytes, modifying the returned value will
   *     <b>not</b> modify this value.
   */
  MutableBytesValue mutableCopy();

  /**
   * Copy the bytes of this value to the provided mutable one, which must have the same size.
   *
   * @param destination The mutable value to which to copy the bytes to, which must have the same
   *     size as this value. If you want to copy value where size differs, you should use {@link
   *     #slice} and/or {@link MutableBytesValue#mutableSlice} and apply the copy to the result.
   * @throws IllegalArgumentException if {@code this.size() != destination.size()}.
   */
  void copyTo(MutableBytesValue destination);

  /**
   * Copy the bytes of this value to the provided mutable one from a particular offset.
   *
   * <p>This is a (potentially slightly more efficient) shortcut for {@code
   * copyTo(destination.mutableSlice(destinationOffset, this.size()))}.
   *
   * @param destination The mutable value to which to copy the bytes to, which must have enough
   *     bytes from {@code destinationOffset} for the copied value.
   * @param destinationOffset The offset in {@code destination} at which the copy starts.
   * @throws IllegalArgumentException if the destination doesn't have enough room, that is if {@code
   *     this.size() &gt; (destination.size() - destinationOffset)}.
   */
  void copyTo(MutableBytesValue destination, int destinationOffset);

  /**
   * Appends the bytes of this value to the provided Vert.x {@link Buffer}.
   *
   * <p>Note that since a Vert.x {@link Buffer} will grow as necessary, this method never fails.
   *
   * @param buffer The {@link Buffer} to which to append this value.
   */
  default void appendTo(final Buffer buffer) {
    for (int i = 0; i < size(); i++) {
      buffer.appendByte(get(i));
    }
  }

  default void copyTo(final byte[] dest, final int srcPos, final int destPos) {
    System.arraycopy(getArrayUnsafe(), srcPos, dest, destPos, size() - srcPos);
  }

  /**
   * Return the number of bytes in common between this set of bytes and another.
   *
   * @param other The bytes to compare to.
   * @return The number of common bytes.
   */
  int commonPrefixLength(BytesValue other);

  /**
   * Return a slice over the common prefix between this set of bytes and another.
   *
   * @param other The bytes to compare to.
   * @return A slice covering the common prefix.
   */
  BytesValue commonPrefix(BytesValue other);

  /**
   * Update the provided message digest with the bytes of this value.
   *
   * @param digest The digest to update.
   */
  void update(MessageDigest digest);

  /**
   * Whether this value has only zeroed bytes.
   *
   * @return True if all the bits of this value are zeros.
   */
  boolean isZero();

  /**
   * Whether this value is empty, that is is of zero size.
   *
   * @return {@code true} if {@code size() == 0}, {@code false} otherwise.
   */
  default boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Extracts the bytes of this value into a newly allocated byte array.
   *
   * @return A newly allocated byte array with the same content than this value.
   */
  default byte[] extractArray() {
    final int size = size();
    final byte[] array = new byte[size];
    for (int i = 0; i < size; i++) {
      array[i] = get(i);
    }
    return array;
  }

  @Override
  default byte[] getByteArray() {
    return extractArray();
  }

  /**
   * Get the bytes represented by this value as byte array.
   *
   * <p>Contrarily to {@link #extractArray()}, this may avoid allocating a new array and directly
   * return the backing array of this value if said value is array backed and doing so is possible.
   * As such, modifications to the returned array may or may not impact this value. As such, this
   * method should be used with care and hence the "unsafe" moniker.
   *
   * @return A byte array with the same content than this value, which may or may not be the direct
   *     backing of this value.
   */
  default byte[] getArrayUnsafe() {
    return extractArray();
  }

  @Override
  default int compareTo(final BytesValue other) {
    final int minSize = Math.min(size(), other.size());
    for (int i = 0; i < minSize; i++) {
      // Using integer comparison to basically simulate unsigned byte comparison
      final int cmp = Integer.compare(get(i) & 0xFF, other.get(i) & 0xFF);
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(size(), other.size());
  }

  /**
   * Returns the hexadecimal string representation of this value.
   *
   * @return The hexadecimal representation of this value, starting with "0x".
   */
  @Override
  String toString();

  /**
   * Returns the hexadecimal string representation of this value.
   *
   * @return The hexadecimal representation of this value, starting with "0x".
   */
  @Override
  default String getHexString() {
    return toString();
  }

  default String toUnprefixedString() {
    final String prefixedHex = toString();
    return prefixedHex.startsWith("0x") ? prefixedHex.substring(2) : prefixedHex;
  }
}
