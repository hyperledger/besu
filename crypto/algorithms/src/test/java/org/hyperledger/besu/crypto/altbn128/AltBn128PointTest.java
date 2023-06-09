/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.crypto.altbn128;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128PointTest {

  @Test
  public void shouldReturnEquivalentValueByAdditionAndDouble() {
    assertThat(AltBn128Point.g1().doub().add(AltBn128Point.g1()).add(AltBn128Point.g1()))
        .isEqualTo(AltBn128Point.g1().doub().doub());
  }

  @Test
  public void shouldReturnInequivalentValueOnIdentityVsDouble() {
    assertThat(AltBn128Point.g1().doub()).isNotEqualTo(AltBn128Point.g1());
  }

  @Test
  public void shouldReturnEqualityOfValueByEquivalentAdditionMultiplication() {
    assertThat(
            AltBn128Point.g1()
                .multiply(BigInteger.valueOf(9))
                .add(AltBn128Point.g1().multiply(BigInteger.valueOf(5))))
        .isEqualTo(
            AltBn128Point.g1()
                .multiply(BigInteger.valueOf(12))
                .add(AltBn128Point.g1().multiply(BigInteger.valueOf(2))));
  }

  @Test
  public void shouldReturnInfinityOnMultiplicationByCurveOrder() {
    final BigInteger curveOrder =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");
    assertThat(AltBn128Point.g1().multiply(curveOrder).isInfinity()).isTrue();
  }

  @Test
  public void shouldReturnTrueWhenValuesAreInfinityBigIntZero() {
    final AltBn128Point p = new AltBn128Point(Fq.create(0), Fq.create(0));
    assertThat(p.isInfinity()).isTrue();
  }

  @Test
  public void shouldReturnInfinityWhenAddingTwoInfinities() {
    final AltBn128Point p0 = AltBn128Point.INFINITY;
    final AltBn128Point p1 = AltBn128Point.INFINITY;
    assertThat(p0.add(p1).equals(AltBn128Point.INFINITY)).isTrue();
  }

  @Test
  public void shouldReturnTrueWhenEqual() {
    final AltBn128Point p0 = new AltBn128Point(Fq.create(3), Fq.create(4));
    final AltBn128Point p1 = new AltBn128Point(Fq.create(3), Fq.create(4));
    assertThat(p0.equals(p1)).isTrue();
  }

  @Test
  public void shouldReturnFalseWhenNotEqual() {
    final AltBn128Point p0 = new AltBn128Point(Fq.create(4), Fq.create(4));
    final AltBn128Point p1 = new AltBn128Point(Fq.create(3), Fq.create(4));
    assertThat(p0.equals(p1)).isFalse();
  }

  @Test
  public void shouldReturnIdentityWhenPointAddInfinity() {
    final AltBn128Point p0 = new AltBn128Point(Fq.create(1), Fq.create(2));
    final AltBn128Point p1 = new AltBn128Point(Fq.create(0), Fq.create(0));
    assertThat(p0.add(p1).equals(new AltBn128Point(Fq.create(1), Fq.create(2)))).isTrue();
  }

  @Test
  public void shouldReturnPointOnInfinityAddPoint() {
    final AltBn128Point p0 =
        new AltBn128Point(Fq.create(BigInteger.valueOf(0)), Fq.create(BigInteger.valueOf(0)));
    final AltBn128Point p1 =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));
    assertThat(p0.add(p1).equals(p1)).isTrue();
  }

  @Test
  public void shouldReturnTrueSumOnDoubling() {
    final AltBn128Point p0 =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));
    final AltBn128Point p1 =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));
    final Fq sumX =
        Fq.create(
            new BigInteger(
                "1368015179489954701390400359078579693043519447331113978918064868415326638035"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "9918110051302171585080402603319702774565515993150576347155970296011118125764"));
    assertThat(p0.add(p1).equals(new AltBn128Point(sumX, sumY))).isTrue();
  }

  @Test
  public void shouldReturnInfinityOnIdenticalInputPointValuesOfX() {
    final Fq p0x =
        Fq.create(
            new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145"));
    final Fq p0y =
        Fq.create(
            new BigInteger(
                "848677436511517736191562425154572367705380862894644942948681172815252343932"));
    final AltBn128Point p0 = new AltBn128Point(p0x, p0y);

    final Fq p1x =
        Fq.create(
            new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145"));
    final Fq p1y =
        Fq.create(
            new BigInteger(
                "21039565435327757486054843320102702720990930294403178719740356721829973864651"));
    final AltBn128Point p1 = new AltBn128Point(p1x, p1y);

    assertThat(p0.add(p1).equals(AltBn128Point.INFINITY)).isTrue();
  }

  @Test
  public void shouldReturnTrueAddAndComputeSlope() {
    final Fq p0x =
        Fq.create(
            new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145"));
    final Fq p0y =
        Fq.create(
            new BigInteger(
                "848677436511517736191562425154572367705380862894644942948681172815252343932"));
    final AltBn128Point p0 = new AltBn128Point(p0x, p0y);

    final Fq p1x =
        Fq.create(
            new BigInteger(
                "1624070059937464756887933993293429854168590106605707304006200119738501412969"));
    final Fq p1y =
        Fq.create(
            new BigInteger(
                "3269329550605213075043232856820720631601935657990457502777101397807070461336"));
    final AltBn128Point p1 = new AltBn128Point(p1x, p1y);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "9836339169314901400584090930519505895878753154116006108033708428907043344230"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "2085718088180884207082818799076507077917184375787335400014805976331012093279"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(p0.add(p1).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnInfinityWhenMultiplierIsInfinity() {
    final Fq px =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq py =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point p = new AltBn128Point(px, py);
    final BigInteger multiplier = BigInteger.ZERO;
    assertThat(p.multiply(multiplier).equals(AltBn128Point.INFINITY)).isTrue();
  }

  @Test
  public void shouldReturnTrueMultiplyScalarAndPoint() {
    final AltBn128Point multiplicand =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));
    final BigInteger multiplier =
        new BigInteger(
            "115792089237316195423570985008687907853269984665640564039457584007913129639935");

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "21415159568991615317144600033915305503576371596506956373206836402282692989778"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "8573070896319864868535933562264623076420652926303237982078693068147657243287"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnIdentityWhenMultipliedByScalarValueOne() {
    final Fq multiplicandX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq multiplicandY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point multiplicand = new AltBn128Point(multiplicandX, multiplicandY);

    final BigInteger multiplier = BigInteger.valueOf(1);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnTrueMultiplyPointByScalar() {
    final AltBn128Point multiplicand =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));

    final BigInteger multiplier = BigInteger.valueOf(9);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "1624070059937464756887933993293429854168590106605707304006200119738501412969"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "3269329550605213075043232856820720631601935657990457502777101397807070461336"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnInfinityMultiplyPointByFieldModulus() {
    final Fq multiplicandX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq multiplicandY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point multiplicand = new AltBn128Point(multiplicandX, multiplicandY);

    final BigInteger multiplier =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");

    assertThat(multiplicand.multiply(multiplier).equals(AltBn128Point.INFINITY)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_0() {
    final Fq multiplicandX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq multiplicandY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point multiplicand = new AltBn128Point(multiplicandX, multiplicandY);

    final BigInteger multiplier = BigInteger.valueOf(2);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "1735584146725871897168740753407579795109098319299249929076571979506257370192"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "6064265680718387101814183009970545962379849150498077125987052947023017993936"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_1() {
    final Fq multiplicandX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq multiplicandY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point multiplicand = new AltBn128Point(multiplicandX, multiplicandY);

    final BigInteger multiplier = BigInteger.valueOf(9);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "13447195743588318540108422034660542894354216867239950480700468911927695682420"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "20282243652944194694550455553589850678366346583698568858716117082144718267765"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_2() {
    final AltBn128Point multiplicand =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));

    final BigInteger multiplier =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495616");

    final Fq sumX = Fq.create(BigInteger.valueOf(1));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "21888242871839275222246405745257275088696311157297823662689037894645226208581"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_3() {
    final Fq multiplicandX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq multiplicandY =
        Fq.create(
            new BigInteger(
                "11843594000332171325303933275547366297934113019079887694534126289021216356598"));
    final AltBn128Point multiplicand = new AltBn128Point(multiplicandX, multiplicandY);

    final BigInteger multiplier =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495616");

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "11999875504842010600789954262886096740416429265635183817701593963271973497827"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "10044648871507103896942472469709908790762198138217935968154911605624009851985"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_4() {
    final AltBn128Point multiplicand =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));

    final BigInteger multiplier = new BigInteger("340282366920938463463374607431768211456");

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "8920802327774939509523725599419958131004060744305956036272850138837360588708"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "15515729996153051217274459095713198084165220977632053298080637275617709055542"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }

  @Test
  public void shouldReturnSumMultiplyPointByScalar_5() {
    final AltBn128Point multiplicand =
        new AltBn128Point(Fq.create(BigInteger.valueOf(1)), Fq.create(BigInteger.valueOf(2)));

    final BigInteger multiplier = BigInteger.valueOf(2);

    final Fq sumX =
        Fq.create(
            new BigInteger(
                "1368015179489954701390400359078579693043519447331113978918064868415326638035"));
    final Fq sumY =
        Fq.create(
            new BigInteger(
                "9918110051302171585080402603319702774565515993150576347155970296011118125764"));
    final AltBn128Point sum = new AltBn128Point(sumX, sumY);

    assertThat(multiplicand.multiply(multiplier).equals(sum)).isTrue();
  }
}
