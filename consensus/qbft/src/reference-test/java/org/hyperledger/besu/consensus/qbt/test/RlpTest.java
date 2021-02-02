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
package org.hyperledger.besu.consensus.qbt.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.consensus.qbt.support.RlpTestCaseSpec;
import org.hyperledger.besu.consensus.qbt.support.RlpTestInput;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RlpTest {

  private static final String TEST_CONFIG_PATH = "rlp";
  private final RlpTestCaseSpec spec;

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(RlpTestCaseSpec.class).generate(TEST_CONFIG_PATH);
  }

  @Test
  public void encode() {
    assertThat(Bytes.fromHexString(spec.getOutput())).isEqualTo(spec.getInput().toRlp());
  }

  @Test
  public void decode() {
    final RlpTestInput rlpTestInput =
        spec.getInput().fromRlp(Bytes.fromHexString(spec.getOutput()));
    assertThat(spec.getInput()).usingRecursiveComparison().isEqualTo(rlpTestInput);
  }

  public RlpTest(final String name, final RlpTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }
}
