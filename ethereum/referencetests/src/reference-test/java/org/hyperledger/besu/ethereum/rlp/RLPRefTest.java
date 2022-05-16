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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.assertj.core.api.Assertions;
import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The Ethereum reference RLP tests. */
@RunWith(Parameterized.class)
public class RLPRefTest {

  private static final String TEST_CONFIG_FILES = "RLPTests/rlptest.json";

  private final RLPRefTestCaseSpec spec;

  public RLPRefTest(final String name, final RLPRefTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(RLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES);
  }

  @Test
  public void encode() {
    Assertions.assertThat(RLPTestUtil.encode(spec.getIn())).isEqualTo(spec.getOut());
  }

  @Test
  public void decode() {
    Assertions.assertThat(RLPTestUtil.decode(spec.getOut())).isEqualTo(spec.getIn());
  }
}
