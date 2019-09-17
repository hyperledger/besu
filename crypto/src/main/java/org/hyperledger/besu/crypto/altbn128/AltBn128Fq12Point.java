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

import java.util.Arrays;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Fq12Point extends AbstractFieldPoint<AltBn128Fq12Point> {

  public static AltBn128Fq12Point g12() {
    return twist(AltBn128Fq2Point.g2());
  }

  public static AltBn128Fq12Point twist(final AltBn128Fq2Point p) {
    final Fq2 x = p.getX();
    final Fq2 y = p.getY();

    final Fq[] xCoeffs = x.getCoefficients();
    final Fq[] yCoeffs = y.getCoefficients();

    final Fq[] nX = new Fq[Fq12.DEGREE];
    Arrays.fill(nX, Fq.zero());
    nX[0] = xCoeffs[0].subtract(xCoeffs[1].multiply(9));
    nX[6] = xCoeffs[1];
    final Fq[] nY = new Fq[Fq12.DEGREE];
    Arrays.fill(nY, Fq.zero());
    nY[0] = yCoeffs[0].subtract(yCoeffs[1].multiply(9));
    nY[6] = yCoeffs[1];

    final Fq12 newX = new Fq12(nX);
    final Fq12 newY = new Fq12(nY);
    final Fq12 w = w();
    return new AltBn128Fq12Point(newX.multiply(w.power(2)), newY.multiply(w.power(3)));
  }

  private static Fq12 w() {
    return Fq12.create(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  public AltBn128Fq12Point(final Fq12 x, final Fq12 y) {
    super(x, y);
  }

  public Fq12 getX() {
    return (Fq12) x;
  }

  public Fq12 getY() {
    return (Fq12) y;
  }

  @Override
  protected AltBn128Fq12Point infinity() {
    return new AltBn128Fq12Point(Fq12.zero(), Fq12.zero());
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected AltBn128Fq12Point newInstance(final FieldElement x, final FieldElement y) {
    return new AltBn128Fq12Point((Fq12) x, (Fq12) y);
  }
}
