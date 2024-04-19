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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsAndExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsWithoutExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithoutExitsAndExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithoutExitsWithExitsRoot;

import org.hyperledger.besu.ethereum.core.ValidatorExit;
import org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.ValidateExitTestParameter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ValidatorExitsValidatorTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateValidatorExitParameter")
  public void validateValidatorExitParameter(
      final String description,
      final Optional<List<ValidatorExit>> maybeExits,
      final boolean expectedValidity) {
    assertThat(
            new ValidatorExitsValidator.ProhibitedExits()
                .validateValidatorExitParameter(maybeExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateValidatorExitParameter() {
    return Stream.of(
        Arguments.of("Prohibited exits - validating empty exits", Optional.empty(), true),
        Arguments.of("Prohibited exits - validating present exits", Optional.of(List.of()), false));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateExitsInBlockParamsForProhibited")
  public void validateExitsInBlock_WhenProhibited(
      final ValidateExitTestParameter param, final boolean expectedValidity) {
    assertThat(
            new ValidatorExitsValidator.ProhibitedExits()
                .validateExitsInBlock(param.block, param.expectedExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateExitsInBlockParamsForProhibited() {
    return Stream.of(
        Arguments.of(blockWithExitsAndExitsRoot(), false),
        Arguments.of(blockWithExitsWithoutExitsRoot(), false),
        Arguments.of(blockWithoutExitsWithExitsRoot(), false),
        Arguments.of(blockWithoutExitsAndExitsRoot(), true));
  }

  @Test
  public void allowExitsShouldReturnFalse() {
    assertThat(new ValidatorExitsValidator.ProhibitedExits().allowValidatorExits()).isFalse();
  }
}
