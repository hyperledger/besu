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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The Ethereum reference RLP tests. */
public class InvalidRLPRefTest {

  private static final String[] TEST_CONFIG_FILES = {
    // TODO: upstream these additional tests to the ethereum tests repo
    "org/hyperledger/besu/ethereum/rlp/invalidRLPTest.json", "RLPTests/invalidRLPTest.json"
  };

  public static Stream<Arguments> getTestParametersForConfig() {
    return JsonTestParameters.create(InvalidRLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES).stream().map(params -> Arguments.of(params[0], params[1], params[2]));
  }

  /** Test RLP decoding. */
  @ParameterizedTest(name = "Name: {0}")
  @MethodSource("getTestParametersForConfig")
  public void decode(final String name, final InvalidRLPRefTestCaseSpec spec, final boolean runTest) {
    assumeTrue(runTest, "Test was blacklisted");
    final Bytes rlp = spec.getRLP();
    assertThatThrownBy(() -> RLPTestUtil.decode(rlp)).isInstanceOf(RLPException.class);
  }
}
