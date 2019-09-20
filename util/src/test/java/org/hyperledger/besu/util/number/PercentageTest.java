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

import org.junit.Test;

public class PercentageTest {

  @Test
  public void assertThatAPercentageCanBeBuiltFromAValidInt() {
    final int value = 12;
    Percentage percentage = Percentage.fromInt(value);
    assertThat(percentage).isNotNull();
    assertThat(percentage.getValue()).isEqualTo(value);
  }

  @Test
  public void assertThatAPercentageCannotBeBuiltFromAnInvalidInt() {
    final Throwable thrown = catchThrowable(() -> Percentage.fromInt(120));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void assertThatAPercentageCanBeBuiltFromAValidString() {
    final int value = 12;
    Percentage percentage = Percentage.fromString(String.valueOf(value));
    assertThat(percentage).isNotNull();
    assertThat(percentage.getValue()).isEqualTo(value);
  }

  @Test
  public void assertThatAPercentageCannotBeBuiltFromAnInvalidString() {
    final Throwable thrown = catchThrowable(() -> Percentage.fromString("not a percentage"));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }
}
