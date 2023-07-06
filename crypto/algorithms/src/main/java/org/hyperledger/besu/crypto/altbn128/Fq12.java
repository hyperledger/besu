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

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class Fq12 extends AbstractFqp<Fq12> {

  /** The constant DEGREE. */
  public static final int DEGREE = 12;

  /**
   * Zero fq 12.
   *
   * @return the fq 12
   */
  static final Fq12 zero() {
    return create(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * One fq 12.
   *
   * @return the fq 12
   */
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

  /**
   * Create fq 12.
   *
   * @param c0 the c 0
   * @param c1 the c 1
   * @param c2 the c 2
   * @param c3 the c 3
   * @param c4 the c 4
   * @param c5 the c 5
   * @param c6 the c 6
   * @param c7 the c 7
   * @param c8 the c 8
   * @param c9 the c 9
   * @param c10 the c 10
   * @param c11 the c 11
   * @return the fq 12
   */
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

  /**
   * Instantiates a new Fq 12.
   *
   * @param coefficients the coefficients
   */
  protected Fq12(final Fq... coefficients) {
    super(DEGREE, MODULUS_COEFFICIENTS, coefficients);
  }

  @Override
  protected Fq12 newInstance(final Fq[] coefficients) {
    return new Fq12(coefficients);
  }
}
