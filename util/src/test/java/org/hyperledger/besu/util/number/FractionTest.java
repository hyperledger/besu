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
package org.hyperledger.besu.util.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

public class FractionTest {

  @Test
  public void assertThatAFractionCanBeBuiltFromAValidDouble() {
    final float value = 0.12f;
    Fraction fraction = Fraction.fromFloat(value);
    assertThat(fraction).isNotNull();
    assertThat(fraction.getValue()).isEqualTo(value);
  }

  @Test
  public void assertThatAFractionCannotBeBuiltFromAnInvalidDouble() {
    final Throwable thrown = catchThrowable(() -> Fraction.fromFloat(1.2f));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void assertThatAFractionCanBeBuiltFromAValidString() {
    final float value = 0.12f;
    Fraction fraction = Fraction.fromString(String.valueOf(value));
    assertThat(fraction).isNotNull();
    assertThat(fraction.getValue()).isEqualTo(value);
  }

  @Test
  public void assertThatAFractionCannotBeBuiltFromAnInvalidString() {
    final Throwable thrown = catchThrowable(() -> Fraction.fromString("not a fraction"));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void assertThatAFractionCanBeBuiltFromAValidPercentage() {
    Fraction fraction = Fraction.fromPercentage(Percentage.fromInt(12));
    assertThat(fraction).isNotNull();
    assertThat(fraction.getValue()).isEqualTo(0.12f);
  }
}
