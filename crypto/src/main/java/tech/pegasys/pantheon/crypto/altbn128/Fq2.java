package net.consensys.pantheon.crypto.altbn128;

import java.math.BigInteger;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class Fq2 extends AbstractFqp<Fq2> {

  private static final int DEGREE = 2;

  static final Fq2 zero() {
    return new Fq2(new Fq[] {Fq.zero(), Fq.zero()});
  }

  static final Fq2 one() {
    return new Fq2(new Fq[] {Fq.one(), Fq.zero()});
  }

  private static final Fq[] MODULUS_COEFFICIENTS = new Fq[] {Fq.create(1), Fq.create(0)};

  public static final Fq2 create(final long c0, final long c1) {
    return create(BigInteger.valueOf(c0), BigInteger.valueOf((c1)));
  }

  public static final Fq2 create(final BigInteger c0, final BigInteger c1) {
    return new Fq2(Fq.create(c0), Fq.create(c1));
  }

  private Fq2(final Fq... coefficients) {
    super(DEGREE, MODULUS_COEFFICIENTS, coefficients);
  }

  public static Fq2 b2() {
    final Fq2 numerator = create(3, 0);
    final Fq2 denominator = create(9, 1);
    return numerator.divide(denominator);
  }

  @Override
  protected Fq2 newInstance(final Fq[] coefficients) {
    return new Fq2(coefficients);
  }
}
