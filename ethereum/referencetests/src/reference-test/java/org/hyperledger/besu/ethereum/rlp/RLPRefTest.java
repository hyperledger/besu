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
package org.hyperledger.besu.ethereum.rlp;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.assertj.core.api.Assertions;
import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Ethereum reference RLP tests. */
public class RLPRefTest {

  private static final String TEST_CONFIG_FILES = "RLPTests/rlptest.json";

  public static Stream<Arguments> getTestParametersForConfig() {
    return JsonTestParameters.create(RLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES).stream().map(params -> Arguments.of(params[0], params[1], params[2]));
  }

  @ParameterizedTest(name = "Name: {0}")
  @MethodSource("getTestParametersForConfig")
  public void encode(final String name, final RLPRefTestCaseSpec spec, final boolean runTest) {
    assumeTrue(runTest, "Test was blacklisted");
    Assertions.assertThat(RLPTestUtil.encode(spec.getIn())).isEqualTo(spec.getOut());
  }

  @ParameterizedTest(name = "Name: {0}")
  @MethodSource("getTestParametersForConfig")
  public void decode(final String name, final RLPRefTestCaseSpec spec, final boolean runTest) {
    assumeTrue(runTest, "Test was blacklisted");
    Assertions.assertThat(RLPTestUtil.decode(spec.getOut())).isEqualTo(spec.getIn());
  }
}
