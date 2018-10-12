package tech.pegasys.pantheon.crypto.altbn128;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Fq12PointTest {

  @Test
  public void shouldProduceTheSameResultUsingAddsAndDoublings() {
    assertThat(
            AltBn128Fq12Point.g12()
                .doub()
                .add(AltBn128Fq12Point.g12())
                .add(AltBn128Fq12Point.g12()))
        .isEqualTo(AltBn128Fq12Point.g12().doub().doub());
  }

  @Test
  public void shouldNotEqualEachOtherWhenDiferentPoints() {
    assertThat(AltBn128Fq12Point.g12().doub()).isNotEqualTo(AltBn128Fq12Point.g12());
  }

  @Test
  public void shouldEqualEachOtherWhenImpartialFractionsAreTheSame() {
    assertThat(
            AltBn128Fq12Point.g12()
                .multiply(BigInteger.valueOf(9))
                .add(AltBn128Fq12Point.g12().multiply(BigInteger.valueOf(5))))
        .isEqualTo(
            AltBn128Fq12Point.g12()
                .multiply(BigInteger.valueOf(12))
                .add(AltBn128Fq12Point.g12().multiply(BigInteger.valueOf(2))));
  }

  @Test
  public void shouldBeInfinityWhenMultipliedByCurveOrder() {
    final BigInteger curveOrder =
        new BigInteger(
            "21888242871839275222246405745257275088548364400416034343698204186575808495617");

    assertThat(AltBn128Fq12Point.g12().multiply(curveOrder).isInfinity()).isTrue();
  }
}
