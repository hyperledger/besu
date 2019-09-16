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
package org.hyperledger.besu.util.uint;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.MutableBytes32;
import org.hyperledger.besu.util.uint.UInt256Bytes.BinaryLongOp;
import org.hyperledger.besu.util.uint.UInt256Bytes.BinaryOp;

import java.math.BigInteger;

import org.junit.Test;

public class UInt256BytesTest {

  private static String h(final String n) {
    return UInt256.of(new BigInteger(n)).toShortHexString();
  }

  @Test
  public void shiftLeft() {
    shiftLeft("0x01", 1, "0x02");
    shiftLeft("0x01", 2, "0x04");
    shiftLeft("0x01", 8, "0x0100");
    shiftLeft("0x01", 9, "0x0200");
    shiftLeft("0x01", 16, "0x10000");

    shiftLeft("0x00FF00", 4, "0x0FF000");
    shiftLeft("0x00FF00", 8, "0xFF0000");
    shiftLeft("0x00FF00", 1, "0x01FE00");
  }

  @Test
  public void shiftRight() {
    shiftRight("0x01", 1, "0x00");
    shiftRight("0x10", 1, "0x08");
    shiftRight("0x10", 2, "0x04");
    shiftRight("0x10", 8, "0x00");

    shiftRight("0x1000", 4, "0x0100");
    shiftRight("0x1000", 5, "0x0080");
    shiftRight("0x1000", 8, "0x0010");
    shiftRight("0x1000", 9, "0x0008");
    shiftRight("0x1000", 16, "0x0000");

    shiftRight("0x00FF00", 4, "0x000FF0");
    shiftRight("0x00FF00", 8, "0x0000FF");
    shiftRight("0x00FF00", 1, "0x007F80");
  }

  @Test
  public void longModulo() {
    longModulo(h("0"), 2, h("0"));
    longModulo(h("1"), 2, h("1"));
    longModulo(h("2"), 2, h("0"));
    longModulo(h("3"), 2, h("1"));

    longModulo(h("13492324908428420834234908342"), 2, h("0"));
    longModulo(h("13492324908428420834234908343"), 2, h("1"));

    longModulo(h("0"), 8, h("0"));
    longModulo(h("1"), 8, h("1"));
    longModulo(h("2"), 8, h("2"));
    longModulo(h("3"), 8, h("3"));
    longModulo(h("7"), 8, h("7"));
    longModulo(h("8"), 8, h("0"));
    longModulo(h("9"), 8, h("1"));

    longModulo(h("1024"), 8, h("0"));
    longModulo(h("1026"), 8, h("2"));

    longModulo(h("13492324908428420834234908342"), 8, h("6"));
    longModulo(h("13492324908428420834234908343"), 8, h("7"));
    longModulo(h("13492324908428420834234908344"), 8, h("0"));
  }

  @Test
  public void divide() {
    longDivide(h("0"), 2, h("0"));
    longDivide(h("1"), 2, h("0"));
    longDivide(h("2"), 2, h("1"));
    longDivide(h("3"), 2, h("1"));
    longDivide(h("4"), 2, h("2"));

    longDivide(h("13492324908428420834234908341"), 2, h("6746162454214210417117454170"));
    longDivide(h("13492324908428420834234908342"), 2, h("6746162454214210417117454171"));
    longDivide(h("13492324908428420834234908343"), 2, h("6746162454214210417117454171"));

    longDivide(h("2"), 8, h("0"));
    longDivide(h("7"), 8, h("0"));
    longDivide(h("8"), 8, h("1"));
    longDivide(h("9"), 8, h("1"));
    longDivide(h("17"), 8, h("2"));

    longDivide(h("1024"), 8, h("128"));
    longDivide(h("1026"), 8, h("128"));

    longDivide(h("13492324908428420834234908342"), 8, h("1686540613553552604279363542"));
    longDivide(h("13492324908428420834234908342"), 2048, h("6588049271693564860466263"));
    longDivide(h("13492324908428420834234908342"), 131072, h("102938269870211950944785"));
  }

  @Test
  public void multiply() {
    longMultiply(h("0"), 2, h("0"));
    longMultiply(h("1"), 2, h("2"));
    longMultiply(h("2"), 2, h("4"));
    longMultiply(h("3"), 2, h("6"));
    longMultiply(h("4"), 2, h("8"));

    longMultiply(h("10"), 18, h("180"));

    longMultiply(h("13492324908428420834234908341"), 2, h("26984649816856841668469816682"));
    longMultiply(h("13492324908428420834234908342"), 2, h("26984649816856841668469816684"));

    longMultiply(h("2"), 8, h("16"));
    longMultiply(h("7"), 8, h("56"));
    longMultiply(h("8"), 8, h("64"));
    longMultiply(h("17"), 8, h("136"));

    longMultiply(h("13492324908428420834234908342"), 8, h("107938599267427366673879266736"));
    longMultiply(h("13492324908428420834234908342"), 2048, h("27632281412461405868513092284416"));
    longMultiply(
        h("13492324908428420834234908342"), 131072, h("1768466010397529975584837906202624"));
  }

  @Test
  public void add() {
    longAdd(h("0"), 1, h("1"));
    longAdd(h("0"), 100, h("100"));
    longAdd(h("2"), 2, h("4"));
    longAdd(h("100"), 90, h("190"));

    longAdd(h("13492324908428420834234908342"), 10, h("13492324908428420834234908352"));
    longAdd(
        h("13492324908428420834234908342"), 23422141424214L, h("13492324908428444256376332556"));

    longAdd(h("1"), Long.MAX_VALUE, h(UInt256.of(2).pow(UInt256.of(63)).toString()));

    longAdd(
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        0,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"));
    longAdd(
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        10,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641369"));
  }

  @Test
  public void subtract() {
    longSubtract(h("1"), 0, h("1"));
    longSubtract(h("100"), 0, h("100"));
    longSubtract(h("4"), 2, h("2"));
    longSubtract(h("100"), 10, h("90"));
    longSubtract(h("1"), 1, h("0"));

    longSubtract(
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        0,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"));
    longSubtract(
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        10,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641349"));
  }

  @Test
  public void bitLength() {
    bitLength("0x", 0);
    bitLength("0x1", 1);
    bitLength("0x2", 2);
    bitLength("0x3", 2);
    bitLength("0xF", 4);
    bitLength("0x8F", 8);

    bitLength("0x100000000", 33);
  }

  @Test
  public void shortHexStrings() {
    assertThat(UInt256.of(0).toShortHexString()).isEqualTo("0x0");
    assertThat(UInt256.of(1).toShortHexString()).isEqualTo("0x1");
    assertThat(UInt256.fromHexString("0xdeadbeef").toShortHexString()).isEqualTo("0xdeadbeef");
    assertThat(
            UInt256.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000decafbad")
                .toShortHexString())
        .isEqualTo("0xdecafbad");
    assertThat(UInt256.fromHexString("cafebabe").toShortHexString()).isEqualTo("0xcafebabe");
    assertThat(UInt256.fromHexString("facefeed").toShortHexString()).isEqualTo("0xfacefeed");
  }

  @Test
  public void strictShortHexStrings() {
    assertThat(UInt256.of(0).toStrictShortHexString()).isEqualTo("0x00");
    assertThat(UInt256.of(1).toStrictShortHexString()).isEqualTo("0x01");
    assertThat(UInt256.fromHexString("0xdeadbeef").toStrictShortHexString())
        .isEqualTo("0xdeadbeef");
    assertThat(
            UInt256.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000decafbad")
                .toStrictShortHexString())
        .isEqualTo("0xdecafbad");
    assertThat(UInt256.fromHexString("cafebabe").toStrictShortHexString()).isEqualTo("0xcafebabe");
    assertThat(UInt256.fromHexString("facefeed").toStrictShortHexString()).isEqualTo("0xfacefeed");
    assertThat(UInt256.fromHexString("0xdedbeef").toStrictShortHexString()).isEqualTo("0x0dedbeef");
    assertThat(
            UInt256.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000dcafbad")
                .toStrictShortHexString())
        .isEqualTo("0x0dcafbad");
    assertThat(UInt256.fromHexString("cafebab").toStrictShortHexString()).isEqualTo("0x0cafebab");
    assertThat(UInt256.fromHexString("facefed").toStrictShortHexString()).isEqualTo("0x0facefed");
  }

  @Test
  public void fullHexStrings() {
    assertThat(UInt256.of(0).toHexString())
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    assertThat(UInt256.of(1).toHexString())
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000001");
    assertThat(UInt256.fromHexString("0xdeadbeef").toHexString())
        .isEqualTo("0x00000000000000000000000000000000000000000000000000000000deadbeef");
    assertThat(
            UInt256.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000decafbad")
                .toHexString())
        .isEqualTo("0x00000000000000000000000000000000000000000000000000000000decafbad");
    assertThat(UInt256.fromHexString("cafebabe").toHexString())
        .isEqualTo("0x00000000000000000000000000000000000000000000000000000000cafebabe");
    assertThat(UInt256.fromHexString("facefeed").toHexString())
        .isEqualTo("0x00000000000000000000000000000000000000000000000000000000facefeed");
  }

  private void bitLength(final String input, final int expectedLength) {
    assertThat(UInt256Bytes.bitLength(Bytes32.fromHexStringLenient(input)))
        .isEqualTo(expectedLength);
  }

  private void shiftLeft(final String input, final int shift, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(input);
    intOp(UInt256Bytes::shiftLeft, v, shift, expected);
  }

  private void shiftRight(final String input, final int shift, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(input);
    intOp(UInt256Bytes::shiftRight, v, shift, expected);
  }

  private void longModulo(final String input, final long modulo, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::modulo, UInt256Bytes::modulo, v, modulo, expected);
  }

  private void longDivide(final String input, final long divisor, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::divide, UInt256Bytes::divide, v, divisor, expected);
  }

  private void longMultiply(final String input, final long divisor, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::multiply, UInt256Bytes::multiply, v, divisor, expected);
  }

  private void longAdd(final String v1, final long v2, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(v1);
    longOp(UInt256Bytes::add, UInt256Bytes::add, v, v2, expected);
  }

  private void longSubtract(final String v1, final long v2, final String expected) {
    final Bytes32 v = Bytes32.fromHexStringLenient(v1);
    longOp(UInt256Bytes::subtract, UInt256Bytes::subtract, v, v2, expected);
  }

  interface BinaryIntOp {
    void applyOp(Bytes32 op1, int op2, MutableBytes32 result);
  }

  private void intOp(final BinaryIntOp op, final Bytes32 v1, final int v2, final String expected) {
    intOp(op, v1, v2, Bytes32.fromHexStringLenient(expected));
  }

  private void intOp(final BinaryIntOp op, final Bytes32 v1, final int v2, final Bytes32 expected) {
    // Note: we only use this for bit-related operations, so displaying as Hex on error.

    final MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    customHexDisplayAssertThat(expected, r1, true);

    // Also test in-place.
    final MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    customHexDisplayAssertThat(expected, r2, true);
  }

  private void longOp(
      final BinaryLongOp opLong,
      final BinaryOp op,
      final Bytes32 v1,
      final long v2,
      final String expected) {
    final Bytes32 result = Bytes32.fromHexStringLenient(expected);
    longOp(opLong, v1, v2, result);

    // Also test the Bytes32 variant to avoid tests duplication.
    op(op, v1, UInt256Bytes.of(v2), result);
  }

  private void longOp(
      final BinaryLongOp op, final Bytes32 v1, final long v2, final Bytes32 expected) {
    final MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    customHexDisplayAssertThat(expected, r1, false);

    // Also test in-place.
    final MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    customHexDisplayAssertThat(expected, r2, false);
  }

  private void op(final BinaryOp op, final Bytes32 v1, final Bytes32 v2, final Bytes32 expected) {
    final MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    customHexDisplayAssertThat(expected, r1, false);

    // Also test in-place.
    final MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    customHexDisplayAssertThat(expected, r2, false);
  }

  private void customHexDisplayAssertThat(
      final Bytes32 expected, final Bytes32 actual, final boolean displayAsHex) {
    var assertion = assertThat(actual);
    if (!displayAsHex) {
      assertion =
          assertion.withFailMessage(
              "Expected %s but got %s",
              UInt256Bytes.toString(expected), UInt256Bytes.toString(actual));
    }
    assertion.isEqualByComparingTo(expected);
  }
}
