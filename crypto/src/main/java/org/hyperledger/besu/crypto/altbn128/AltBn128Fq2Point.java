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
 */
public class AltBn128Fq2Point extends AbstractFieldPoint<AltBn128Fq2Point> {
  private static final BigInteger CURVE_ORDER =
      new BigInteger(
          "21888242871839275222246405745257275088548364400416034343698204186575808495617", 10);

  public static AltBn128Fq2Point g2() {
    final Fq2 x =
        Fq2.create(
            new BigInteger(
                "10857046999023057135944570762232829481370756359578518086990519993285655852781"),
            new BigInteger(
                "11559732032986387107991004021392285783925812861821192530917403151452391805634"));
    final Fq2 y =
        Fq2.create(
            new BigInteger(
                "8495653923123431417604973247489272438418190587263600148770280649306958101930"),
            new BigInteger(
                "4082367875863433681332203403145435568316851327593401208105741076214120093531"));
    return new AltBn128Fq2Point(x, y);
  }

  public AltBn128Fq2Point(final Fq2 x, final Fq2 y) {
    super(x, y);
  }

  public Fq2 getX() {
    return (Fq2) x;
  }

  public Fq2 getY() {
    return (Fq2) y;
  }

  @Override
  protected AltBn128Fq2Point infinity() {
    return new AltBn128Fq2Point(Fq2.zero(), Fq2.zero());
  }

  public boolean isOnCurve() {
    if (!x.isValid() || !y.isValid()) {
      return false;
    }

    if (isInfinity()) {
      return true;
    }

    final Fq2 x = getX();
    final Fq2 y = getY();

    return y.power(2).subtract(x.power(3)).equals(Fq2.b2());
  }

  public boolean isInGroup() {
    return multiply(CURVE_ORDER).isInfinity();
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected AltBn128Fq2Point newInstance(final FieldElement x, final FieldElement y) {
    return new AltBn128Fq2Point((Fq2) x, (Fq2) y);
  }
}
