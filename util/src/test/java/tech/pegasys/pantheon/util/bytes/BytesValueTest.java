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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexStringLenient;
import static tech.pegasys.pantheon.util.bytes.BytesValue.of;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrap;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrapBuffer;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BytesValueTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static BytesValue h(final String hex) {
    return fromHexString(hex);
  }

  private static Buffer b(final String hex) {
    return Buffer.buffer(fromHexString(hex).getArrayUnsafe());
  }

  private static ByteBuf bb(final String hex) {
    final byte[] bytes = fromHexString(hex).getArrayUnsafe();
    return Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length, Integer.MAX_VALUE))
        .writeBytes(bytes);
  }

  @Test
  public void rangeOfWrap() {
    assertEquals(BytesValue.EMPTY, wrap(new byte[0]));

    assertWrap(new byte[10]);
    assertWrap(new byte[] {1});
    assertWrap(new byte[] {1, 2, 3, 4});
    assertWrap(new byte[] {-1, 127, -128});
  }

  private static void assertWrap(final byte[] bytes) {
    final BytesValue value = wrap(bytes);
    assertEquals(bytes.length, value.size());
    assertArrayEquals(bytes, value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void testWrapNull() {
    wrap(null);
  }

  /** Checks that modifying a wrapped array modifies the value itself. */
  @Test
  public void wrapReflectsUpdates() {
    final byte[] bytes = new byte[] {1, 2, 3, 4, 5};
    final BytesValue value = wrap(bytes);

    assertEquals(bytes.length, value.size());
    assertArrayEquals(bytes, value.extractArray());

    bytes[1] = 127;
    bytes[3] = 127;

    assertEquals(bytes.length, value.size());
    assertArrayEquals(bytes, value.extractArray());
  }

  @Test
  public void wrapSlice() {
    assertEquals(BytesValue.EMPTY, wrap(new byte[0], 0, 0));
    assertEquals(BytesValue.EMPTY, wrap(new byte[] {1, 2, 3}, 0, 0));
    assertEquals(BytesValue.EMPTY, wrap(new byte[] {1, 2, 3}, 2, 0));

    assertWrapSlice(new byte[] {1, 2, 3, 4}, 0, 4);
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 0, 2);
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 2, 1);
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 2, 2);
  }

  private static void assertWrapSlice(final byte[] bytes, final int offset, final int length) {
    final BytesValue value = wrap(bytes, offset, length);
    assertEquals(length, value.size());
    assertArrayEquals(Arrays.copyOfRange(bytes, offset, offset + length), value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void wrapSliceNull() {
    wrap(null, 0, 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void wrapSliceNegativeOffset() {
    assertWrapSlice(new byte[] {1, 2, 3, 4}, -1, 4);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void wrapSliceOutOfBoundOffset() {
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 5, 1);
  }

  @Test
  public void wrapSliceNegativeLength() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid negative length");
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 0, -2);
  }

  @Test
  public void wrapSliceTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Provided length 3 is too big: the value has only 2 bytes from offset 2");
    assertWrapSlice(new byte[] {1, 2, 3, 4}, 2, 3);
  }

  /**
   * Checks that modifying a wrapped array modifies the value itself, but only if within the wrapped
   * slice.
   */
  @Test
  public void wrapSliceReflectsUpdates() {
    final byte[] bytes = new byte[] {1, 2, 3, 4, 5};
    assertWrapSlice(bytes, 2, 2);
    bytes[2] = 127;
    bytes[3] = 127;
    assertWrapSlice(bytes, 2, 2);

    final BytesValue wrapped = wrap(bytes, 2, 2);
    final BytesValue copy = wrapped.copy();

    // Modify the bytes outside of the wrapped slice and check this doesn't affect the value (that
    // it is still equal to the copy from before the updates)
    bytes[0] = 127;
    assertEquals(copy, wrapped);

    // Sanity check for copy(): modify within the wrapped slice and check the copy differs now.
    bytes[2] = 42;
    assertNotEquals(copy, wrapped);
  }

  @Test
  public void concatenatedWrap() {
    assertConcatenatedWrap(new byte[] {}, new byte[] {});
    assertConcatenatedWrap(new byte[] {}, new byte[] {1, 2, 3});
    assertConcatenatedWrap(new byte[] {1, 2, 3}, new byte[] {});
    assertConcatenatedWrap(new byte[] {1, 2, 3}, new byte[] {4, 5});
  }

  private static void assertConcatenatedWrap(final byte[] first, final byte[] second) {
    final byte[] res = wrap(wrap(first), wrap(second)).extractArray();
    assertArrayEquals(first, Arrays.copyOfRange(res, 0, first.length));
    assertArrayEquals(second, Arrays.copyOfRange(res, first.length, res.length));
  }

  @Test
  public void concatenatedWrapReflectsUpdates() {
    final byte[] first = new byte[] {1, 2, 3};
    final byte[] second = new byte[] {4, 5};
    final byte[] expected1 = new byte[] {1, 2, 3, 4, 5};
    final BytesValue res = wrap(wrap(first), wrap(second));
    assertArrayEquals(expected1, res.extractArray());

    first[1] = 42;
    second[0] = 42;
    final byte[] expected2 = new byte[] {1, 42, 3, 42, 5};
    assertArrayEquals(expected2, res.extractArray());
  }

  @Test
  public void wrapByteBuf() {
    assertEquals(BytesValue.EMPTY, wrapBuffer(Buffer.buffer()));

    assertWrapByteBuf(new byte[10]);
    assertWrapByteBuf(new byte[] {1});
    assertWrapByteBuf(new byte[] {1, 2, 3, 4});
    assertWrapByteBuf(new byte[] {-1, 127, -128});
  }

  private static void assertWrapByteBuf(final byte[] bytes) {
    final ByteBuf buffer =
        Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length, Integer.MAX_VALUE))
            .writeBytes(bytes);
    final BytesValue value = wrapBuffer(buffer);
    assertEquals(buffer.writerIndex(), value.size());
    final byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    assertArrayEquals(arr, value.extractArray());
  }

  @Test
  public void rangeOfWrapBuffer() {
    assertEquals(BytesValue.EMPTY, wrapBuffer(Buffer.buffer()));

    assertWrapBuffer(new byte[10]);
    assertWrapBuffer(new byte[] {1});
    assertWrapBuffer(new byte[] {1, 2, 3, 4});
    assertWrapBuffer(new byte[] {-1, 127, -128});
  }

  private static void assertWrapBuffer(final byte[] bytes) {
    final Buffer buffer = Buffer.buffer(bytes);
    final BytesValue value = wrapBuffer(buffer);
    assertEquals(buffer.length(), value.size());
    assertArrayEquals(buffer.getBytes(), value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void wrapBufferNull() {
    wrapBuffer((Buffer) null);
  }

  @Test(expected = NullPointerException.class)
  public void wrapByteBufNull() {
    wrapBuffer((ByteBuf) null);
  }

  /** Checks that modifying a wrapped buffer modifies the value itself. */
  @Test
  public void wrapBufferReflectsUpdates() {
    final Buffer buffer = Buffer.buffer(new byte[] {1, 2, 3, 4, 5});
    final BytesValue value = wrapBuffer(buffer);

    assertEquals(buffer.length(), value.size());
    assertArrayEquals(buffer.getBytes(), value.extractArray());

    buffer.setByte(1, (byte) 127);
    buffer.setByte(3, (byte) 127);

    assertEquals(buffer.length(), value.size());
    assertArrayEquals(buffer.getBytes(), value.extractArray());
  }

  @Test
  public void wrapBufferSlice() {
    assertEquals(BytesValue.EMPTY, wrapBuffer(Buffer.buffer(new byte[0]), 0, 0));
    assertEquals(BytesValue.EMPTY, wrapBuffer(Buffer.buffer(new byte[] {1, 2, 3}), 0, 0));
    assertEquals(BytesValue.EMPTY, wrapBuffer(Buffer.buffer(new byte[] {1, 2, 3}), 2, 0));

    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 0, 4);
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 0, 2);
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 2, 1);
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 2, 2);
  }

  private static void assertWrapBufferSlice(
      final byte[] bytes, final int offset, final int length) {
    final Buffer buffer = Buffer.buffer(bytes);
    final BytesValue value = wrapBuffer(buffer, offset, length);
    assertEquals(length, value.size());
    assertArrayEquals(Arrays.copyOfRange(bytes, offset, offset + length), value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void wrapBufferSliceNull() {
    wrapBuffer((Buffer) null, 0, 2);
  }

  @Test(expected = NullPointerException.class)
  public void wrapByteBufSliceNull() {
    wrapBuffer((ByteBuf) null, 0, 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void wrapBufferSliceNegativeOffset() {
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, -1, 4);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void wrapBufferSliceOutOfBoundOffset() {
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 5, 1);
  }

  @Test
  public void wrapBufferSliceNegativeLength() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid negative length");
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 0, -2);
  }

  @Test
  public void wrapBufferSliceTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Provided length 3 is too big: the buffer has size 4 and has only 2 bytes from 2");
    assertWrapBufferSlice(new byte[] {1, 2, 3, 4}, 2, 3);
  }

  /**
   * Checks that modifying a wrapped array modifies the value itself, but only if within the wrapped
   * slice.
   */
  @Test
  public void wrapBufferSliceReflectsUpdates() {
    final Buffer buffer = Buffer.buffer(new byte[] {1, 2, 3, 4, 5});
    final BytesValue value = wrapBuffer(buffer, 2, 2);
    final BytesValue copy = value.copy();

    assertEquals(2, value.size());
    assertArrayEquals(Arrays.copyOfRange(buffer.getBytes(), 2, 4), value.extractArray());

    // Modify within the wrapped slice. This should have modified the wrapped value but not its copy
    buffer.setByte(2, (byte) 127);
    buffer.setByte(3, (byte) 127);

    assertArrayEquals(Arrays.copyOfRange(buffer.getBytes(), 2, 4), value.extractArray());
    assertFalse(Arrays.equals(Arrays.copyOfRange(buffer.getBytes(), 2, 4), copy.extractArray()));

    final BytesValue newCopy = value.copy();

    // Modify the bytes outside of the wrapped slice and check this doesn't affect the value (that
    // it is still equal to the copy from before the updates)
    buffer.setByte(0, (byte) 127);
    assertEquals(newCopy, value);
  }

  @Test
  public void bytes() {
    assertArrayEquals(new byte[] {}, of().extractArray());
    assertArrayEquals(new byte[] {1, 2}, of((byte) 1, (byte) 2).extractArray());
    assertArrayEquals(
        new byte[] {1, 2, 3, 4, 5},
        of((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5).extractArray());

    assertArrayEquals(new byte[] {-1, 2, -3}, of((byte) -1, (byte) 2, (byte) -3).extractArray());
  }

  @Test
  public void integers() {
    assertArrayEquals(new byte[] {1, 2}, of(1, 2).extractArray());
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, of(1, 2, 3, 4, 5).extractArray());
    assertArrayEquals(new byte[] {-1, 127, -128}, of(0xff, 0x7f, 0x80).extractArray());
  }

  @Test
  public void integerTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("3th value 256 does not fit a byte");
    of(2, 3, 256);
  }

  @Test
  public void integerTooLow() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("2th value -1 does not fit a byte");
    of(2, -1, 3);
  }

  @Test
  public void hexStringLenient() {
    assertEquals(of(), fromHexStringLenient(""));
    assertEquals(of(), fromHexStringLenient("0x"));

    assertEquals(of(0), fromHexStringLenient("0"));
    assertEquals(of(0), fromHexStringLenient("0x0"));
    assertEquals(of(0), fromHexStringLenient("00"));
    assertEquals(of(0), fromHexStringLenient("0x00"));
    assertEquals(of(1), fromHexStringLenient("0x1"));
    assertEquals(of(1), fromHexStringLenient("0x01"));

    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("1FF2A"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x1FF2A"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x1ff2a"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x1fF2a"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("01FF2A"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x01FF2A"));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x01ff2A"));
  }

  @Test
  public void hxeStringLenientInvalidInput() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Illegal character 'o' found at index 1");
    fromHexStringLenient("foo");
  }

  @Test
  public void hexStringLenientLeftPadding() {
    assertEquals(of(), fromHexStringLenient("", 0));
    assertEquals(of(0), fromHexStringLenient("", 1));
    assertEquals(of(0, 0), fromHexStringLenient("", 2));
    assertEquals(of(0, 0), fromHexStringLenient("0x", 2));

    assertEquals(of(0, 0, 0), fromHexStringLenient("0", 3));
    assertEquals(of(0, 0, 0), fromHexStringLenient("0x0", 3));
    assertEquals(of(0, 0, 0), fromHexStringLenient("00", 3));
    assertEquals(of(0, 0, 0), fromHexStringLenient("0x00", 3));
    assertEquals(of(0, 0, 1), fromHexStringLenient("0x1", 3));
    assertEquals(of(0, 0, 1), fromHexStringLenient("0x01", 3));

    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("1FF2A", 3));
    assertEquals(of(0x00, 0x01, 0xff, 0x2a), fromHexStringLenient("0x1FF2A", 4));
    assertEquals(of(0x00, 0x00, 0x01, 0xff, 0x2a), fromHexStringLenient("0x1ff2a", 5));
    assertEquals(of(0x00, 0x01, 0xff, 0x2a), fromHexStringLenient("0x1fF2a", 4));
    assertEquals(of(0x00, 0x01, 0xff, 0x2a), fromHexStringLenient("01FF2A", 4));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x01FF2A", 3));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexStringLenient("0x01ff2A", 3));
  }

  @Test
  public void hexStringLenientLeftPaddingInvalidInput() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Illegal character 'o' found at index 1");
    fromHexStringLenient("foo", 10);
  }

  @Test
  public void hexStringLenientLeftPaddingInvalidSize() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Hex value 0x001F34 is too big: expected at most 2 bytes but got 3");
    fromHexStringLenient("0x001F34", 2);
  }

  @Test
  public void hexString() {
    assertEquals(of(), fromHexString("0x"));

    assertEquals(of(0), fromHexString("00"));
    assertEquals(of(0), fromHexString("0x00"));
    assertEquals(of(1), fromHexString("0x01"));

    assertEquals(of(1, 0xff, 0x2a), fromHexString("01FF2A"));
    assertEquals(of(1, 0xff, 0x2a), fromHexString("0x01FF2A"));
    assertEquals(of(1, 0xff, 0x2a), fromHexString("0x01ff2a"));
    assertEquals(of(1, 0xff, 0x2a), fromHexString("0x01fF2a"));
  }

  @Test
  public void hexStringInvalidInput() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Illegal character 'o' found at index 1");
    fromHexString("fooo");
  }

  @Test
  public void hexStringNotLenient() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid odd-length hex binary representation");
    fromHexString("0x100");
  }

  @Test
  public void hexStringLeftPadding() {
    assertEquals(of(), fromHexString("0x", 0));
    assertEquals(of(0, 0), fromHexString("0x", 2));
    assertEquals(of(0, 0, 0, 0), fromHexString("0x", 4));

    assertEquals(of(0, 0), fromHexString("00", 2));
    assertEquals(of(0, 0), fromHexString("0x00", 2));
    assertEquals(of(0, 0, 1), fromHexString("0x01", 3));

    assertEquals(of(0x00, 0x01, 0xff, 0x2a), fromHexString("01FF2A", 4));
    assertEquals(of(0x01, 0xff, 0x2a), fromHexString("0x01FF2A", 3));
    assertEquals(of(0x00, 0x00, 0x01, 0xff, 0x2a), fromHexString("0x01ff2a", 5));
    assertEquals(of(0x00, 0x00, 0x01, 0xff, 0x2a), fromHexString("0x01fF2a", 5));
  }

  @Test
  public void hexStringLeftPaddingInvalidInput() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Illegal character 'o' found at index 1");
    fromHexString("fooo", 4);
  }

  @Test
  public void hexStringLeftPaddingNotLenient() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid odd-length hex binary representation");
    fromHexString("0x100", 4);
  }

  @Test
  public void hexStringLeftPaddingInvalidSize() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Hex value 0x001F34 is too big: expected at most 2 bytes but got 3");
    fromHexStringLenient("0x001F34", 2);
  }

  @Test
  public void sizes() {
    assertEquals(0, wrap(new byte[0]).size());
    assertEquals(1, wrap(new byte[1]).size());
    assertEquals(10, wrap(new byte[10]).size());
  }

  @Test
  public void gets() {
    final BytesValue v = wrap(new byte[] {1, 2, 3, 4});
    assertEquals(1, v.get(0));
    assertEquals(2, v.get(1));
    assertEquals(3, v.get(2));
    assertEquals(4, v.get(3));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getNegativeIndex() {
    wrap(new byte[] {1, 2, 3, 4}).get(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getOutOfBound() {
    wrap(new byte[] {1, 2, 3, 4}).get(4);
  }

  @Test
  public void getInt() {
    final BytesValue value = wrap(new byte[] {0, 0, 1, 0, -1, -1, -1, -1});

    // 0x00000100 = 256
    assertEquals(256, value.getInt(0));
    // 0x000100FF = 65536 + 255 = 65791
    assertEquals(65791, value.getInt(1));
    // 0x0100FFFF = 16777216 (2^24) + (65536 - 1) = 16842751
    assertEquals(16842751, value.getInt(2));
    // 0xFFFFFFFF = -1
    assertEquals(-1, value.getInt(4));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getIntNegativeIndex() {
    wrap(new byte[] {1, 2, 3, 4}).getInt(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getIntOutOfBound() {
    wrap(new byte[] {1, 2, 3, 4}).getInt(4);
  }

  @Test
  public void getIntNotEnoughBytes() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Value of size 4 has not enough bytes to read a 4 bytes int from index 1");
    wrap(new byte[] {1, 2, 3, 4}).getInt(1);
  }

  @Test
  public void getLong() {
    final BytesValue value1 = wrap(new byte[] {0, 0, 1, 0, -1, -1, -1, -1, 0, 0});
    // 0x00000100FFFFFFFF = (2^40) + (2^32) - 1 = 1103806595071
    assertEquals(1103806595071L, value1.getLong(0));
    // 0x 000100FFFFFFFF00 = (2^48) + (2^40) - 1 - 255 = 282574488338176
    assertEquals(282574488338176L, value1.getLong(1));

    final BytesValue value2 = wrap(new byte[] {-1, -1, -1, -1, -1, -1, -1, -1});
    assertEquals(-1L, value2.getLong(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getLongNegativeIndex() {
    wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}).getLong(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getLongOutOfBound() {
    wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}).getLong(8);
  }

  @Test
  public void getLongNotEnoughBytes() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Value of size 4 has not enough bytes to read a 8 bytes long from index 0");
    wrap(new byte[] {1, 2, 3, 4}).getLong(0);
  }

  @Test
  public void rangedSlice() {
    assertEquals(h("0x"), h("0x0123456789").slice(0, 0));
    assertEquals(h("0x"), h("0x0123456789").slice(2, 0));
    assertEquals(h("0x01"), h("0x0123456789").slice(0, 1));
    assertEquals(h("0x0123"), h("0x0123456789").slice(0, 2));

    assertEquals(h("0x4567"), h("0x0123456789").slice(2, 2));
    assertEquals(h("0x23456789"), h("0x0123456789").slice(1, 4));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void sliceNegativeOffset() {
    h("0x012345").slice(-1, 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void sliceOffsetOutOfBound() {
    h("0x012345").slice(3, 2);
  }

  @Test
  public void sliceTooLong() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Provided length 3 is too big: the value has size 3 and has only 2 bytes from 1");
    h("0x012345").slice(1, 3);
  }

  @Test
  public void rangedMutableCopy() {
    final BytesValue v = h("0x012345");
    final MutableBytesValue mutableCopy = v.mutableCopy();

    // Initially, copy must be equal.
    assertEquals(mutableCopy, v);

    // Upon modification, original should not have been modified.
    mutableCopy.set(0, (byte) -1);
    assertNotEquals(mutableCopy, v);
    assertEquals(h("0x012345"), v);
    assertEquals(h("0xFF2345"), mutableCopy);
  }

  @Test
  public void copying() {
    MutableBytesValue dest;

    // The follow does nothing, but simply making sure it doesn't throw.
    dest = MutableBytesValue.EMPTY;
    BytesValue.EMPTY.copyTo(dest);
    assertEquals(BytesValue.EMPTY, dest);

    dest = MutableBytesValue.create(1);
    of(1).copyTo(dest);
    assertEquals(h("0x01"), dest);

    dest = MutableBytesValue.create(1);
    of(10).copyTo(dest);
    assertEquals(h("0x0A"), dest);

    dest = MutableBytesValue.create(2);
    of(0xff, 0x03).copyTo(dest);
    assertEquals(h("0xFF03"), dest);

    dest = MutableBytesValue.create(4);
    of(0xff, 0x03).copyTo(dest.mutableSlice(1, 2));
    assertEquals(h("0x00FF0300"), dest);
  }

  @Test
  public void copyingToTooSmall() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot copy 3 bytes to destination of non-equal size 2");
    of(1, 2, 3).copyTo(MutableBytesValue.create(2));
  }

  @Test
  public void copyingToTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot copy 3 bytes to destination of non-equal size 4");
    of(1, 2, 3).copyTo(MutableBytesValue.create(4));
  }

  @Test
  public void copyingToWithOffset() {
    MutableBytesValue dest;

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    BytesValue.EMPTY.copyTo(dest, 0);
    assertEquals(h("0x010203"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    of(1).copyTo(dest, 1);
    assertEquals(h("0x010103"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    of(2).copyTo(dest, 0);
    assertEquals(h("0x020203"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    of(1, 1).copyTo(dest, 1);
    assertEquals(h("0x010101"), dest);

    dest = MutableBytesValue.create(4);
    of(0xff, 0x03).copyTo(dest, 1);
    assertEquals(h("0x00FF0300"), dest);
  }

  @Test
  public void copyingToWithOffsetTooSmall() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot copy 3 bytes, destination has only 2 bytes from index 2");
    of(1, 2, 3).copyTo(MutableBytesValue.create(4), 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void copyingToWithNegativeOffset() {
    of(1, 2, 3).copyTo(MutableBytesValue.create(10), -1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void copyingToWithOutOfBoundIndex() {
    of(1, 2, 3).copyTo(MutableBytesValue.create(10), 10);
  }

  @Test
  public void appending() {
    assertAppendTo(BytesValue.EMPTY, Buffer.buffer(), BytesValue.EMPTY);
    assertAppendTo(BytesValue.EMPTY, b("0x1234"), h("0x1234"));
    assertAppendTo(h("0x1234"), Buffer.buffer(), h("0x1234"));
    assertAppendTo(h("0x5678"), b("0x1234"), h("0x12345678"));
  }

  private void assertAppendTo(
      final BytesValue toAppend, final Buffer buffer, final BytesValue expected) {
    toAppend.appendTo(buffer);
    assertEquals(expected, BytesValue.wrap(buffer.getBytes()));
  }

  @Test
  public void appendingToByteBuf() {
    final byte[] bytes0 = new byte[0];
    final byte[] bytes1 = new byte[0];
    assertAppendToByteBuf(
        BytesValue.EMPTY,
        Unpooled.unreleasableBuffer(Unpooled.buffer(bytes0.length, Integer.MAX_VALUE))
            .writeBytes(bytes0),
        BytesValue.EMPTY);
    assertAppendToByteBuf(BytesValue.EMPTY, bb("0x1234"), h("0x1234"));
    assertAppendToByteBuf(
        h("0x1234"),
        Unpooled.unreleasableBuffer(Unpooled.buffer(bytes1.length, Integer.MAX_VALUE))
            .writeBytes(bytes1),
        h("0x1234"));
    assertAppendToByteBuf(h("0x5678"), bb("0x1234"), h("0x12345678"));
  }

  private void assertAppendToByteBuf(
      final BytesValue toAppend, final ByteBuf buffer, final BytesValue expected) {
    toAppend.appendTo(buffer);
    final byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    assertEquals(expected, BytesValue.wrap(arr));
  }

  @Test
  public void zero() {
    assertTrue(BytesValue.EMPTY.isZero());
    assertTrue(BytesValue.of(0).isZero());
    assertTrue(BytesValue.of(0, 0, 0).isZero());

    assertFalse(BytesValue.of(1).isZero());
    assertFalse(BytesValue.of(1, 0, 0).isZero());
    assertFalse(BytesValue.of(0, 0, 1).isZero());
    assertFalse(BytesValue.of(0, 0, 1, 0, 0).isZero());
  }

  @Test
  public void findsCommonPrefix() {
    final BytesValue v = BytesValue.of(1, 2, 3, 4, 5, 6, 7);
    final BytesValue o = BytesValue.of(1, 2, 3, 4, 4, 3, 2);
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(BytesValue.of(1, 2, 3, 4));
  }

  @Test
  public void findsCommonPrefixOfShorter() {
    final BytesValue v = BytesValue.of(1, 2, 3, 4, 5, 6, 7);
    final BytesValue o = BytesValue.of(1, 2, 3, 4);
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(BytesValue.of(1, 2, 3, 4));
  }

  @Test
  public void findsCommonPrefixOfLonger() {
    final BytesValue v = BytesValue.of(1, 2, 3, 4);
    final BytesValue o = BytesValue.of(1, 2, 3, 4, 4, 3, 2);
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(BytesValue.of(1, 2, 3, 4));
  }

  @Test
  public void findsCommonPrefixOfSliced() {
    final BytesValue v = BytesValue.of(1, 2, 3, 4).slice(2, 2);
    final BytesValue o = BytesValue.of(3, 4, 3, 3, 2).slice(3, 2);
    assertThat(v.commonPrefixLength(o)).isEqualTo(1);
    assertThat(v.commonPrefix(o)).isEqualTo(BytesValue.of(3));
  }

  @Test
  public void slideToEnd() {
    assertThat(BytesValue.of(1, 2, 3, 4).slice(0)).isEqualTo(BytesValue.of(1, 2, 3, 4));
    assertThat(BytesValue.of(1, 2, 3, 4).slice(1)).isEqualTo(BytesValue.of(2, 3, 4));
    assertThat(BytesValue.of(1, 2, 3, 4).slice(2)).isEqualTo(BytesValue.of(3, 4));
    assertThat(BytesValue.of(1, 2, 3, 4).slice(3)).isEqualTo(BytesValue.of(4));
  }

  @Test
  public void slicePastEndReturnsEmpty() {
    assertThat(BytesValue.of(1, 2, 3, 4).slice(4)).isEqualTo(BytesValue.EMPTY);
    assertThat(BytesValue.of(1, 2, 3, 4).slice(5)).isEqualTo(BytesValue.EMPTY);
  }

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  @Test
  public void update() throws NoSuchAlgorithmException {
    // Digest the same byte array in 4 ways:
    //  1) directly from the array
    //  2) after wrapped using the update() method
    //  3) after wrapped and copied using the update() method
    //  4) after wrapped but getting the byte manually
    // and check all compute the same digest.
    final MessageDigest md1 = MessageDigest.getInstance("SHA-1");
    final MessageDigest md2 = MessageDigest.getInstance("SHA-1");
    final MessageDigest md3 = MessageDigest.getInstance("SHA-1");
    final MessageDigest md4 = MessageDigest.getInstance("SHA-1");

    final byte[] toDigest = new BigInteger("12324029423415041783577517238472017314").toByteArray();
    final BytesValue wrapped = wrap(toDigest);

    final byte[] digest1 = md1.digest(toDigest);

    wrapped.update(md2);
    final byte[] digest2 = md2.digest();

    wrapped.copy().update(md3);
    final byte[] digest3 = md3.digest();

    for (int i = 0; i < wrapped.size(); i++) md4.update(wrapped.get(i));
    final byte[] digest4 = md4.digest();

    assertArrayEquals(digest1, digest2);
    assertArrayEquals(digest1, digest3);
    assertArrayEquals(digest1, digest4);
  }

  @Test
  public void arrayExtraction() {
    // extractArray() and getArrayUnsafe() have essentially the same contract...
    assertArrayExtraction(BytesValue::extractArray);
    assertArrayExtraction(BytesValue::getArrayUnsafe);

    // But on top of the basic, extractArray() guarantees modifying the returned array is safe from
    // impacting the original value (not that getArrayUnsafe makes no guarantees here one way or
    // another, so there is nothing to test).
    final byte[] orig = new byte[] {1, 2, 3, 4};
    final BytesValue value = wrap(orig);
    final byte[] extracted = value.extractArray();
    assertArrayEquals(extracted, orig);
    Arrays.fill(extracted, (byte) -1);
    assertArrayEquals(new byte[] {-1, -1, -1, -1}, extracted);
    assertArrayEquals(new byte[] {1, 2, 3, 4}, orig);
    assertEquals(of(1, 2, 3, 4), value);
  }

  private void assertArrayExtraction(final Function<BytesValue, byte[]> extractor) {
    assertArrayEquals(new byte[0], extractor.apply(BytesValue.EMPTY));

    final byte[][] toTest =
        new byte[][] {new byte[] {1}, new byte[] {1, 2, 3, 4, 5, 6}, new byte[] {-1, -1, 0, -1}};
    for (final byte[] array : toTest) {
      assertArrayEquals(array, extractor.apply(wrap(array)));
    }

    // Test slightly more complex interactions
    assertArrayEquals(
        new byte[] {3, 4}, extractor.apply(wrap(new byte[] {1, 2, 3, 4, 5}).slice(2, 2)));
    assertArrayEquals(new byte[] {}, extractor.apply(wrap(new byte[] {1, 2, 3, 4, 5}).slice(2, 0)));
  }

  @Test
  public void asString() {
    assertEquals("0x", BytesValue.EMPTY.toString());

    assertEquals("0x01", of(1).toString());
    assertEquals("0x0aff03", of(0x0a, 0xff, 0x03).toString());
  }

  @Test
  public void testBytesValuesComparatorReturnsMatchUnsignedValueByteValue() {
    final BytesValue big = BytesValue.of(129);
    final BytesValue small = BytesValue.of(127);
    final BytesValue otherSmall = BytesValue.of(127);

    assertThat(big.compareTo(small)).isEqualTo(1);

    assertThat(small.compareTo(big)).isEqualTo(-1);

    assertThat(small.compareTo(otherSmall)).isEqualTo(0);
  }
}
