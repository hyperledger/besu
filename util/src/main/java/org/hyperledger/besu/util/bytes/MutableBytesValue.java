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

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

/**
 * A mutable {@link BytesValue}.
 *
 * @see BytesValues for static methods to create and work with {@link MutableBytesValue}.
 */
public interface MutableBytesValue extends BytesValue {

  /**
   * The empty value (with 0 bytes).
   *
   * <p>Note that while this implements {@link MutableBytesValue} so it can be used where a {@link
   * MutableBytesValue} is required, it is effectively immutable since empty and thus having nothing
   * to mutate.
   */
  MutableBytesValue EMPTY = wrap(new byte[0]);

  /**
   * Creates a new mutable byte value of the provided size.
   *
   * @param size The size of the returned value.
   * @return A newly allocated {@link MutableBytesValue}.
   */
  static MutableBytesValue create(final int size) {
    return new MutableArrayWrappingBytesValue(new byte[size]);
  }

  /**
   * Wraps a byte array as a mutable byte value.
   *
   * <p>This method behave exactly as {@link BytesValue#wrap(byte[])} except that the result is
   * mutable.
   *
   * @param value The value to wrap.
   * @return A {@link MutableBytesValue} wrapping {@code value}.
   */
  static MutableBytesValue wrap(final byte[] value) {
    return new MutableArrayWrappingBytesValue(value);
  }

  /**
   * /** Wraps a byte array as a mutable byte value.
   *
   * <p>This method behave exactly as {@link BytesValue#wrap(byte[],int,int)} except that the result
   * is mutable.
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
  static MutableBytesValue wrap(final byte[] value, final int offset, final int length) {
    return new MutableArrayWrappingBytesValue(value, offset, length);
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
   * Wraps a slice of a Vert.x {@link Buffer} as a {@link MutableBytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value, and any change to the returned value will be reflected in the
   * buffer.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @param size The size of the returned value.
   * @return A {@link MutableBytesValue} that exposes (reading and writing) the bytes in {@code
   *     buffer} from {@code offset} (inclusive) to {@code offset + size} (exclusive).
   */
  static MutableBytesValue wrapBuffer(final Buffer buffer, final int offset, final int size) {
    if (size == 0) {
      return EMPTY;
    }
    return new MutableBufferWrappingBytesValue(buffer, offset, size);
  }

  /**
   * Wraps a full Netty {@link ByteBuf} as a {@link BytesValue}.
   *
   * @param buffer The buffer to wrap.
   * @return A {@link BytesValue} that exposes the bytes of {@code buffer}.
   */
  static BytesValue wrapBuffer(final ByteBuf buffer) {
    return wrapBuffer(buffer, buffer.readerIndex(), buffer.readableBytes());
  }

  /**
   * Wraps a slice of a Netty {@link ByteBuf} as a {@link MutableBytesValue}.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @param size The size of the returned value.
   * @return A {@link MutableBytesValue} that exposes (reading and writing) the bytes in {@code
   *     buffer} from {@code offset} (inclusive) to {@code offset + size} (exclusive).
   */
  static MutableBytesValue wrapBuffer(final ByteBuf buffer, final int offset, final int size) {
    if (size == 0) {
      return EMPTY;
    }
    return new MutableByteBufWrappingBytesValue(buffer, offset, size);
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
   * Wraps a slice of a {@link ByteBuffer} as a {@link MutableBytesValue}.
   *
   * <p>Note that as the buffer is wrapped, any change to the content of that buffer may be
   * reflected in the returned value, and any change to the returned value will be reflected in the
   * buffer.
   *
   * @param buffer The buffer to wrap.
   * @param offset The offset in {@code buffer} from which to expose the bytes in the returned
   *     value. That is, {@code wrapBuffer(buffer, i, 1).get(0) == buffer.getByte(i)}.
   * @param size The size of the returned value.
   * @return A {@link MutableBytesValue} that exposes (reading and writing) the bytes in {@code
   *     buffer} from {@code offset} (inclusive) to {@code offset + size} (exclusive).
   */
  static MutableBytesValue wrapBuffer(final ByteBuffer buffer, final int offset, final int size) {
    if (size == 0) {
      return EMPTY;
    }
    return new MutableByteBufferWrappingBytesValue(buffer, offset, size);
  }

  /**
   * Sets a particular byte in this value.
   *
   * @param i The index of the byte to set.
   * @param b The value to set that byte to.
   * @throws IndexOutOfBoundsException if {@code i < 0} or {i &gt;= size()}.
   */
  void set(int i, byte b);

  /**
   * Sets the 4 bytes starting at the provided index in this value to the provided integer value.
   *
   * @param i The index from which to set the int, which must less than or equal to {@code size() -
   *     4}.
   * @param value The value to set.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   * @throws IllegalArgumentException if {@code i &gt; size() - 4}.
   */
  default void setInt(final int i, final int value) {
    checkElementIndex(i, size());
    checkArgument(
        i <= size() - 4,
        "Value of size %s has not enough bytes to write a 4 bytes int from index %s",
        size(),
        i);

    set(i, (byte) (value >>> 24));
    set(i + 1, (byte) ((value >>> 16) & 0xFF));
    set(i + 2, (byte) ((value >>> 8) & 0xFF));
    set(i + 3, (byte) (value & 0xFF));
  }

  /**
   * Sets the 8 bytes starting at the provided index in this value to the provided long value.
   *
   * @param i The index from which to set the long, which must less than or equal to {@code size() -
   *     8}.
   * @param value The value to set.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()}.
   * @throws IllegalArgumentException if {@code i &gt; size() - 8}.
   */
  default void setLong(final int i, final long value) {
    checkElementIndex(i, size());
    checkArgument(
        i <= size() - 8,
        "Value of size %s has not enough bytes to write a 8 bytes long from index %s",
        size(),
        i);

    setInt(i, (int) (value >>> 32));
    setInt(i + 4, (int) value);
  }

  /**
   * Creates a new value representing a mutable slice of the bytes of this value.
   *
   * <p>Please note that the resulting slice is only a view and as such maintains a link to the
   * underlying full value. So holding a reference to the returned slice may hold more memory than
   * the slide represents. Use {@link #copy} on the returned slice if that is not what you want.
   *
   * @param i The start index for the slice.
   * @param length The length of the resulting value.
   * @return A new mutable view over the bytes of this value from index {@code i} (included) to
   *     index {@code i + length} (excluded).
   * @throws IllegalArgumentException if {@code length &lt; 0}.
   * @throws IndexOutOfBoundsException if {@code i &lt; 0} or {i &gt;= size()} or {i + length &gt;
   *     size()} .
   */
  MutableBytesValue mutableSlice(int i, int length);

  /**
   * Fills all the bytes of this value with the provided byte.
   *
   * @param b The byte to use to fill the value.
   */
  default void fill(final byte b) {
    for (int i = 0; i < size(); i++) {
      set(i, b);
    }
  }

  /** Clears all the bytes (set to 0) of this value. */
  default void clear() {
    fill((byte) 0);
  }
}
