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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import com.google.common.io.BaseEncoding;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BytesValueImplementationsTest {

  private final BytesValueCreator creator;
  private final BytesValueSliceCreator sliceCreator;

  public BytesValueImplementationsTest(
      final String name,
      final BytesValueCreator creator,
      final BytesValueSliceCreator sliceCreator) {
    this.creator = creator;
    this.sliceCreator = sliceCreator;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "BytesValue.wrap()",
            (BytesValueCreator) BytesValue::wrap,
            (BytesValueSliceCreator) BytesValue::wrap
          },
          {
            "BytesValue.of()",
            (BytesValueCreator) BytesValue::of,
            // There isn't really an analogue of slice for of, so just slice it
            (BytesValueSliceCreator) (b, start, len) -> BytesValue.of(b).slice(start, len)
          },
          {
            "BytesValue.wrapBuffer() (Vertx Buffer)",
            (BytesValueCreator) (b) -> BytesValue.wrapBuffer(buffer(b)),
            (BytesValueSliceCreator) (b, start, len) -> BytesValue.wrapBuffer(buffer(b), start, len)
          },
          {
            "BytesValue.wrapBuffer() (Netty ByteBuf)",
            (BytesValueCreator) (b) -> BytesValue.wrapBuffer(byteBuf(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> BytesValue.wrapBuffer(byteBuf(b), start, len)
          },
          {
            "BytesValue.wrapBuffer() (nio ByteBuffer)",
            (BytesValueCreator) (b) -> BytesValue.wrapBuffer(byteBuffer(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> BytesValue.wrapBuffer(byteBuffer(b), start, len)
          },
          {
            "MutableBytesValue.wrap()",
            (BytesValueCreator) MutableBytesValue::wrap,
            (BytesValueSliceCreator) MutableBytesValue::wrap
          },
          {
            "MutableBytesValue.wrapBuffer() (Vertx Buffer)",
            (BytesValueCreator) (b) -> MutableBytesValue.wrapBuffer(buffer(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> MutableBytesValue.wrapBuffer(buffer(b), start, len)
          },
          {
            "MutableBytesValue.wrapBuffer() (Netty ByteBuf)",
            (BytesValueCreator) (b) -> MutableBytesValue.wrapBuffer(byteBuf(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> MutableBytesValue.wrapBuffer(byteBuf(b), start, len)
          },
          {
            "MutableBytesValue.wrapBuffer() (nio ByteBuffer)",
            (BytesValueCreator) (b) -> MutableBytesValue.wrapBuffer(byteBuffer(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> MutableBytesValue.wrapBuffer(byteBuffer(b), start, len)
          },
          {
            ArrayWrappingBytesValue.class.getSimpleName(),
            (BytesValueCreator) ArrayWrappingBytesValue::new,
            (BytesValueSliceCreator) ArrayWrappingBytesValue::new
          },
          {
            MutableArrayWrappingBytesValue.class.getSimpleName(),
            (BytesValueCreator) MutableArrayWrappingBytesValue::new,
            (BytesValueSliceCreator) MutableArrayWrappingBytesValue::new
          },
          {
            MutableBufferWrappingBytesValue.class.getSimpleName(),
            (BytesValueCreator) (b) -> new MutableBufferWrappingBytesValue(buffer(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> new MutableBufferWrappingBytesValue(buffer(b), start, len)
          },
          {
            MutableByteBufWrappingBytesValue.class.getSimpleName(),
            (BytesValueCreator) (b) -> new MutableByteBufWrappingBytesValue(byteBuf(b)),
            (BytesValueSliceCreator)
                (b, start, len) -> new MutableByteBufWrappingBytesValue(byteBuf(b), start, len),
          },
          {
            MutableByteBufferWrappingBytesValue.class.getSimpleName(),
            (BytesValueCreator) (b) -> new MutableByteBufferWrappingBytesValue(byteBuffer(b)),
            (BytesValueSliceCreator)
                (b, start, len) ->
                    new MutableByteBufferWrappingBytesValue(byteBuffer(b), start, len)
          }
        });
  }

  private static ByteBuffer byteBuffer(final byte[] bytes) {
    return ByteBuffer.wrap(bytes);
  }

  private static ByteBuf byteBuf(final byte[] bytes) {
    return Unpooled.copiedBuffer(bytes);
  }

  private static Buffer buffer(final byte[] bytes) {
    return Buffer.buffer(bytes);
  }

  private static Buffer hexToBuffer(final String hex) {
    return Buffer.buffer(fromHexString(hex).getArrayUnsafe());
  }

  private static ByteBuf hexToByteBuf(final String hex) {
    final byte[] bytes = fromHexString(hex).getArrayUnsafe();
    return Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length, Integer.MAX_VALUE))
        .writeBytes(bytes);
  }

  private BytesValue fromHex(final String hex) {
    String hexVal = hex;
    if (hex.substring(0, 2).equals("0x")) {
      hexVal = hex.substring((2));
    }
    byte[] bytes = BaseEncoding.base16().decode(hexVal);
    return creator.create(bytes);
  }

  @Test
  public void createInstance() {
    assertEquals(BytesValue.EMPTY, creator.create(new byte[0]));

    assertCreateInstance(new byte[10]);
    assertCreateInstance(new byte[] {1});
    assertCreateInstance(new byte[] {1, 2, 3, 4});
    assertCreateInstance(new byte[] {-1, 127, -128});
  }

  private void assertCreateInstance(final byte[] bytes) {
    final BytesValue value = creator.create(bytes);
    assertEquals(bytes.length, value.size());
    assertArrayEquals(bytes, value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void testWrapNull() {
    creator.create(null);
  }

  @Test
  public void createSlice() {
    assertEquals(BytesValue.EMPTY, sliceCreator.create(new byte[0], 0, 0));
    assertEquals(BytesValue.EMPTY, sliceCreator.create(new byte[] {1, 2, 3}, 0, 0));
    assertEquals(BytesValue.EMPTY, sliceCreator.create(new byte[] {1, 2, 3}, 2, 0));

    assertSliceCreated(new byte[] {1, 2, 3, 4}, 0, 4);
    assertSliceCreated(new byte[] {1, 2, 3, 4}, 0, 2);
    assertSliceCreated(new byte[] {1, 2, 3, 4}, 2, 1);
    assertSliceCreated(new byte[] {1, 2, 3, 4}, 2, 2);
  }

  private void assertSliceCreated(final byte[] bytes, final int offset, final int length) {
    final BytesValue value = sliceCreator.create(bytes, offset, length);
    assertEquals(length, value.size());
    assertArrayEquals(Arrays.copyOfRange(bytes, offset, offset + length), value.extractArray());
  }

  @Test(expected = NullPointerException.class)
  public void createSliceFromNull() {
    sliceCreator.create(null, 0, 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void createSliceNegativeOffset() {
    assertSliceCreated(new byte[] {1, 2, 3, 4}, -1, 4);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void createSliceOutOfBoundOffset() {
    assertSliceCreated(new byte[] {1, 2, 3, 4}, 5, 1);
  }

  @Test
  public void createSliceNegativeLength() {
    assertThatThrownBy(() -> assertSliceCreated(new byte[] {1, 2, 3, 4}, 0, -2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid negative length provided");
  }

  @Test
  public void createSliceTooBig() {
    assertThatThrownBy(() -> assertSliceCreated(new byte[] {1, 2, 3, 4}, 2, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Provided length 3 is too big");
  }

  @Test
  public void sizes() {
    assertEquals(0, creator.create(new byte[0]).size());
    assertEquals(1, creator.create(new byte[1]).size());
    assertEquals(10, creator.create(new byte[10]).size());
  }

  @Test
  public void gets() {
    final BytesValue v = creator.create(new byte[] {1, 2, 3, 4});
    assertEquals(1, v.get(0));
    assertEquals(2, v.get(1));
    assertEquals(3, v.get(2));
    assertEquals(4, v.get(3));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getNegativeIndex() {
    creator.create(new byte[] {1, 2, 3, 4}).get(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getOutOfBound() {
    creator.create(new byte[] {1, 2, 3, 4}).get(4);
  }

  @Test
  public void getInt() {
    final BytesValue value = creator.create(new byte[] {0, 0, 1, 0, -1, -1, -1, -1});

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
    creator.create(new byte[] {1, 2, 3, 4}).getInt(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getIntOutOfBound() {
    creator.create(new byte[] {1, 2, 3, 4}).getInt(4);
  }

  @Test
  public void getIntNotEnoughBytes() {
    assertThatThrownBy(() -> creator.create(new byte[] {1, 2, 3, 4}).getInt(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value of size 4 has not enough bytes to read a 4 bytes int from index 1");
  }

  @Test
  public void getLong() {
    final BytesValue value1 = creator.create(new byte[] {0, 0, 1, 0, -1, -1, -1, -1, 0, 0});
    // 0x00000100FFFFFFFF = (2^40) + (2^32) - 1 = 1103806595071
    assertEquals(1103806595071L, value1.getLong(0));
    // 0x 000100FFFFFFFF00 = (2^48) + (2^40) - 1 - 255 = 282574488338176
    assertEquals(282574488338176L, value1.getLong(1));

    final BytesValue value2 = creator.create(new byte[] {-1, -1, -1, -1, -1, -1, -1, -1});
    assertEquals(-1L, value2.getLong(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getLongNegativeIndex() {
    creator.create(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}).getLong(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getLongOutOfBound() {
    creator.create(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}).getLong(8);
  }

  @Test
  public void getLongNotEnoughBytes() {
    assertThatThrownBy(() -> creator.create(new byte[] {1, 2, 3, 4}).getLong(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value of size 4 has not enough bytes to read a 8 bytes long from index 0");
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void sliceNegativeOffset() {
    fromHex("0x012345").slice(-1, 2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void sliceOffsetOutOfBound() {
    fromHex("0x012345").slice(3, 2);
  }

  @Test
  public void sliceTooLong() {
    assertThatThrownBy(() -> fromHex("0x012345").slice(1, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Provided length 3 is too big: the value has size 3 and has only 2 bytes from 1");
  }

  @Test
  public void rangedSlice() {
    assertEquals(fromHex("0x"), fromHex("0x0123456789").slice(0, 0));
    assertEquals(fromHex("0x"), fromHex("0x0123456789").slice(2, 0));
    assertEquals(fromHex("0x01"), fromHex("0x0123456789").slice(0, 1));
    assertEquals(fromHex("0x0123"), fromHex("0x0123456789").slice(0, 2));

    assertEquals(fromHex("0x4567"), fromHex("0x0123456789").slice(2, 2));
    assertEquals(fromHex("0x23456789"), fromHex("0x0123456789").slice(1, 4));
  }

  @Test
  public void appending() {
    assertAppendTo(BytesValue.EMPTY, Buffer.buffer(), BytesValue.EMPTY);
    assertAppendTo(BytesValue.EMPTY, hexToBuffer("0x1234"), fromHex("0x1234"));
    assertAppendTo(fromHex("0x1234"), Buffer.buffer(), fromHex("0x1234"));
    assertAppendTo(fromHex("0x5678"), hexToBuffer("0x1234"), fromHex("0x12345678"));
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
    assertAppendToByteBuf(BytesValue.EMPTY, hexToByteBuf("0x1234"), fromHex("0x1234"));
    assertAppendToByteBuf(
        fromHex("0x1234"),
        Unpooled.unreleasableBuffer(Unpooled.buffer(bytes1.length, Integer.MAX_VALUE))
            .writeBytes(bytes1),
        fromHex("0x1234"));
    assertAppendToByteBuf(fromHex("0x5678"), hexToByteBuf("0x1234"), fromHex("0x12345678"));
  }

  private void assertAppendToByteBuf(
      final BytesValue toAppend, final ByteBuf buffer, final BytesValue expected) {
    toAppend.appendTo(buffer);
    final byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    assertEquals(expected, BytesValue.wrap(arr));
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
    final BytesValue wrapped = creator.create(toDigest);

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
  public void asString() {
    assertEquals("0x", BytesValue.EMPTY.toString());

    assertEquals("0x01", creator.create(new byte[] {1}).toString());
    assertEquals("0x0aff03", creator.create(new byte[] {0x0a, (byte) 0xff, 0x03}).toString());
  }

  @Test
  public void zero() {
    assertTrue(BytesValue.EMPTY.isZero());
    assertTrue(creator.create(new byte[] {0}).isZero());
    assertTrue(creator.create(new byte[] {0, 0, 0}).isZero());

    assertFalse(creator.create(new byte[] {1}).isZero());
    assertFalse(creator.create(new byte[] {1, 0, 0}).isZero());
    assertFalse(creator.create(new byte[] {0, 0, 1}).isZero());
    assertFalse(creator.create(new byte[] {0, 0, 1, 0, 0}).isZero());
  }

  @Test
  public void findsCommonPrefix() {
    final BytesValue v = creator.create(new byte[] {1, 2, 3, 4, 5, 6, 7});
    final BytesValue o = creator.create(new byte[] {1, 2, 3, 4, 4, 3, 2});
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(creator.create(new byte[] {1, 2, 3, 4}));
  }

  @Test
  public void findsCommonPrefixOfShorter() {
    final BytesValue v = creator.create(new byte[] {1, 2, 3, 4, 5, 6, 7});
    final BytesValue o = creator.create(new byte[] {1, 2, 3, 4});
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(creator.create(new byte[] {1, 2, 3, 4}));
  }

  @Test
  public void findsCommonPrefixOfLonger() {
    final BytesValue v = creator.create(new byte[] {1, 2, 3, 4});
    final BytesValue o = creator.create(new byte[] {1, 2, 3, 4, 4, 3, 2});
    assertThat(v.commonPrefixLength(o)).isEqualTo(4);
    assertThat(v.commonPrefix(o)).isEqualTo(creator.create(new byte[] {1, 2, 3, 4}));
  }

  @Test
  public void findsCommonPrefixOfSliced() {
    final BytesValue v = creator.create(new byte[] {1, 2, 3, 4}).slice(2, 2);
    final BytesValue o = creator.create(new byte[] {3, 4, 3, 3, 2}).slice(3, 2);
    assertThat(v.commonPrefixLength(o)).isEqualTo(1);
    assertThat(v.commonPrefix(o)).isEqualTo(creator.create(new byte[] {3}));
  }

  @Test
  public void slideToEnd() {
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(0))
        .isEqualTo(creator.create(new byte[] {1, 2, 3, 4}));
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(1))
        .isEqualTo(creator.create(new byte[] {2, 3, 4}));
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(2))
        .isEqualTo(creator.create(new byte[] {3, 4}));
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(3))
        .isEqualTo(creator.create(new byte[] {4}));
  }

  @Test
  public void slicePastEndReturnsEmpty() {
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(4)).isEqualTo(BytesValue.EMPTY);
    assertThat(creator.create(new byte[] {1, 2, 3, 4}).slice(5)).isEqualTo(BytesValue.EMPTY);
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
    final BytesValue value = creator.create(orig);
    final byte[] extracted = value.extractArray();
    assertArrayEquals(extracted, orig);
    Arrays.fill(extracted, (byte) -1);
    assertArrayEquals(new byte[] {-1, -1, -1, -1}, extracted);
    assertArrayEquals(new byte[] {1, 2, 3, 4}, orig);
    assertEquals(creator.create(new byte[] {1, 2, 3, 4}), value);
  }

  private void assertArrayExtraction(final Function<BytesValue, byte[]> extractor) {
    assertArrayEquals(new byte[0], extractor.apply(BytesValue.EMPTY));

    final byte[][] toTest =
        new byte[][] {new byte[] {1}, new byte[] {1, 2, 3, 4, 5, 6}, new byte[] {-1, -1, 0, -1}};
    for (final byte[] array : toTest) {
      assertArrayEquals(array, extractor.apply(creator.create(array)));
    }

    // Test slightly more complex interactions
    assertArrayEquals(
        new byte[] {3, 4}, extractor.apply(creator.create(new byte[] {1, 2, 3, 4, 5}).slice(2, 2)));
    assertArrayEquals(
        new byte[] {}, extractor.apply(creator.create(new byte[] {1, 2, 3, 4, 5}).slice(2, 0)));
  }

  @Test
  public void testBytesValuesComparatorReturnsMatchUnsignedValueByteValue() {
    final BytesValue big = creator.create(new byte[] {(byte) 129});
    final BytesValue small = creator.create(new byte[] {127});
    final BytesValue otherSmall = creator.create(new byte[] {127});

    assertThat(big.compareTo(small)).isEqualTo(1);

    assertThat(small.compareTo(big)).isEqualTo(-1);

    assertThat(small.compareTo(otherSmall)).isEqualTo(0);
  }

  @Test
  public void rangedMutableCopy() {
    final BytesValue v = fromHex("0x012345");
    final MutableBytesValue mutableCopy = v.mutableCopy();

    // Initially, copy must be equal.
    assertEquals(mutableCopy, v);

    // Upon modification, original should not have been modified.
    mutableCopy.set(0, (byte) -1);
    assertNotEquals(mutableCopy, v);
    assertEquals(fromHex("0x012345"), v);
    assertEquals(fromHex("0xFF2345"), mutableCopy);
  }

  @Test
  public void copying() {
    MutableBytesValue dest;

    // The follow does nothing, but simply making sure it doesn't throw.
    dest = MutableBytesValue.EMPTY;
    BytesValue.EMPTY.copyTo(dest);
    assertEquals(BytesValue.EMPTY, dest);

    dest = MutableBytesValue.create(1);
    creator.create(new byte[] {1}).copyTo(dest);
    assertEquals(fromHex("0x01"), dest);

    dest = MutableBytesValue.create(1);
    creator.create(new byte[] {10}).copyTo(dest);
    assertEquals(fromHex("0x0A"), dest);

    dest = MutableBytesValue.create(2);
    creator.create(new byte[] {(byte) 0xff, 0x03}).copyTo(dest);
    assertEquals(fromHex("0xFF03"), dest);

    dest = MutableBytesValue.create(4);
    creator.create(new byte[] {(byte) 0xff, 0x03}).copyTo(dest.mutableSlice(1, 2));
    assertEquals(fromHex("0x00FF0300"), dest);
  }

  @Test
  public void copyingToTooSmall() {
    assertThatThrownBy(
            () -> creator.create(new byte[] {1, 2, 3}).copyTo(MutableBytesValue.create(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot copy 3 bytes to destination of non-equal size 2");
  }

  @Test
  public void copyingToTooBig() {
    assertThatThrownBy(
            () -> creator.create(new byte[] {1, 2, 3}).copyTo(MutableBytesValue.create(4)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot copy 3 bytes to destination of non-equal size 4");
  }

  @Test
  public void copyingToWithOffset() {
    MutableBytesValue dest;

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    BytesValue.EMPTY.copyTo(dest, 0);
    assertEquals(fromHex("0x010203"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    creator.create(new byte[] {1}).copyTo(dest, 1);
    assertEquals(fromHex("0x010103"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    creator.create(new byte[] {2}).copyTo(dest, 0);
    assertEquals(fromHex("0x020203"), dest);

    dest = MutableBytesValue.wrap(new byte[] {1, 2, 3});
    creator.create(new byte[] {1, 1}).copyTo(dest, 1);
    assertEquals(fromHex("0x010101"), dest);

    dest = MutableBytesValue.create(4);
    creator.create(new byte[] {(byte) 0xff, 0x03}).copyTo(dest, 1);
    assertEquals(fromHex("0x00FF0300"), dest);
  }

  @Test
  public void copyingToWithOffsetTooSmall() {
    assertThatThrownBy(
            () -> creator.create(new byte[] {1, 2, 3}).copyTo(MutableBytesValue.create(4), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot copy 3 bytes, destination has only 2 bytes from index 2");
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void copyingToWithNegativeOffset() {
    creator.create(new byte[] {1, 2, 3}).copyTo(MutableBytesValue.create(10), -1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void copyingToWithOutOfBoundIndex() {
    creator.create(new byte[] {1, 2, 3}).copyTo(MutableBytesValue.create(10), 10);
  }

  @FunctionalInterface
  private interface BytesValueCreator {
    public BytesValue create(byte[] bytes);
  }

  @FunctionalInterface
  private interface BytesValueSliceCreator {
    public BytesValue create(byte[] bytes, int start, int length);
  }
}
