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

import java.math.BigInteger;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 *
 * @param <T> the type parameter
 */
@SuppressWarnings("rawtypes")
public interface FieldElement<T extends FieldElement> {

  /** The constant FIELD_MODULUS. */
  BigInteger FIELD_MODULUS =
      new BigInteger(
          "21888242871839275222246405745257275088696311157297823662689037894645226208583");

  /**
   * Is valid boolean.
   *
   * @return the boolean
   */
  boolean isValid();

  /**
   * Is zero boolean.
   *
   * @return the boolean
   */
  boolean isZero();

  /**
   * Add t.
   *
   * @param other the other
   * @return the t
   */
  T add(T other);

  /**
   * Subtract t.
   *
   * @param other the other
   * @return the t
   */
  T subtract(T other);

  /**
   * Multiply t.
   *
   * @param val the val
   * @return the t
   */
  T multiply(int val);

  /**
   * Multiply t.
   *
   * @param other the other
   * @return the t
   */
  T multiply(T other);

  /**
   * Negate t.
   *
   * @return the t
   */
  T negate();

  /**
   * Divide t.
   *
   * @param other the other
   * @return the t
   */
  T divide(T other);

  /**
   * Power t.
   *
   * @param n the n
   * @return the t
   */
  T power(int n);

  /**
   * Power t.
   *
   * @param n the n
   * @return the t
   */
  T power(BigInteger n);
}
