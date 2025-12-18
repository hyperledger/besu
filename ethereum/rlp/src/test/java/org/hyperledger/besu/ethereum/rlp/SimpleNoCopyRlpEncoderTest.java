/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.rlp;

import java.util.List;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleNoCopyRlpEncoderTest {

  private SimpleNoCopyRlpEncoder encoder;

  @BeforeEach
  public void beforeTest() {
    encoder = new SimpleNoCopyRlpEncoder();
  }

  @Test
  public void testEncodeByte0() {
    Bytes unencodedBytes = Bytes.of(0);
    Assertions.assertEquals("0x00", encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeByteUnder127() {
    Bytes unencodedBytes = Bytes.of(72);
    Assertions.assertEquals("0x48", encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeByte127() {
    Bytes unencodedBytes = Bytes.of(127);
    Assertions.assertEquals("0x7f", encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeMinLengthShortString() {
    Bytes unencodedBytes = Bytes.of(128);
    Assertions.assertEquals("0x8180", encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeShortString() {
    Bytes unencodedBytes = Bytes.of(128, 129, 130);
    Assertions.assertEquals("0x83808182", encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeMaxLengthShortString() {
    // unencoded bytes are 55 bytes long
    Bytes unencodedBytes =
        Bytes.fromHexString(
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbe");
    Bytes expectedEncoding =
        Bytes.fromHexString(
            "b7deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbe");
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeMinLengthLongString() {
    // unencoded bytes are 56 bytes long
    Bytes unencodedBytes =
        Bytes.fromHexString(
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    Bytes expectedEncoding =
        Bytes.fromHexString(
            "b838deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeLongString() {
    // unencoded bytes are 1024 bytes long
    Bytes unencodedBytes = Bytes.fromHexString("deadbeef".repeat(256));
    Bytes expectedEncoding = Bytes.fromHexString("b90400" + "deadbeef".repeat(256));
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encode(unencodedBytes).toHexString());
  }

  @Test
  public void testEncodeShortList() {
    List<Bytes> encodedItems =
        Stream.of(Bytes.of(1, 2, 3), Bytes.of(4, 5, 6)).map(encoder::encode).toList();
    Bytes expectedEncoding = Bytes.fromHexString("c88301020383040506");
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encodeList(encodedItems).toHexString());
  }

  @Test
  public void testEncodeMaxLengthShortList() {
    List<Bytes> encodedItems =
        Stream.of(Bytes.fromHexString("deadbeef".repeat(13)), Bytes.of(128))
            .map(encoder::encode)
            .toList();
    Bytes expectedEncoding = Bytes.fromHexString("f7b4" + "deadbeef".repeat(13) + "8180");
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encodeList(encodedItems).toHexString());
  }

  @Test
  public void testEncodeMinLengthLongList() {
    // encoded items length is 53 + 3 = 56
    List<Bytes> encodedItems =
        Stream.of(Bytes.fromHexString("deadbeef".repeat(13)), Bytes.of(128, 129))
            .map(encoder::encode)
            .toList();
    Bytes expectedEncoding = Bytes.fromHexString("f838b4" + "deadbeef".repeat(13) + "828081");
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encodeList(encodedItems).toHexString());
  }

  @Test
  public void testEncodeLongList() {
    // encoded items length is 53 + 3  + 542 = 598
    List<Bytes> encodedItems =
        Stream.of(
                Bytes.fromHexString("deadbeef".repeat(13)),
                Bytes.of(128, 129),
                Bytes.fromHexString("deadbeef".repeat(135)))
            .map(encoder::encode)
            .toList();
    Bytes expectedEncoding =
        Bytes.fromHexString(
            "f90257b4" + "deadbeef".repeat(13) + "828081b9021c" + "deadbeef".repeat(135));
    Assertions.assertEquals(
        expectedEncoding.toHexString(), encoder.encodeList(encodedItems).toHexString());
  }
}
