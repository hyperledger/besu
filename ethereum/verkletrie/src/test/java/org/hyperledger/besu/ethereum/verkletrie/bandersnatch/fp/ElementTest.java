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
package org.hyperledger.besu.ethereum.verkletrie.bandersnatch.fp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class ElementTest {

  @Test
  public void testIsZero() {
    assertThat(Element.ZERO.isZero()).isTrue();
    assertThat(Element.ONE.isZero()).isFalse();
    assertThat(new Element(UInt256.valueOf(42L)).isZero()).isFalse();
  }

  @Test
  public void testInverseZero() {
    Element elt = Element.ZERO;
    assertThat(elt.inverse()).isEqualTo(elt);
  }

  @Test
  public void testInverse42() {
    assertThat(new Element(UInt256.valueOf(42L)).inverse())
        .isEqualTo(
            new Element(
                UInt256.fromHexString(
                    "3fa8731a5789261bc0c32da675b6100c6bd8289edea72861d409c282e75503ba")));
  }

  @Test
  public void testInverseOverQ() {
    assertThat(new Element(Element.Q_MODULUS.value.add(1)).inverse())
        .isEqualTo(
            new Element(
                UInt256.fromHexString(
                    "0748d9d99f59ff1105d314967254398f2b6cedcb87925c23c999e990f3f29c6d")));
  }

  @Test
  public void testRandom() {
    assertThat(Element.random().value.lessOrEqualThan(Element.Q_MODULUS.value)).isTrue();
  }

  @Test
  public void testDivide() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d433339d80809a1d80253bda402fffe5bfcfffffffefffffff3"));
    Element y =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d753339d80809a1d7d553bda402fffe5c1cfffffffeffffffe9"));
    assertThat(x.divide(y))
        .isEqualTo(
            new Element(
                UInt256.fromHexString(
                    "42291ebda05409e31e85f7061384f4066381dbac31023c5f2609827a639d6ddc")));
  }

  @Test
  public void testMultiply() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d433339d80809a1d80253bda402fffe5bfcfffffffefffffff3"));
    Element y =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d753339d80809a1d7d553bda402fffe5c1cfffffffeffffffe9"));
    assertThat(x.multiply(y))
        .isEqualTo(
            new Element(
                UInt256.fromHexString(
                    "addbab1d634d4bbc1c11001562f72e2a24caa1b482c2f2038b3a8d46bf1416f")));
  }

  @Test
  public void testNeg() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "05f98ae63ff2eb86b466cc60a939dd4adaeed3599e3ad7a34694ff6dbf518a76"));
    Element result = x.neg();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "6df41c6ce9aa91c17ed30ba76067faba78ced0a961c3845bb96b009140ae758b"));
  }

  @Test
  public void testNegOverQ() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000002"));
    Element result = x.neg();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
  }

  @Test
  public void testNegZero() {
    Element x = Element.ZERO;
    Element result = x.neg();
    assertThat(result.value).isEqualTo(Element.ZERO.value);
  }

  @Test
  public void testNegOne() {
    Element x = new Element(UInt256.ONE);
    Element result = x.neg();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000000"));
  }

  @Test
  public void testFromMont() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000002"));
    Element result = x.fromMontgomery();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "1bbe869330009d577204078a4f77266aab6fca8f09dc705f13f75b69fe75c040"));
  }

  @Test
  public void testToMont() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000002"));
    Element result = x.toMontgomery();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "0x1824b159acc5056f998c4fefecbc4ff55884b7fa0003480200000001fffffffe"));
  }

  @Test
  public void testToMont2() {
    Element before =
        new Element(
            UInt256.valueOf(new BigInteger("999881962499998002", 10))
                .add(UInt256.valueOf(new BigInteger("0", 10)).shiftLeft(64))
                .add(UInt256.valueOf(new BigInteger("1", 10)).shiftLeft(128))
                .add(UInt256.valueOf(new BigInteger("0", 10)).shiftLeft(192)));
    Element expected =
        new Element(
            UInt256.valueOf(new BigInteger("479530773443910689", 10))
                .add(UInt256.valueOf(new BigInteger("6571075494204656015", 10)).shiftLeft(64))
                .add(UInt256.valueOf(new BigInteger("12714171422618877016", 10)).shiftLeft(128))
                .add(UInt256.valueOf(new BigInteger("1309675183017776880", 10)).shiftLeft(192)));
    assertThat(before.toMontgomery()).isEqualTo(expected);
  }

  //  @Test
  //  public void testToMont3() {
  //    Element before =
  //        new Element(
  //            UInt256.valueOf(new BigInteger("6121572481584493354", 10))
  //                .add(UInt256.valueOf(new BigInteger("12601925224297426022", 10)).shiftLeft(64))
  //                .add(UInt256.valueOf(new BigInteger("7716030069200636218", 10)).shiftLeft(128))
  //                .add(UInt256.valueOf(new BigInteger("1351674413095362254",
  // 10)).shiftLeft(192)));
  //    Element expected =
  //        new Element(
  //            UInt256.valueOf(new BigInteger("479530773443910689", 10))
  //                .add(UInt256.valueOf(new BigInteger("6571075494204656015", 10)).shiftLeft(64))
  //                .add(UInt256.valueOf(new BigInteger("12714171422618877016", 10)).shiftLeft(128))
  //                .add(UInt256.valueOf(new BigInteger("1309675183017776880",
  // 10)).shiftLeft(192)));
  //    assertThat(before.toMontgomery()).isEqualTo(expected);
  //  }

  @Test
  public void testSetBytes() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "0000000000000000000000000000000100000000000000000de04b58e8220132"));
    Element expected =
        new Element(
            UInt256.valueOf(new BigInteger("999881962499998002", 10))
                .add(UInt256.valueOf(new BigInteger("0", 10)).shiftLeft(64))
                .add(UInt256.valueOf(new BigInteger("1", 10)).shiftLeft(128))
                .add(UInt256.valueOf(new BigInteger("0", 10)).shiftLeft(192)));
    assertThat(x).isEqualTo(expected);
    Element result = x.toMontgomery();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "122ce6a3d6eb56f0b071cf8bda9efc585b312658d057c98f06a7a2b2a1fe1c21"));
  }
}
