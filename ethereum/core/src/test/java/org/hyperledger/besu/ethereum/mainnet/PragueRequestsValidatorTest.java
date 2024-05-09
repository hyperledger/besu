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
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithMoreThanMaximumWithdrawalRequests;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsAndWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsMismatch;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsRootMismatch;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithWithdrawalRequestsWithoutWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithoutWithdrawalRequestsAndWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.blockWithoutWithdrawalRequestsWithWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.requests.MainnetRequestsValidator.pragueRequestsValidator;

import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestValidatorTestFixtures.WithdrawalRequestTestParameter;
import org.hyperledger.besu.ethereum.mainnet.requests.WithdrawalRequestValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PragueRequestsValidatorTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateWithdrawalRequestParameter")
  public void validateWithdrawalRequestParameter(
      final String description,
      final Optional<List<Request>> maybeWithdrawalRequests,
      final boolean expectedValidity) {
    assertThat(new WithdrawalRequestValidator().validateParameter(maybeWithdrawalRequests))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateWithdrawalRequestParameter() {
    return Stream.of(
        Arguments.of(
            "Allowed WithdrawalRequests - validating empty WithdrawalRequests",
            Optional.empty(),
            false),
        Arguments.of(
            "Allowed WithdrawalRequests - validating present WithdrawalRequests",
            Optional.of(List.of()),
            true));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateWithdrawalRequestsInBlockParamsForPrague")
  public void validateWithdrawalRequestsInBlock_WhenPrague(
      final WithdrawalRequestTestParameter param, final boolean expectedValidity) {
    assertThat(
            pragueRequestsValidator(null)
                .validate(param.block, new ArrayList<>(param.expectedWithdrawalRequest), List.of()))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateWithdrawalRequestsInBlockParamsForPrague() {
    return Stream.of(
        Arguments.of(blockWithWithdrawalRequestsAndWithdrawalRequestsRoot(), true),
        Arguments.of(blockWithWithdrawalRequestsWithoutWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithoutWithdrawalRequestsWithWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithoutWithdrawalRequestsAndWithdrawalRequestsRoot(), false),
        Arguments.of(blockWithWithdrawalRequestsRootMismatch(), false),
        Arguments.of(blockWithWithdrawalRequestsMismatch(), false),
        Arguments.of(blockWithMoreThanMaximumWithdrawalRequests(), false));
  }
}
