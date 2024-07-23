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
import static org.hyperledger.besu.ethereum.mainnet.ConsolidationRequestValidatorTestFixtures.blockWithConsolidationRequestsAndWithdrawalRequestsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ConsolidationRequestValidatorTestFixtures.blockWithConsolidationRequestsMismatch;
import static org.hyperledger.besu.ethereum.mainnet.ConsolidationRequestValidatorTestFixtures.blockWithMoreThanMaximumConsolidationRequests;

import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.ConsolidationRequestValidatorTestFixtures.ConsolidationRequestTestParameter;
import org.hyperledger.besu.ethereum.mainnet.requests.ConsolidationRequestValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PragueConsolidationRequestValidatorTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateConsolidationRequestParameter")
  public void validateConsolidationRequestParameter(
      final String description,
      final Optional<List<Request>> maybeRequests,
      final boolean expectedValidity) {
    assertThat(new ConsolidationRequestValidator().validateParameter(maybeRequests))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateConsolidationRequestParameter() {
    return Stream.of(
        Arguments.of(
            "Allowed ConsolidationRequests - validating empty ConsolidationRequests",
            Optional.empty(),
            true),
        Arguments.of(
            "Allowed ConsolidationRequests - validating present ConsolidationRequests",
            Optional.of(List.of()),
            true));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateConsolidationRequestsInBlockParamsForPrague")
  public void validateConsolidationRequestsInBlock_WhenPrague(
      final ConsolidationRequestTestParameter param, final boolean expectedValidity) {
    assertThat(
            new ConsolidationRequestValidator()
                .validate(
                    param.block, new ArrayList<>(param.expectedConsolidationRequest), List.of()))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateConsolidationRequestsInBlockParamsForPrague() {
    return Stream.of(
        Arguments.of(blockWithConsolidationRequestsAndWithdrawalRequestsRoot(), true),
        Arguments.of(blockWithConsolidationRequestsMismatch(), false),
        Arguments.of(blockWithMoreThanMaximumConsolidationRequests(), false));
  }
}
