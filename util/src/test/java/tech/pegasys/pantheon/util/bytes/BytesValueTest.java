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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexStringLenient;
import static tech.pegasys.pantheon.util.bytes.BytesValue.of;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrap;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrapBuffer;

import java.util.Arrays;

import io.vertx.core.buffer.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BytesValueTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static BytesValue h(final String hex) {
    return fromHexString(hex);
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

  private static void assertWrapSlice(final byte[] bytes, final int offset, final int length) {
    final BytesValue value = wrap(bytes, offset, length);
    assertEquals(length, value.size());
    assertArrayEquals(Arrays.copyOfRange(bytes, offset, offset + length), value.extractArray());
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
  public void hexStringLenientInvalidInput() {
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
}
