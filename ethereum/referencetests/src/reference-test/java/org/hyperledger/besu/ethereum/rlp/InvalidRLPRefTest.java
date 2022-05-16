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
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The Ethereum reference RLP tests. */
@RunWith(Parameterized.class)
public class InvalidRLPRefTest {

  private static final String[] TEST_CONFIG_FILES = {
    // TODO: upstream these additional tests to the ethereum tests repo
    "org/hyperledger/besu/ethereum/rlp/invalidRLPTest.json", "RLPTests/invalidRLPTest.json"
  };

  private final InvalidRLPRefTestCaseSpec spec;

  public InvalidRLPRefTest(
      final String name, final InvalidRLPRefTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(InvalidRLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES);
  }

  /** Test RLP decoding. */
  @Test
  public void decode() throws Exception {
    final Bytes rlp = spec.getRLP();
    assertThatThrownBy(() -> RLPTestUtil.decode(rlp)).isInstanceOf(RLPException.class);
  }
}
