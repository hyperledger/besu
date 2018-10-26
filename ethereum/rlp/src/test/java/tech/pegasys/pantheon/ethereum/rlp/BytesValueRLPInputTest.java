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
package tech.pegasys.pantheon.ethereum.rlp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class BytesValueRLPInputTest {

  private static BytesValue h(final String hex) {
    return BytesValue.fromHexString(hex);
  }

  private static String times(final String base, final int times) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) sb.append(base);
    return sb.toString();
  }

  @Test
  public void empty() {
    final RLPInput in = RLP.input(BytesValue.EMPTY);
    assertTrue(in.isDone());
  }

  @Test
  public void singleByte() {
    final RLPInput in = RLP.input(h("0x01"));
    assertFalse(in.isDone());
    assertEquals((byte) 1, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleByteLowerBoundary() {
    final RLPInput in = RLP.input(h("0x00"));
    assertFalse(in.isDone());
    assertEquals((byte) 0, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleByteUpperBoundary() {
    final RLPInput in = RLP.input(h("0x7f"));
    assertFalse(in.isDone());
    assertEquals((byte) 0x7f, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleShortElement() {
    final RLPInput in = RLP.input(h("0x81FF"));
    assertFalse(in.isDone());
    assertEquals((byte) 0xFF, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleBarelyShortElement() {
    final RLPInput in = RLP.input(h("0xb7" + times("2b", 55)));
    assertFalse(in.isDone());
    assertEquals(h(times("2b", 55)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleBarelyLongElement() {
    final RLPInput in = RLP.input(h("0xb838" + times("2b", 56)));
    assertFalse(in.isDone());
    assertEquals(h(times("2b", 56)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElement() {
    final RLPInput in = RLP.input(h("0xb908c1" + times("3c", 2241)));

    assertFalse(in.isDone());
    assertEquals(h(times("3c", 2241)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_1() {
    final RLPInput in = RLP.input(h("0xb8ff" + times("3c", 255)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 255)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_2() {
    final RLPInput in = RLP.input(h("0xb90100" + times("3c", 256)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 256)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_3() {
    final RLPInput in = RLP.input(h("0xb9ffff" + times("3c", 65535)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 65535)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_4() {
    final RLPInput in = RLP.input(h("0xba010000" + times("3c", 65536)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 65536)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_5() {
    final RLPInput in = RLP.input(h("0xbaffffff" + times("3c", 16777215)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 16777215)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElementBoundaryCase_6() {
    // A RLPx Frame can have a maximum length of 0xffffff, so boundary above this
    // will be not be real world scenarios.
    final RLPInput in = RLP.input(h("0xbb01000000" + times("3c", 16777216)));
    assertFalse(in.isDone());
    assertEquals(h(times("3c", 16777216)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void assertLongScalar() {
    // Scalar should be encoded as the minimal byte array representing the number. For 0, that means
    // the empty byte array, which is a short element of zero-length, so 0x80.
    assertLongScalar(0L, h("0x80"));

    assertLongScalar(1L, h("0x01"));
    assertLongScalar(15L, h("0x0F"));
    assertLongScalar(1024L, h("0x820400"));
  }

  @Test
  public void longScalar_NegativeLong() {
    BytesValue bytes = h("0x88FFFFFFFFFFFFFFFF");
    final RLPInput in = RLP.input(bytes);
    assertThatThrownBy(in::readLongScalar)
        .isInstanceOf(RLPException.class)
        .hasMessageStartingWith("long scalar -1 is not non-negative");
  }

  private void assertLongScalar(final long expected, final BytesValue toTest) {
    final RLPInput in = RLP.input(toTest);
    assertFalse(in.isDone());
    assertEquals(expected, in.readLongScalar());
    assertTrue(in.isDone());
  }

  @Test
  public void intScalar() {
    // Scalar should be encoded as the minimal byte array representing the number. For 0, that means
    // the empty byte array, which is a short element of zero-length, so 0x80.
    assertIntScalar(0, h("0x80"));

    assertIntScalar(1, h("0x01"));
    assertIntScalar(15, h("0x0F"));
    assertIntScalar(1024, h("0x820400"));
  }

  private void assertIntScalar(final int expected, final BytesValue toTest) {
    final RLPInput in = RLP.input(toTest);
    assertFalse(in.isDone());
    assertEquals(expected, in.readIntScalar());
    assertTrue(in.isDone());
  }

  @Test
  public void emptyList() {
    final RLPInput in = RLP.input(h("0xc0"));
    assertFalse(in.isDone());
    assertEquals(0, in.enterList());
    assertFalse(in.isDone());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleShortList() {
    final RLPInput in = RLP.input(h("0xc22c3b"));

    assertFalse(in.isDone());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleIntBeforeShortList() {
    final RLPInput in = RLP.input(h("0x02c22c3b"));

    assertFalse(in.isDone());
    assertEquals(2, in.readIntScalar());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleShortListUpperBoundary() {
    final RLPInput in = RLP.input(h("0xf7" + times("3c", 55)));
    assertFalse(in.isDone());
    assertEquals(55, in.enterList());
    for (int i = 0; i < 55; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListLowerBoundary() {
    final RLPInput in = RLP.input(h("0xf838" + times("3c", 56)));
    assertFalse(in.isDone());
    assertEquals(56, in.enterList());
    for (int i = 0; i < 56; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_1() {
    final RLPInput in = RLP.input(h("0xf8ff" + times("3c", 255)));
    assertFalse(in.isDone());
    assertEquals(255, in.enterList());
    for (int i = 0; i < 255; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_2() {
    final RLPInput in = RLP.input(h("0xf90100" + times("3c", 256)));
    assertFalse(in.isDone());
    assertEquals(256, in.enterList());
    for (int i = 0; i < 256; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_3() {
    final RLPInput in = RLP.input(h("0xf9ffff" + times("3c", 65535)));
    assertFalse(in.isDone());
    assertEquals(65535, in.enterList());
    for (int i = 0; i < 65535; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_4() {
    final RLPInput in = RLP.input(h("0xfa010000" + times("3c", 65536)));
    assertFalse(in.isDone());
    assertEquals(65536, in.enterList());
    for (int i = 0; i < 65536; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_5() {
    final RLPInput in = RLP.input(h("0xfaffffff" + times("3c", 16777215)));
    assertFalse(in.isDone());
    assertEquals(16777215, in.enterList());
    for (int i = 0; i < 16777215; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleLongListBoundaryCase_6() {
    // A RLPx Frame can have a maximum length of 0xffffff, so boundary above this
    // will be not be real world scenarios.
    final RLPInput in = RLP.input(h("0xfb01000000" + times("3c", 16777216)));
    assertFalse(in.isDone());
    assertEquals(16777216, in.enterList());
    for (int i = 0; i < 16777216; i++) {
      assertEquals((byte) 0x3c, in.readByte());
    }
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleListwithBytesValue() {
    final RLPInput in = RLP.input(h("0xc28180"));
    assertFalse(in.isDone());
    assertEquals(1, in.enterList());
    assertEquals(h("0x80"), in.readBytesValue());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleNestedList() {
    final RLPInput in = RLP.input(h("0xc52cc203123b"));

    assertFalse(in.isDone());
    assertEquals(3, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x03, in.readByte());
    assertEquals((byte) 0x12, in.readByte());
    in.leaveList();
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void readAsRlp() {
    // Test null value
    final BytesValue nullValue = h("0x80");
    final RLPInput nv = RLP.input(nullValue);
    assertEquals(nv.raw(), nv.readAsRlp().raw());
    nv.reset();
    assertTrue(nv.nextIsNull());
    assertTrue(nv.readAsRlp().nextIsNull());

    // Test empty list
    final BytesValue emptyList = h("0xc0");
    final RLPInput el = RLP.input(emptyList);
    assertEquals(emptyList, el.readAsRlp().raw());
    el.reset();
    assertEquals(0, el.readAsRlp().enterList());
    el.reset();
    assertEquals(0, el.enterList());

    final BytesValue nestedList =
        RLP.encode(
            out -> {
              out.startList();
              out.writeByte((byte) 0x01);
              out.writeByte((byte) 0x02);
              out.startList();
              out.writeByte((byte) 0x11);
              out.writeByte((byte) 0x12);
              out.startList();
              out.writeByte((byte) 0x21);
              out.writeByte((byte) 0x22);
              out.endList();
              out.endList();
              out.endList();
            });

    final RLPInput nl = RLP.input(nestedList);
    final RLPInput compare = nl.readAsRlp();
    assertEquals(nl.raw(), compare.raw());
    nl.reset();
    nl.enterList();
    nl.skipNext(); // 0x01

    // Read the next byte that's inside the list, extract it as raw RLP and assert it's its own
    // representation.
    assertEquals(h("0x02"), nl.readAsRlp().raw());
    // Extract the inner list.
    assertEquals(h("0xc51112c22122"), nl.readAsRlp().raw());
    // Reset
    nl.reset();
    nl.enterList();
    nl.skipNext();
    nl.skipNext();
    nl.enterList();
    nl.skipNext();
    nl.skipNext();

    // Assert on the inner list of depth 3.
    assertEquals(h("0xc22122"), nl.readAsRlp().raw());
  }

  @Test
  public void raw() {
    final BytesValue initial = h("0xc80102c51112c22122");
    final RLPInput in = RLP.input(initial);
    assertEquals(initial, in.raw());
  }

  @Test
  public void reset() {
    final RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    for (int i = 0; i < 100; i++) {
      assertEquals(3, in.enterList());
      assertEquals(0x01, in.readByte());
      assertEquals(0x02, in.readByte());
      assertEquals(3, in.enterList());
      assertEquals(0x11, in.readByte());
      assertEquals(0x12, in.readByte());
      assertEquals(2, in.enterList());
      assertEquals(0x21, in.readByte());
      assertEquals(0x22, in.readByte());
      in.reset();
    }
  }

  @Test
  public void ignoreListTail() {
    final RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    assertEquals(3, in.enterList());
    assertEquals(0x01, in.readByte());
    in.leaveList(true);
  }

  @Test
  public void leaveListEarly() {
    final RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    assertEquals(3, in.enterList());
    assertEquals(0x01, in.readByte());
    assertThatThrownBy(() -> in.leaveList(false))
        .isInstanceOf(RLPException.class)
        .hasMessageStartingWith("Not at the end of the current list");
  }

  @Test
  public void failsWhenPayloadSizeIsTruncated() {
    // The prefix B9 indicates this is a long value that requires 2 bytes to encode the payload size
    // Only 1 byte follows the prefix
    BytesValue bytes = h("0xB901");
    assertThatThrownBy(() -> RLP.input(bytes))
        .isInstanceOf(RLPException.class)
        .hasRootCauseInstanceOf(CorruptedRLPInputException.class)
        .hasMessageContaining(
            "value of size 2 has not enough bytes to read the 2 bytes payload size ");
  }

  @Test
  public void failsWhenPayloadSizeHasLeadingZeroes() {
    // Sanity check correctly encoded value: a byte string of 56 bytes, requiring 1 byte to encode
    // size 56
    final BytesValue correctBytes =
        h(
            "0xB8380102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738");
    assertEquals(
        RLP.input(correctBytes).readBytesValue(),
        h(
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738"));

    // Encode same value, but use 2 bytes to represent the size, and pad size value with leading
    // zeroes
    final BytesValue incorrectBytes =
        h(
            "0xB900380102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738");
    assertThatThrownBy(() -> RLP.input(incorrectBytes))
        .isInstanceOf(RLPException.class)
        .hasRootCauseInstanceOf(MalformedRLPInputException.class)
        .hasMessageContaining("size of payload has leading zeros");
  }

  @Test
  public void failsWhenShortByteStringEncodedAsLongByteString() {
    // Sanity check correctly encoded value: a byte string of 55 bytes encoded as short byte string
    final BytesValue correctBytes =
        h(
            "0xB70102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637");
    assertEquals(
        RLP.input(correctBytes).readBytesValue(),
        h(
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637"));

    // Encode same value using long format
    final BytesValue incorrectBytes =
        h(
            "0xB8370102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637");
    assertThatThrownBy(() -> RLP.input(incorrectBytes))
        .isInstanceOf(RLPException.class)
        .hasRootCauseInstanceOf(MalformedRLPInputException.class)
        .hasMessageContaining("written as a long item, but size 55 < 56 bytes");
  }
}
