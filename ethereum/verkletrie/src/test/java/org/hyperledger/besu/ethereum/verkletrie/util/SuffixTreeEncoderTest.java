/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.verkletrie.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SuffixTreeEncoderTest {

  public static Stream<Arguments> valuesStart() {
    return Stream.of(
        Arguments.of(Bytes32.ZERO, Bytes32.ZERO),
        Arguments.of(
            Bytes.of(0xff),
            Bytes32.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000FF")),
        Arguments.of(
            Bytes32.leftPad(Bytes.of(0xff)),
            Bytes32.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000FF")),
        Arguments.of(
            Bytes.repeat((byte) 0xff, 12),
            Bytes32.fromHexString(
                "0x0000000000000000000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")),
        Arguments.of(
            Bytes.fromHexString("0xadef"),
            Bytes32.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000ADEF")),
        Arguments.of(
            Bytes.fromHexString("0x1123d3"),
            Bytes32.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000001123D3")));
  }

  public static Stream<Arguments> valuesMiddle() {
    return Stream.of(
        Arguments.of(Bytes32.ZERO, Bytes32.ZERO),
        Arguments.of(
            Bytes.of(0xff),
            Bytes32.fromHexString(
                "0x00000000000000000000000000000000FF000000000000000000000000000000")),
        Arguments.of(
            Bytes.repeat((byte) 0xff, 12),
            Bytes32.fromHexString(
                "0x00000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF00000000000000000000")),
        Arguments.of(
            Bytes.fromHexString("0xadef"),
            Bytes32.fromHexString(
                "0x000000000000000000000000000000ADEF000000000000000000000000000000")),
        Arguments.of(
            Bytes.fromHexString("0x1123d3"),
            Bytes32.fromHexString(
                "0x0000000000000000000000000000001123D30000000000000000000000000000")));
  }

  public static Stream<Arguments> valuesEnd() {
    return Stream.of(
        Arguments.of(Bytes32.ZERO, Bytes32.ZERO),
        Arguments.of(
            Bytes.repeat((byte) 0xff, 12),
            Bytes32.fromHexString(
                "0xFFFFFFFFFFFFFFFFFFFFFFFF0000000000000000000000000000000000000000")),
        Arguments.of(
            Bytes.of(0xff),
            Bytes32.fromHexString(
                "0xFF00000000000000000000000000000000000000000000000000000000000000")),
        Arguments.of(
            Bytes.fromHexString("0xadef"),
            Bytes32.fromHexString(
                "0xADEF000000000000000000000000000000000000000000000000000000000000")),
        Arguments.of(
            Bytes.fromHexString("0x1123d3"),
            Bytes32.fromHexString(
                "0x1123D30000000000000000000000000000000000000000000000000000000000")));
  }

  public static Stream<Arguments> valuesOutOfRange() {
    return Stream.of(
        Arguments.of(Bytes.repeat((byte) 0xff, 12), 25),
        Arguments.of(Bytes.of(0xff), 32),
        Arguments.of(Bytes.fromHexString("0xadef"), 31),
        Arguments.of(Bytes.fromHexString("0x1123d3"), 30));
  }

  @ParameterizedTest
  @MethodSource("valuesStart")
  void encodeBytesStart(final Bytes value, final Bytes32 expected) {
    assertEquals(expected, SuffixTreeEncoder.encodeIntoBasicDataLeaf(value, 0));
  }

  @ParameterizedTest
  @MethodSource("valuesMiddle")
  void encodeBytesMiddle(final Bytes value, final Bytes32 expected) {
    assertEquals(
        expected, SuffixTreeEncoder.encodeIntoBasicDataLeaf(value, (32 - value.size()) / 2));
  }

  @ParameterizedTest
  @MethodSource("valuesEnd")
  void encodeBytesEnd(final Bytes value, final Bytes32 expected) {
    assertEquals(expected, SuffixTreeEncoder.encodeIntoBasicDataLeaf(value, 32 - value.size()));
  }

  @ParameterizedTest
  @MethodSource("valuesOutOfRange")
  void encodeBytesOutsideRange(final Bytes value, final int byteShift) {
    assertThrows(
        IllegalArgumentException.class,
        () -> SuffixTreeEncoder.encodeIntoBasicDataLeaf(value, byteShift));
  }
}
