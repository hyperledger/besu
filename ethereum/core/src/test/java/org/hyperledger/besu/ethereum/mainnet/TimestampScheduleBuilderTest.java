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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

public class TimestampScheduleBuilderTest {

  private static final BigInteger chainId = BigInteger.ONE;
  private static final BigInteger defaultChainId = BigInteger.ONE;
  private static final PrivacyParameters privacyParameters = new PrivacyParameters();
  private static final EvmConfiguration evmConfiguration = EvmConfiguration.DEFAULT;
  private TimestampScheduleBuilder builder;
  private StubGenesisConfigOptions config;

  private final Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier = Function.identity();

  private final long FIRST_TIMESTAMP_FORK = 1L;

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
            ProtocolSpecAdapters.create(FIRST_TIMESTAMP_FORK, modifier),
            privacyParameters,
            isRevertReasonEnabled,
            quorumCompatibilityMode,
            evmConfiguration);
  }

  // TODO SLD unify this test
  //  @Test
  //  public void createTimestampScheduleInOrder() {
  //    config.mergeNetSplitBlock(0);
  //    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
  //    config.cancunTime(3);
  //    final UnifiedProtocolSchedule timestampSchedule = builder.createTimestampSchedule();
  //
  //    assertThat(timestampSchedule.getChainId()).contains(chainId);
  //    assertThat(timestampSchedule.getByBlockHeader(blockHeader(0))).isNull();
  //
  // assertThat(timestampSchedule.getByBlockHeader(blockHeader(1)).getName()).isEqualTo("Shanghai");
  //
  // assertThat(timestampSchedule.getByBlockHeader(blockHeader(2)).getName()).isEqualTo("Shanghai");
  //
  // assertThat(timestampSchedule.getByBlockHeader(blockHeader(3)).getName()).isEqualTo("Cancun");
  //
  // assertThat(timestampSchedule.getByBlockHeader(blockHeader(4)).getName()).isEqualTo("Cancun");
  //  }

  @Test
  public void createTimestampScheduleOverlappingUsesLatestFork() {
    config.shanghaiTime(0);
    config.cancunTime(0);
    final UnifiedProtocolSchedule timestampSchedule = builder.createTimestampSchedule();

    assertThat(timestampSchedule.getChainId()).contains(chainId);
    assertThat(timestampSchedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Cancun");
    assertThat(timestampSchedule.getByBlockHeader(blockHeader(1)).getName()).isEqualTo("Cancun");
  }

  @Test
  public void createTimestampScheduleOutOfOrderThrows() {
    config.shanghaiTime(3);
    config.cancunTime(2);
    assertThatThrownBy(() -> builder.createTimestampSchedule())
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Genesis Config Error: 'Cancun' is scheduled for milestone 2 but it must be on or after milestone 3.");
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
