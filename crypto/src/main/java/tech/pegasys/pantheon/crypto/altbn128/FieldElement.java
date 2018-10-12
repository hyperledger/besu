package net.consensys.pantheon.crypto.altbn128;

import java.math.BigInteger;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
@SuppressWarnings("rawtypes")
public interface FieldElement<T extends FieldElement> {

  BigInteger FIELD_MODULUS =
      new BigInteger(
          "21888242871839275222246405745257275088696311157297823662689037894645226208583");

  boolean isValid();

  boolean isZero();

  T add(T other);

  T subtract(T other);

  T multiply(int val);

  T multiply(T other);

  T negate();

  T divide(T other);

  T power(int n);

  T power(BigInteger n);
}
