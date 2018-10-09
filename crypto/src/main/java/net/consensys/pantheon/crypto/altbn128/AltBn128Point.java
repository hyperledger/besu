package net.consensys.pantheon.crypto.altbn128;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Point extends AbstractFieldPoint<AltBn128Point> {

  static final Fq B = Fq.create(3);

  public static final AltBn128Point g1() {
    return new AltBn128Point(Fq.create(1), Fq.create(2));
  }

  static final AltBn128Point INFINITY = new AltBn128Point(Fq.zero(), Fq.zero());

  public AltBn128Point(final Fq x, final Fq y) {
    super(x, y);
  }

  public Fq getX() {
    return (Fq) x;
  }

  public Fq getY() {
    return (Fq) y;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public boolean isOnCurve() {
    if (!x.isValid() || !y.isValid()) {
      return false;
    }
    if (isInfinity()) {
      return true;
    }
    return y.power(2).subtract(x.power(3)).equals(B);
  }

  @Override
  protected AltBn128Point infinity() {
    return new AltBn128Point(Fq.zero(), Fq.zero());
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected AltBn128Point newInstance(final FieldElement x, final FieldElement y) {
    return new AltBn128Point((Fq) x, (Fq) y);
  }
}
