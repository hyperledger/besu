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
