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
package org.hyperledger.besu.ethereum.verkletrie.bandersnatch.fr;

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
                    "2546c9a92adbd3384fa236bec5cec2a385f743ac23217035cd355920b19e79b")));
  }

  @Test
  public void testInverseOverQ() {
    assertThat(new Element(Element.Q_MODULUS.value.add(1)).inverse())
        .isEqualTo(
            new Element(
                UInt256.fromHexString(
                    "0ae793ddb14aec7daa9e6daec0055cea40fa7ca27fecb938dbb4f5d658db47cb")));
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
                    "14d7b4f7f22fb5688cab25906b04691b468a5397997285ab6ef862deae100957")));
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
                    "209ee057659143a392a0e135c23c3dee1f96f11ebf6151699193e4a29141e0fd")));
  }

  @Test
  public void testNeg() {
    Element x =
        new Element(
            UInt256.fromHexString(
                "0000000000000000000000000000000000000000000000000000000000000001"));
    Element result = x.neg();
    assertThat(result.value)
        .isEqualTo(
            UInt256.fromHexString(
                "1cfb69d4ca675f520cce760202687600ff8f87007419047174fd06b52876e7e0"));
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
                "a90dc281a0c9e209d9949df9f8c69dfbabd1e2fd741aa87274fd06b62876e7df"));
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
                "0x1cfb69d4ca675f520cce760202687600ff8f87007419047174fd06b52876e7e0"));
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
                "029dcd39374fa1ed499348e004ce8e397648170983b64e150042fce1ccb70b7d"));
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
                "ff2df0939978a11eb3730340bb816b35def26d563fd67125f62b5942d4903e8"));
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
            UInt256.fromHexString(
                "12c21cb79c889ece6b14d87776efc93aaee3083940486c6654f431bd0fa6732a"));
    assertThat(before.toMontgomery()).isEqualTo(expected);
  }

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
                "12c21cb79c889ece6b14d87776efc93aaee3083940486c6654f431bd0fa6732a"));
  }
}
