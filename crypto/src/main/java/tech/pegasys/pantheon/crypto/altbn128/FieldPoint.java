package net.consensys.pantheon.crypto.altbn128;

import java.math.BigInteger;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
@SuppressWarnings("rawtypes")
public interface FieldPoint<T extends FieldPoint> {

  boolean isInfinity();

  T add(T other);

  T multiply(T other);

  T multiply(BigInteger n);

  T doub();

  T negate();
}
