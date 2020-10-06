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
package org.hyperledger.besu.ethereum.core.fees;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EIP1559BaseFeeTest {

  private final EIP1559 eip1559 = new EIP1559(0);

  @Parameters
  public static Collection<Object[]> data() {
    try {
      final List<Object[]> data = new ArrayList<>();
      final String testFilePath = "basefee-test.json";
      final URL testFileUrl = EIP1559BaseFeeTest.class.getResource(testFilePath);
      checkState(testFileUrl != null, "Cannot find test file " + testFilePath);
      final String testSuiteJson = Resources.toString(testFileUrl, Charsets.UTF_8);
      final ObjectMapper objectMapper = new ObjectMapper();
      final Eip1559BaseFeeTestCase[] testCases =
          objectMapper.readValue(testSuiteJson, Eip1559BaseFeeTestCase[].class);
      for (final Eip1559BaseFeeTestCase testCase : testCases) {
        data.add(
            new Object[] {
              testCase.parentBaseFee,
              testCase.parentGasUsed,
              testCase.parentTargetGasUsed,
              testCase.expectedBaseFee
            });
      }
      return data;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final long parentBaseFee;
  private final long parentGasUsed;
  private final long parentTargetGasUsed;
  private final long expectedBaseFee;

  public EIP1559BaseFeeTest(
      final long parentBaseFee,
      final long parentGasUsed,
      final long parentTargetGasUsed,
      final long expectedBaseFee) {
    this.parentBaseFee = parentBaseFee;
    this.parentGasUsed = parentGasUsed;
    this.parentTargetGasUsed = parentTargetGasUsed;
    this.expectedBaseFee = expectedBaseFee;
  }

  @Before
  public void setUp() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  @Ignore("Need to have spec frozen to define correct values")
  public void assertThatBaseFeeIsCorrect() {
    assertThat(eip1559.computeBaseFee(0L, parentBaseFee, parentGasUsed, parentTargetGasUsed))
        .isEqualTo(expectedBaseFee);
  }

  private static class Eip1559BaseFeeTestCase {

    private long parentBaseFee;
    private long parentGasUsed;
    private long parentTargetGasUsed;
    private long expectedBaseFee;

    public Eip1559BaseFeeTestCase() {}

    public Eip1559BaseFeeTestCase(
        final long parentBaseFee,
        final long parentGasUsed,
        final long parentTargetGasUsed,
        final long expectedBaseFee) {
      this.parentBaseFee = parentBaseFee;
      this.parentGasUsed = parentGasUsed;
      this.parentTargetGasUsed = parentTargetGasUsed;
      this.expectedBaseFee = expectedBaseFee;
    }

    public long getParentBaseFee() {
      return parentBaseFee;
    }

    public long getParentGasUsed() {
      return parentGasUsed;
    }

    public long getParentTargetGasUsed() {
      return parentTargetGasUsed;
    }

    public long getExpectedBaseFee() {
      return expectedBaseFee;
    }

    public void setParentBaseFee(final long parentBaseFee) {
      this.parentBaseFee = parentBaseFee;
    }

    public void setParentGasUsed(final long parentGasUsed) {
      this.parentGasUsed = parentGasUsed;
    }

    public void setParentTargetGasUsed(final long parentTargetGasUsed) {
      this.parentTargetGasUsed = parentTargetGasUsed;
    }

    public void setExpectedBaseFee(final long expectedBaseFee) {
      this.expectedBaseFee = expectedBaseFee;
    }
  }
}
