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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class Fq2Test {

  @Test
  public void shouldBeTheSumWhenAdded() {
    final Fq2 x = Fq2.create(1, 0);
    final Fq2 f = Fq2.create(1, 2);
    final Fq2 fpx = Fq2.create(2, 2);

    assertThat(x.add(f)).isEqualTo(fpx);
  }

  @Test
  public void shouldBeOneWhenPointIsDividedByItself() {
    final Fq2 f = Fq2.create(1, 2);
    final Fq2 one = Fq2.create(1, 0);

    assertThat(f.divide(f)).isEqualTo(one);
  }

  @Test
  public void shouldBeALinearDivide() {
    final Fq2 x = Fq2.create(1, 0);
    final Fq2 f = Fq2.create(1, 2);
    final Fq2 one = Fq2.create(1, 0);

    assertThat(one.divide(f).add(x.divide(f))).isEqualTo(one.add(x).divide(f));
  }

  @Test
  public void shouldBeALinearMultiply() {
    final Fq2 x = Fq2.create(1, 0);
    final Fq2 f = Fq2.create(1, 2);
    final Fq2 one = Fq2.create(1, 0);

    assertThat(one.multiply(f).add(x.multiply(f))).isEqualTo(one.add(x).multiply(f));
  }

  @Test
  public void shouldEqualOneWhenRaisedToFieldModulus() {
    final Fq2 x = Fq2.create(1, 0);
    final Fq2 one = Fq2.create(1, 0);

    assertThat(x.power(FieldElement.FIELD_MODULUS.pow(2).subtract(BigInteger.ONE))).isEqualTo(one);
  }
}
