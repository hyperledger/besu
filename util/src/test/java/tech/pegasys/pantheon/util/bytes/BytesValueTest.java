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
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexStringLenient;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrap;
import static tech.pegasys.pantheon.util.bytes.BytesValue.wrapBuffer;

import java.util.Arrays;

import io.vertx.core.buffer.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BytesValueTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  /** Checks that modifying a wrapped array modifies the value itself. */
  @Test
  public void wrapReflectsUpdates() {
    final byte[] bytes = new byte[] {1, 2, 3, 4, 5};
    final BytesValue value = wrap(bytes);

    assertThat(value.size()).isEqualTo(bytes.length);
    assertThat(value.extractArray()).containsExactly(bytes);

    bytes[1] = 127;
    bytes[3] = 127;

    assertThat(value.size()).isEqualTo(bytes.length);
    assertThat(value.extractArray()).containsExactly(bytes);
  }

  private static void assertWrapSlice(final byte[] bytes, final int offset, final int length) {
    final BytesValue value = wrap(bytes, offset, length);
    assertThat(value.size()).isEqualTo(length);
    assertThat(value.extractArray())
        .containsExactly(Arrays.copyOfRange(bytes, offset, offset + length));
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
    assertThat(wrapped).isEqualTo(copy);

    // Sanity check for copy(): modify within the wrapped slice and check the copy differs now.
    bytes[2] = 42;
    assertThat(wrapped).isNotEqualByComparingTo(copy);
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
    assertThat(Arrays.copyOfRange(res, 0, first.length)).containsExactly(first);
    assertThat(Arrays.copyOfRange(res, first.length, res.length)).containsExactly(second);
  }

  @Test
  public void concatenatedWrapReflectsUpdates() {
    final byte[] first = new byte[] {1, 2, 3};
    final byte[] second = new byte[] {4, 5};
    final byte[] expected1 = new byte[] {1, 2, 3, 4, 5};
    final BytesValue res = wrap(wrap(first), wrap(second));
    assertThat(res.extractArray()).containsExactly(expected1);

    first[1] = 42;
    second[0] = 42;
    final byte[] expected2 = new byte[] {1, 42, 3, 42, 5};
    assertThat(res.extractArray()).containsExactly(expected2);
  }

  /** Checks that modifying a wrapped buffer modifies the value itself. */
  @Test
  public void wrapBufferReflectsUpdates() {
    final Buffer buffer = Buffer.buffer(new byte[] {1, 2, 3, 4, 5});
    final BytesValue value = wrapBuffer(buffer);

    assertThat(value.size()).isEqualTo(buffer.length());
    assertThat(value.extractArray()).containsExactly(buffer.getBytes());

    buffer.setByte(1, (byte) 127);
    buffer.setByte(3, (byte) 127);

    assertThat(value.size()).isEqualTo(buffer.length());
    assertThat(value.extractArray()).containsExactly(buffer.getBytes());
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

    assertThat(value.size()).isEqualTo(2);
    assertThat(value.extractArray()).containsExactly(Arrays.copyOfRange(buffer.getBytes(), 2, 4));

    // Modify within the wrapped slice. This should have modified the wrapped value but not its copy
    buffer.setByte(2, (byte) 127);
    buffer.setByte(3, (byte) 127);

    assertThat(value.extractArray()).containsExactly(Arrays.copyOfRange(buffer.getBytes(), 2, 4));
    assertThat(copy.extractArray()).doesNotContain(Arrays.copyOfRange(buffer.getBytes(), 2, 4));

    final BytesValue newCopy = value.copy();

    // Modify the bytes outside of the wrapped slice and check this doesn't affect the value (that
    // it is still equal to the copy from before the updates)
    buffer.setByte(0, (byte) 127);
    assertThat(value).isEqualTo(newCopy);
  }

  @Test
  public void bytes() {
    assertThat(BytesValue.of().extractArray()).containsExactly(new byte[] {});
    assertThat(BytesValue.of((byte) 1, (byte) 2).extractArray()).containsExactly(new byte[] {1, 2});
    assertThat(BytesValue.of((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5).extractArray())
        .containsExactly(new byte[] {1, 2, 3, 4, 5});

    assertThat(BytesValue.of((byte) -1, (byte) 2, (byte) -3).extractArray())
        .containsExactly(new byte[] {-1, 2, -3});
  }

  @Test
  public void integers() {
    assertThat(BytesValue.of(1, 2).extractArray()).containsExactly(new byte[] {1, 2});
    assertThat(BytesValue.of(1, 2, 3, 4, 5).extractArray())
        .containsExactly(new byte[] {1, 2, 3, 4, 5});
    assertThat(BytesValue.of(0xff, 0x7f, 0x80).extractArray())
        .containsExactly(new byte[] {-1, 127, -128});
  }

  @Test
  public void integerTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("3th value 256 does not fit a byte");
    BytesValue.of(2, 3, 256);
  }

  @Test
  public void integerTooLow() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("2th value -1 does not fit a byte");
    BytesValue.of(2, -1, 3);
  }

  @Test
  public void hexStringLenient() {
    assertThat(fromHexStringLenient("")).isEqualByComparingTo(BytesValue.of());
    assertThat(fromHexStringLenient("0x")).isEqualByComparingTo(BytesValue.of());

    assertThat(fromHexStringLenient("0")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexStringLenient("0x0")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexStringLenient("00")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexStringLenient("0x00")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexStringLenient("0x1")).isEqualByComparingTo(BytesValue.of(1));
    assertThat(fromHexStringLenient("0x01")).isEqualByComparingTo(BytesValue.of(1));

    assertThat(fromHexStringLenient("1FF2A")).isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1FF2A"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1ff2a"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1fF2a"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("01FF2A"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x01FF2A"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x01ff2A"))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
  }

  @Test
  public void hexStringLenientInvalidInput() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Illegal character 'o' found at index 1");
    fromHexStringLenient("foo");
  }

  @Test
  public void hexStringLenientLeftPadding() {
    assertThat(fromHexStringLenient("", 0)).isEqualByComparingTo(BytesValue.of());
    assertThat(fromHexStringLenient("", 1)).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexStringLenient("", 2)).isEqualByComparingTo(BytesValue.of(0, 0));
    assertThat(fromHexStringLenient("0x", 2)).isEqualByComparingTo(BytesValue.of(0, 0));

    assertThat(fromHexStringLenient("0", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 0));
    assertThat(fromHexStringLenient("0x0", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 0));
    assertThat(fromHexStringLenient("00", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 0));
    assertThat(fromHexStringLenient("0x00", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 0));
    assertThat(fromHexStringLenient("0x1", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 1));
    assertThat(fromHexStringLenient("0x01", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 1));

    assertThat(fromHexStringLenient("1FF2A", 3))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1FF2A", 4))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1ff2a", 5))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x1fF2a", 4))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("01FF2A", 4))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x01FF2A", 3))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexStringLenient("0x01ff2A", 3))
        .isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
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
    assertThat(fromHexString("0x")).isEqualByComparingTo(BytesValue.of());

    assertThat(fromHexString("00")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexString("0x00")).isEqualByComparingTo(BytesValue.of(0));
    assertThat(fromHexString("0x01")).isEqualByComparingTo(BytesValue.of(1));

    assertThat(fromHexString("01FF2A")).isEqualByComparingTo(BytesValue.of(1, 0xff, 0x2a));
    assertThat(fromHexString("0x01FF2A")).isEqualByComparingTo(BytesValue.of(1, 0xff, 0x2a));
    assertThat(fromHexString("0x01ff2a")).isEqualByComparingTo(BytesValue.of(1, 0xff, 0x2a));
    assertThat(fromHexString("0x01fF2a")).isEqualByComparingTo(BytesValue.of(1, 0xff, 0x2a));
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
    assertThat(fromHexString("0x", 0)).isEqualByComparingTo(BytesValue.of());
    assertThat(fromHexString("0x", 2)).isEqualByComparingTo(BytesValue.of(0, 0));
    assertThat(fromHexString("0x", 4)).isEqualByComparingTo(BytesValue.of(0, 0, 0, 0));

    assertThat(fromHexString("00", 2)).isEqualByComparingTo(BytesValue.of(0, 0));
    assertThat(fromHexString("0x00", 2)).isEqualByComparingTo(BytesValue.of(0, 0));
    assertThat(fromHexString("0x01", 3)).isEqualByComparingTo(BytesValue.of(0, 0, 1));

    assertThat(fromHexString("01FF2A", 4))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexString("0x01FF2A", 3)).isEqualByComparingTo(BytesValue.of(0x01, 0xff, 0x2a));
    assertThat(fromHexString("0x01ff2a", 5))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x00, 0x01, 0xff, 0x2a));
    assertThat(fromHexString("0x01fF2a", 5))
        .isEqualByComparingTo(BytesValue.of(0x00, 0x00, 0x01, 0xff, 0x2a));
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
