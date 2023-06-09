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

import org.junit.jupiter.api.Test;

/**
 * Adapted from the pc_ecc (Apache 2 License) implementation:
 * https://github.com/ethereum/py_ecc/blob/master/py_ecc/bn128/bn128_field_elements.py
 */
public class FqTest {

  @Test
  public void shouldBeValidWhenLessThanFieldModulus() {
    final Fq fq = Fq.create(FieldElement.FIELD_MODULUS.subtract(BigInteger.ONE));
    assertThat(fq.isValid()).isTrue();
  }

  @Test
  public void shouldBeInvalidWhenEqualToFieldModulus() {
    final Fq fq = Fq.create(FieldElement.FIELD_MODULUS);
    assertThat(fq.isValid()).isFalse();
  }

  @Test
  public void shouldBeInvalidWhenGreaterThanFieldModulus() {
    final Fq fq = Fq.create(FieldElement.FIELD_MODULUS.add(BigInteger.ONE));
    assertThat(fq.isValid()).isFalse();
  }

  @Test
  public void shouldBeAbleToAddNumbersWithoutOverflow() {
    final Fq a = Fq.create(1);
    final Fq b = Fq.create(1);
    final Fq c = a.add(b);
    assertThat(c).isEqualTo(Fq.create(2));
  }

  @Test
  public void shouldBeAbleToAddNumbersWithOverflow() {
    final Fq a = Fq.create(FieldElement.FIELD_MODULUS.subtract(BigInteger.ONE));
    final Fq b = Fq.create(2);
    final Fq c = a.add(b);
    assertThat(c).isEqualTo(Fq.one());
  }

  @Test
  public void shouldBeAbleToSubtractNumbers() {
    final Fq a = Fq.create(5);
    final Fq b = Fq.create(3);
    final Fq c = a.subtract(b);
    assertThat(c).isEqualTo(Fq.create(2));
  }

  @Test
  public void shouldBeAbleToMultiplyNumbersWithoutOverflow() {
    final Fq a = Fq.create(2);
    final Fq b = Fq.create(3);
    final Fq c = a.multiply(b);
    assertThat(c).isEqualTo(Fq.create(6));
  }

  @Test
  public void shouldBeAbleToMultiplyNumbersWithOverflow() {
    // FIELD_MODULUS is odd so (FIELD_MODULUS + 1) / 2 => FIELD_MODULUS / 2 + 1 (with int types).
    final Fq a =
        Fq.create(FieldElement.FIELD_MODULUS.add(BigInteger.ONE).divide(BigInteger.valueOf(2)));
    final Fq b = Fq.create(2);
    final Fq c = a.multiply(b);
    assertThat(c).isEqualTo(Fq.one());
  }

  @Test
  public void shouldNegatePositiveNumberToNegative() {
    final Fq a = Fq.create(1);
    assertThat(a.negate()).isEqualTo(Fq.create(-1));
  }

  @Test
  public void shouldNegateNegativeNumberToPositive() {
    final Fq a = Fq.create(-1);
    assertThat(a.negate()).isEqualTo(Fq.create(1));
  }

  @Test
  public void shouldBeProductWhenMultiplied() {
    final Fq fq2 = Fq.create(2);
    final Fq fq4 = Fq.create(4);
    assertThat(fq2.multiply(fq2)).isEqualTo(fq4);
  }

  @Test
  public void shouldBeALinearDivide() {
    final Fq fq2 = Fq.create(2);
    final Fq fq7 = Fq.create(7);
    final Fq fq9 = Fq.create(9);
    final Fq fq11 = Fq.create(11);
    assertThat(fq2.divide(fq7).add(fq9.divide(fq7))).isEqualTo(fq11.divide(fq7));
  }

  @Test
  public void shouldBeALinearMultiply() {
    final Fq fq2 = Fq.create(2);
    final Fq fq7 = Fq.create(7);
    final Fq fq9 = Fq.create(9);
    final Fq fq11 = Fq.create(11);
    assertThat(fq2.multiply(fq7).add(fq9.multiply(fq7))).isEqualTo(fq11.multiply(fq7));
  }

  @Test
  public void shouldEqualItselWhenRaisedToFieldModulus() {
    final Fq fq9 = Fq.create(9);
    assertThat(fq9.power(FieldElement.FIELD_MODULUS)).isEqualTo(fq9);
  }
}
