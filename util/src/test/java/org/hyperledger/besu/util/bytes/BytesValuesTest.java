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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.util.bytes.BytesValue.fromHexString;
import static org.hyperledger.besu.util.bytes.BytesValues.asSignedBigInteger;
import static org.hyperledger.besu.util.bytes.BytesValues.asUnsignedBigInteger;
import static org.hyperledger.besu.util.bytes.BytesValues.concatenate;
import static org.hyperledger.besu.util.bytes.BytesValues.extractInt;
import static org.hyperledger.besu.util.bytes.BytesValues.extractLong;
import static org.hyperledger.besu.util.bytes.BytesValues.ofUnsignedShort;
import static org.hyperledger.besu.util.bytes.BytesValues.toMinimalBytes;
import static org.hyperledger.besu.util.bytes.BytesValues.trimLeadingZeros;

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
    assertThat(trimLeadingZeros(h("0x"))).isEqualTo(h("0x"));
    assertThat(trimLeadingZeros(h("0x00"))).isEqualTo(h("0x"));
    assertThat(trimLeadingZeros(h("0x00000000"))).isEqualTo(h("0x"));

    assertThat(trimLeadingZeros(h("0x01"))).isEqualTo(h("0x01"));
    assertThat(trimLeadingZeros(h("0x00000001"))).isEqualTo(h("0x01"));

    assertThat(trimLeadingZeros(h("0x3010"))).isEqualTo(h("0x3010"));
    assertThat(trimLeadingZeros(h("0x00003010"))).isEqualTo(h("0x3010"));

    assertThat(trimLeadingZeros(h("0xFFFFFFFF"))).isEqualTo(h("0xFFFFFFFF"));
    assertThat(trimLeadingZeros(h("0x000000000000FFFFFFFF"))).isEqualTo(h("0xFFFFFFFF"));
  }

  @Test
  public void minimalBytes() {
    assertThat(toMinimalBytes(0)).isEqualTo(h("0x"));

    assertThat(toMinimalBytes(1)).isEqualTo(h("0x01"));
    assertThat(toMinimalBytes(4)).isEqualTo(h("0x04"));
    assertThat(toMinimalBytes(16)).isEqualTo(h("0x10"));
    assertThat(toMinimalBytes(255)).isEqualTo(h("0xFF"));

    assertThat(toMinimalBytes(256)).isEqualTo(h("0x0100"));
    assertThat(toMinimalBytes(512)).isEqualTo(h("0x0200"));

    assertThat(toMinimalBytes(1L << 16)).isEqualTo(h("0x010000"));
    assertThat(toMinimalBytes(1L << 24)).isEqualTo(h("0x01000000"));
    assertThat(toMinimalBytes(1L << 32)).isEqualTo(h("0x0100000000"));
    assertThat(toMinimalBytes(1L << 40)).isEqualTo(h("0x010000000000"));
    assertThat(toMinimalBytes(1L << 48)).isEqualTo(h("0x01000000000000"));
    assertThat(toMinimalBytes(1L << 56)).isEqualTo(h("0x0100000000000000"));
    assertThat(toMinimalBytes(-1L)).isEqualTo(h("0xFFFFFFFFFFFFFFFF"));
  }

  @Test
  public void hexToInteger() {
    assertThat(extractInt(h("0x"))).isEqualTo(0);
    assertThat(extractInt(h("0x00"))).isEqualTo(0);
    assertThat(extractInt(h("0x00000000"))).isEqualTo(0);

    assertThat(extractInt(h("0x01"))).isEqualTo(1);
    assertThat(extractInt(h("0x0001"))).isEqualTo(1);
    assertThat(extractInt(h("0x000001"))).isEqualTo(1);
    assertThat(extractInt(h("0x00000001"))).isEqualTo(1);

    assertThat(extractInt(h("0x0100"))).isEqualTo(256);
    assertThat(extractInt(h("0x000100"))).isEqualTo(256);
    assertThat(extractInt(h("0x00000100"))).isEqualTo(256);

    assertThat(extractInt(h("0xFFFFFFFF"))).isEqualTo(-1);
  }

  @Test
  public void hexToIntegerInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot extract an int");
    extractInt(h("0x0100000000"));
  }

  @Test
  public void hexToLong() {
    assertThat(extractLong(h("0x"))).isEqualTo(0L);
    assertThat(extractLong(h("0x00"))).isEqualTo(0L);
    assertThat(extractLong(h("0x00000000"))).isEqualTo(0L);

    assertThat(extractLong(h("0x01"))).isEqualTo(1L);
    assertThat(extractLong(h("0x0001"))).isEqualTo(1L);
    assertThat(extractLong(h("0x000001"))).isEqualTo(1L);
    assertThat(extractLong(h("0x00000001"))).isEqualTo(1L);
    assertThat(extractLong(h("0x00000001"))).isEqualTo(1L);
    assertThat(extractLong(h("0x0000000001"))).isEqualTo(1L);
    assertThat(extractLong(h("0x000000000001"))).isEqualTo(1L);

    assertThat(extractLong(h("0x0100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x000100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x00000100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x0000000100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x000000000100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x00000000000100"))).isEqualTo(256L);
    assertThat(extractLong(h("0x0000000000000100"))).isEqualTo(256L);

    assertThat(extractLong(h("0xFFFFFFFF"))).isEqualTo((1L << 32) - 1);

    assertThat(extractLong(h("0xFFFFFFFFFFFFFFFF"))).isEqualTo(-1);
  }

  @Test
  public void hexToLongInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cannot extract a long");
    extractLong(h("0x010000000000000000"));
  }

  @Test
  public void unsignedShort() {
    assertThat(ofUnsignedShort(0)).isEqualTo(h("0x0000"));
    assertThat(ofUnsignedShort(1)).isEqualTo(h("0x0001"));

    assertThat(ofUnsignedShort(256)).isEqualTo(h("0x0100"));
    assertThat(ofUnsignedShort(65535)).isEqualTo(h("0xFFFF"));
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
    assertThat(concatenate(h("0x"), h("0x"))).isEqualTo(h("0x"));

    assertThat(concatenate(h("0x1234"), h("0x"))).isEqualTo(h("0x1234"));
    assertThat(concatenate(h("0x"), h("0x1234"))).isEqualTo(h("0x1234"));

    assertThat(concatenate(h("0x1234"), h("0x5678"))).isEqualTo(h("0x12345678"));

    final int valCount = 10;
    final BytesValue[] values = new BytesValue[valCount];
    final StringBuilder res = new StringBuilder();
    for (int i = 0; i < valCount; i++) {
      final String hex = "1234";
      values[i] = h(hex);
      res.append(hex);
    }
    assertThat(concatenate(values)).isEqualTo(h(res.toString()));
  }

  @Test
  public void unsignedBigInteger() {
    assertThat(asUnsignedBigInteger(BytesValue.EMPTY)).isEqualTo(bi("0"));
    assertThat(asUnsignedBigInteger(BytesValue.of(1))).isEqualTo(bi("1"));

    // Make sure things are interpreted unsigned.
    assertThat(asUnsignedBigInteger(h("0xFF"))).isEqualTo(bi("255"));

    // Try 2^100 + Long.MAX_VALUE, as an easy to define a big not too special big integer.
    final BigInteger expected =
        BigInteger.valueOf(2).pow(100).add(BigInteger.valueOf(Long.MAX_VALUE));

    // 2^100 is a one followed by 100 zeros, that's 12 bytes of zeros (=96) plus 4 more zeros (so
    // 0x10 == 16).
    final MutableBytesValue v = MutableBytesValue.create(13);
    v.set(0, (byte) 16);
    v.setLong(v.size() - 8, Long.MAX_VALUE);
    assertThat(asUnsignedBigInteger(v)).isEqualTo(expected);
  }

  @Test
  public void signedBigInteger() {
    assertThat(asSignedBigInteger(BytesValue.EMPTY)).isEqualTo(bi("0"));
    assertThat(asSignedBigInteger(BytesValue.of(1))).isEqualTo(bi("1"));

    // Make sure things are interpreted signed.
    assertThat(asSignedBigInteger(h("0xFF"))).isEqualTo(bi("-1"));

    // Try 2^100 + Long.MAX_VALUE, as an easy to define a big but not too special big integer.
    BigInteger expected = BigInteger.valueOf(2).pow(100).add(BigInteger.valueOf(Long.MAX_VALUE));

    // 2^100 is a one followed by 100 zeros, that's 12 bytes of zeros (=96) plus 4 more zeros (so
    // 0x10 == 16).
    MutableBytesValue v = MutableBytesValue.create(13);
    v.set(0, (byte) 16);
    v.setLong(v.size() - 8, Long.MAX_VALUE);
    assertThat(asSignedBigInteger(v)).isEqualTo(expected);

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
    assertThat(asSignedBigInteger(v)).isEqualTo(expected);
  }
}
