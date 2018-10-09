package net.consensys.pantheon.crypto.altbn128;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Fq2PointTest {

  @Test
  public void shouldProduceTheSameResultUsingAddsAndDoublings() {
    assertThat(AltBn128Fq2Point.g2().doub().add(AltBn128Fq2Point.g2()).add(AltBn128Fq2Point.g2()))
        .isEqualTo(AltBn128Fq2Point.g2().doub().doub());
  }

  @Test
  public void shouldNotEqualEachOtherWhenDiferentPoints() {
    assertThat(AltBn128Fq2Point.g2().doub()).isNotEqualTo(AltBn128Fq2Point.g2());
  }

  @Test
  public void shouldEqualEachOtherWhenImpartialFractionsAreTheSame() {
    assertThat(
            AltBn128Fq2Point.g2()
                .multiply(BigInteger.valueOf(9))
                .add(AltBn128Fq2Point.g2().multiply(BigInteger.valueOf(5))))
        .isEqualTo(
            AltBn128Fq2Point.g2()
                .multiply(BigInteger.valueOf(12))
                .add(AltBn128Fq2Point.g2().multiply(BigInteger.valueOf(2))));
  }

  @Test
  public void shouldBeInfinityWhenMultipliedByCurveOrder() {
    final BigInteger curveOrder =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");

    assertThat(AltBn128Fq2Point.g2().multiply(curveOrder).isInfinity()).isTrue();
  }

  @Test
  public void shouldNotBeInfinityWhenNotMultipliedByCurveOrder() {
    // assert not is_inf(multiply(g2(), 2 * field_modulus - curve_order))
    final BigInteger two = BigInteger.valueOf(2);
    final BigInteger curveOrder =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");
    final BigInteger factor = two.multiply(FieldElement.FIELD_MODULUS).subtract(curveOrder);

    assertThat(AltBn128Fq2Point.g2().multiply(factor).isInfinity()).isFalse();
  }
}
