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

import static org.junit.Assert.assertEquals;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;
import static tech.pegasys.pantheon.util.bytes.BytesValues.asSignedBigInteger;
import static tech.pegasys.pantheon.util.bytes.BytesValues.asUnsignedBigInteger;
import static tech.pegasys.pantheon.util.bytes.BytesValues.concatenate;
import static tech.pegasys.pantheon.util.bytes.BytesValues.extractInt;
import static tech.pegasys.pantheon.util.bytes.BytesValues.extractLong;
import static tech.pegasys.pantheon.util.bytes.BytesValues.ofUnsignedShort;
import static tech.pegasys.pantheon.util.bytes.BytesValues.toMinimalBytes;
import static tech.pegasys.pantheon.util.bytes.BytesValues.trimLeadingZeros;

import java.math.BigInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BytesValuesTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static BytesValue h(final String hex) {
    return fromHexString(hex);
  }

  private static BigInteger bi(final String decimal) {
    return new BigInteger(decimal);
  }

  @Test
  public void shouldTrimLeadingZeroes() {
    assertEquals(h("0x"), trimLeadingZeros(h("0x")));
    assertEquals(h("0x"), trimLeadingZeros(h("0x00")));
    assertEquals(h("0x"), trimLeadingZeros(h("0x00000000")));

    assertEquals(h("0x01"), trimLeadingZeros(h("0x01")));
    assertEquals(h("0x01"), trimLeadingZeros(h("0x00000001")));

    assertEquals(h("0x3010"), trimLeadingZeros(h("0x3010")));
    assertEquals(h("0x3010"), trimLeadingZeros(h("0x00003010")));

    assertEquals(h("0xFFFFFFFF"), trimLeadingZeros(h("0xFFFFFFFF")));
    assertEquals(h("0xFFFFFFFF"), trimLeadingZeros(h("0x000000000000FFFFFFFF")));
  }

  @Test
  public void minimalBytes() {
    assertEquals(h("0x"), toMinimalBytes(0));

    assertEquals(h("0x01"), toMinimalBytes(1));
    assertEquals(h("0x04"), toMinimalBytes(4));
    assertEquals(h("0x10"), toMinimalBytes(16));
    assertEquals(h("0xFF"), toMinimalBytes(255));

    assertEquals(h("0x0100"), toMinimalBytes(256));
    assertEquals(h("0x0200"), toMinimalBytes(512));

    assertEquals(h("0x010000"), toMinimalBytes(1L << 16));
    assertEquals(h("0x01000000"), toMinimalBytes(1L << 24));
    assertEquals(h("0x0100000000"), toMinimalBytes(1L << 32));
    assertEquals(h("0x010000000000"), toMinimalBytes(1L << 40));
    assertEquals(h("0x01000000000000"), toMinimalBytes(1L << 48));
    assertEquals(h("0x0100000000000000"), toMinimalBytes(1L << 56));
    assertEquals(h("0xFFFFFFFFFFFFFFFF"), toMinimalBytes(-1L));
  }

  @Test
  public void hexToInteger() {
    assertEquals(0, extractInt(h("0x")));
    assertEquals(0, extractInt(h("0x00")));
    assertEquals(0, extractInt(h("0x00000000")));

    assertEquals(1, extractInt(h("0x01")));
    assertEquals(1, extractInt(h("0x0001")));
    assertEquals(1, extractInt(h("0x000001")));
    assertEquals(1, extractInt(h("0x00000001")));

    assertEquals(256, extractInt(h("0x0100")));
    assertEquals(256, extractInt(h("0x000100")));
    assertEquals(256, extractInt(h("0x00000100")));

    assertEquals(-1, extractInt(h("0xFFFFFFFF")));
  }

  @Test
  public void hexToIntegerInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot extract an int");
    extractInt(h("0x0100000000"));
  }

  @Test
  public void hexToLong() {
    assertEquals(0L, extractLong(h("0x")));
    assertEquals(0L, extractLong(h("0x00")));
    assertEquals(0L, extractLong(h("0x00000000")));

    assertEquals(1L, extractLong(h("0x01")));
    assertEquals(1L, extractLong(h("0x0001")));
    assertEquals(1L, extractLong(h("0x000001")));
    assertEquals(1L, extractLong(h("0x00000001")));
    assertEquals(1L, extractLong(h("0x00000001")));
    assertEquals(1L, extractLong(h("0x0000000001")));
    assertEquals(1L, extractLong(h("0x000000000001")));

    assertEquals(256L, extractLong(h("0x0100")));
    assertEquals(256L, extractLong(h("0x000100")));
    assertEquals(256L, extractLong(h("0x00000100")));
    assertEquals(256L, extractLong(h("0x0000000100")));
    assertEquals(256L, extractLong(h("0x000000000100")));
    assertEquals(256L, extractLong(h("0x00000000000100")));
    assertEquals(256L, extractLong(h("0x0000000000000100")));

    assertEquals((1L << 32) - 1, extractLong(h("0xFFFFFFFF")));

    assertEquals(-1, extractLong(h("0xFFFFFFFFFFFFFFFF")));
  }

  @Test
  public void hexToLongInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot extract a long");
    extractLong(h("0x010000000000000000"));
  }

  @Test
  public void unsignedShort() {
    assertEquals(h("0x0000"), ofUnsignedShort(0));
    assertEquals(h("0x0001"), ofUnsignedShort(1));

    assertEquals(h("0x0100"), ofUnsignedShort(256));
    assertEquals(h("0xFFFF"), ofUnsignedShort(65535));
  }

  @Test
  public void unsignedShortNegative() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Value -1 cannot be represented as an unsigned short");
    ofUnsignedShort(-1);
  }

  @Test
  public void unsignedShortTooBig() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Value 65536 cannot be represented as an unsigned short");
    ofUnsignedShort(65536);
  }

  @Test
  public void hexConcatenate() {
    assertEquals(h("0x"), concatenate(h("0x"), h("0x")));

    assertEquals(h("0x1234"), concatenate(h("0x1234"), h("0x")));
    assertEquals(h("0x1234"), concatenate(h("0x"), h("0x1234")));

    assertEquals(h("0x12345678"), concatenate(h("0x1234"), h("0x5678")));

    final int valCount = 10;
    final BytesValue[] values = new BytesValue[valCount];
    final StringBuilder res = new StringBuilder();
    for (int i = 0; i < valCount; i++) {
      final String hex = "1234";
      values[i] = h(hex);
      res.append(hex);
    }
    assertEquals(h(res.toString()), concatenate(values));
  }

  @Test
  public void unsignedBigInteger() {
    assertEquals(bi("0"), asUnsignedBigInteger(BytesValue.EMPTY));
    assertEquals(bi("1"), asUnsignedBigInteger(BytesValue.of(1)));

    // Make sure things are interpreted unsigned.
    assertEquals(bi("255"), asUnsignedBigInteger(h("0xFF")));

    // Try 2^100 + Long.MAX_VALUE, as an easy to define a big not too special big integer.
    final BigInteger expected =
        BigInteger.valueOf(2).pow(100).add(BigInteger.valueOf(Long.MAX_VALUE));

    // 2^100 is a one followed by 100 zeros, that's 12 bytes of zeros (=96) plus 4 more zeros (so
    // 0x10 == 16).
    final MutableBytesValue v = MutableBytesValue.create(13);
    v.set(0, (byte) 16);
    v.setLong(v.size() - 8, Long.MAX_VALUE);
    assertEquals(expected, asUnsignedBigInteger(v));
  }

  @Test
  public void signedBigInteger() {
    assertEquals(bi("0"), asSignedBigInteger(BytesValue.EMPTY));
    assertEquals(bi("1"), asSignedBigInteger(BytesValue.of(1)));

    // Make sure things are interpreted signed.
    assertEquals(bi("-1"), asSignedBigInteger(h("0xFF")));

    // Try 2^100 + Long.MAX_VALUE, as an easy to define a big but not too special big integer.
    BigInteger expected = BigInteger.valueOf(2).pow(100).add(BigInteger.valueOf(Long.MAX_VALUE));

    // 2^100 is a one followed by 100 zeros, that's 12 bytes of zeros (=96) plus 4 more zeros (so
    // 0x10 == 16).
    MutableBytesValue v = MutableBytesValue.create(13);
    v.set(0, (byte) 16);
    v.setLong(v.size() - 8, Long.MAX_VALUE);
    assertEquals(expected, asSignedBigInteger(v));

    // And for a large negative one, we use -(2^100 + Long.MAX_VALUE), which is:
    //  2^100 + Long.MAX_VALUE = 0x10(4 bytes of 0)7F(  7 bytes of 1)
    //                 inverse = 0xEF(4 bytes of 1)80(  7 bytes of 0)
    //                      +1 = 0xEF(4 bytes of 1)80(6 bytes of 0)01
    expected = expected.negate();
    v = MutableBytesValue.create(13);
    v.set(0, (byte) 0xEF);
    for (int i = 1; i < 5; i++) v.set(i, (byte) 0xFF);
    v.set(5, (byte) 0x80);
    // 6 bytes of 0
    v.set(12, (byte) 1);
    assertEquals(expected, asSignedBigInteger(v));
  }
}
