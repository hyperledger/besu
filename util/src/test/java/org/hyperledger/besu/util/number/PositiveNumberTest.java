/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class PositiveNumberTest {

  @Test
  public void shouldBuildPositiveNumberFromString() {
    final String positiveNumberString = "1";
    final int positiveNumberInt = 1;
    PositiveNumber positiveNumber = PositiveNumber.fromString(positiveNumberString);
    assertThat(positiveNumber).isNotNull();
    assertThat(positiveNumber.getValue()).isEqualTo(positiveNumberInt);
  }

  @Test
  public void shouldThrowOnDecimalValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("1.1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNonPositiveValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("0"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNegativeValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnEmptyValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNullValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
