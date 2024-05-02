/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsAndWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsWithoutWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithoutWithdrawalRequestsAndWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithoutWithdrawalRequestsWithWithdrawalRequestsRoot;

import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidator.ProhibitedWithdrawalRequests;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.WithdrawalRequestTestParameter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WithdrawalRequestValidatorTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateWithdrawalRequestParameter")
  public void validateWithdrawalRequestParameter(
      final String description,
      final Optional<List<WithdrawalRequest>> maybeExits,
      final boolean expectedValidity) {
    assertThat(new ProhibitedWithdrawalRequests().validateWithdrawalRequestParameter(maybeExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateWithdrawalRequestParameter() {
    return Stream.of(
        Arguments.of("Prohibited exits - validating empty exits", Optional.empty(), true),
        Arguments.of("Prohibited exits - validating present exits", Optional.of(List.of()), false));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateWithdrawalRequestsInBlockParamsForProhibited")
  public void validateWithdrawalRequestsInBlock_WhenProhibited(
      final WithdrawalRequestTestParameter param, final boolean expectedValidity) {
    assertThat(
            new ProhibitedWithdrawalRequests()
                .validateWithdrawalRequestsInBlock(param.block, param.expectedWithdrawalRequest))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateWithdrawalRequestsInBlockParamsForProhibited() {
    return Stream.of(
        Arguments.of(blockWithWithdrawalRequestsAndWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithWithdrawalRequestsWithoutWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithoutWithdrawalRequestsWithWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithoutWithdrawalRequestsAndWithdrawalRequestsRoot(), true));
  }

  @Test
  public void allowExitsShouldReturnFalse() {
    assertThat(new ProhibitedWithdrawalRequests().allowWithdrawalRequests()).isFalse();
  }
}
