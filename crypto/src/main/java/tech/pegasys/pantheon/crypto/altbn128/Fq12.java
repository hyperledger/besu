package net.consensys.pantheon.crypto.altbn128;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class Fq12 extends AbstractFqp<Fq12> {

  public static final int DEGREE = 12;

  static final Fq12 zero() {
    return create(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  public static final Fq12 one() {
    return create(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private static final Fq[] MODULUS_COEFFICIENTS =
      new Fq[] {
        Fq.create(82),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0),
        Fq.create(-18),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0),
        Fq.create(0)
      };

  public static Fq12 create(
      final long c0,
      final long c1,
      final long c2,
      final long c3,
      final long c4,
      final long c5,
      final long c6,
      final long c7,
      final long c8,
      final long c9,
      final long c10,
      final long c11) {
    return new Fq12(
        Fq.create(c0),
        Fq.create(c1),
        Fq.create(c2),
        Fq.create(c3),
        Fq.create(c4),
        Fq.create(c5),
        Fq.create(c6),
        Fq.create(c7),
        Fq.create(c8),
        Fq.create(c9),
        Fq.create(c10),
        Fq.create(c11));
  }

  protected Fq12(final Fq... coefficients) {
    super(DEGREE, MODULUS_COEFFICIENTS, coefficients);
  }

  @Override
  protected Fq12 newInstance(final Fq[] coefficients) {
    return new Fq12(coefficients);
  }
}
