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

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;

public class TimestampScheduleBuilderTest {

  private final BigInteger chainId = BigInteger.ONE;
  private final BigInteger defaultChainId = BigInteger.ONE;
  private final PrivacyParameters privacyParameters = new PrivacyParameters();
  private final EvmConfiguration evmConfiguration = EvmConfiguration.DEFAULT;
  private TimestampScheduleBuilder builder;
  private StubGenesisConfigOptions config;

  @Before
  public void setup() {
    config = new StubGenesisConfigOptions();
    config.chainId(chainId);
    boolean isRevertReasonEnabled = false;
    boolean quorumCompatibilityMode = false;
    builder =
        new TimestampScheduleBuilder(
            config,
            defaultChainId,
            privacyParameters,
            isRevertReasonEnabled,
            quorumCompatibilityMode,
            evmConfiguration);
  }

  @Test
  public void createTimestampSchedule() {
    config.shanghaiTimestamp(1);
    config.cancunTimestamp(3);
    final TimestampSchedule timestampSchedule = builder.createTimestampSchedule();

    assertThat(timestampSchedule.getChainId()).contains(chainId);
    assertThat(timestampSchedule.getByTimestamp(0)).isEmpty();
    assertThat(timestampSchedule.getByTimestamp(1))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Shanghai");
    assertThat(timestampSchedule.getByTimestamp(2))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Shanghai");
    assertThat(timestampSchedule.getByTimestamp(3))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Cancun");
    assertThat(timestampSchedule.getByTimestamp(4))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Cancun");
  }
}
