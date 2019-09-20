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

import static org.hyperledger.besu.crypto.altbn128.AltBn128Fq12Point.twist;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class AltBn128Fq12Pairer {

  private static final int LOG_ATE_LOOP_COUNT = 63;

  private static final BigInteger ATE_LOOP_COUNT = new BigInteger("29793968203157093288");

  private static final BigInteger CURVE_ORDER =
      new BigInteger(
          "21888242871839275222246405745257275088548364400416034343698204186575808495617");

  public static Fq12 pair(final AltBn128Point p, final AltBn128Fq2Point q) {
    return millerLoop(cast(p), twist(q));
  }

  private static AltBn128Fq12Point cast(final AltBn128Point p) {
    final Fq[] newX = new Fq[Fq12.DEGREE];
    Arrays.fill(newX, Fq.zero());
    newX[0] = p.getX();
    final Fq[] newY = new Fq[Fq12.DEGREE];
    Arrays.fill(newY, Fq.zero());
    newY[0] = p.getY();

    return new AltBn128Fq12Point(new Fq12(newX), new Fq12(newY));
  }

  private static Fq12 millerLoop(final AltBn128Fq12Point p, final AltBn128Fq12Point q) {
    if (p.isInfinity() || q.isInfinity()) {
      return Fq12.one();
    }

    AltBn128Fq12Point r = q;
    Fq12 f = Fq12.one();
    for (int i = LOG_ATE_LOOP_COUNT; i >= 0; --i) {
      f = f.multiply(f).multiply(lineFunc(r, r, p));
      r = r.doub();
      if (ATE_LOOP_COUNT.testBit(i)) {
        f = f.multiply(lineFunc(r, q, p));
        r = r.add(q);
      }
    }

    final AltBn128Fq12Point q1 =
        new AltBn128Fq12Point(
            q.getX().power(FieldElement.FIELD_MODULUS), q.getY().power(FieldElement.FIELD_MODULUS));
    final AltBn128Fq12Point nQ2 =
        new AltBn128Fq12Point(
            q1.getX().power(FieldElement.FIELD_MODULUS),
            q1.getY().negate().power(FieldElement.FIELD_MODULUS));
    f = f.multiply(lineFunc(r, q1, p));
    r = r.add(q1);
    f = f.multiply(lineFunc(r, nQ2, p));

    return f;
  }

  public static Fq12 finalize(final Fq12 f) {
    return f.power(FieldElement.FIELD_MODULUS.pow(12).subtract(BigInteger.ONE).divide(CURVE_ORDER));
  }

  private static Fq12 lineFunc(
      final AltBn128Fq12Point p1, final AltBn128Fq12Point p2, final AltBn128Fq12Point t) {
    final Fq12 x1 = p1.getX();
    final Fq12 y1 = p1.getY();
    final Fq12 x2 = p2.getX();
    final Fq12 y2 = p2.getY();
    final Fq12 xT = t.getX();
    final Fq12 yT = t.getY();

    if (!x1.equals(x2)) {
      final Fq12 m = y2.subtract(y1).divide(x2.subtract(x1));
      final Fq12 result = m.multiply(xT.subtract(x1)).subtract(yT.subtract(y1));
      return result;
    } else if (y1.equals(y2)) {
      final Fq12 m = x1.power(2).multiply(3).divide(y1.multiply(2));
      final Fq12 result = m.multiply(xT.subtract(x1)).subtract(yT.subtract(y1));
      return result;
    } else {
      return xT.subtract(x1);
    }
  }
}
