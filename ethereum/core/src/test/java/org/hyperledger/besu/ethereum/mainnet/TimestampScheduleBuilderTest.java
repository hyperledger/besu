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
  private static final BlockHeader BLOCK_HEADER =
      new BlockHeaderTestFixture().timestamp(1L).buildHeader();
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

  @Test
  public void createTimestampScheduleInOrder() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    config.cancunTime(3);
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

  @Test
  public void createTimestampScheduleOverlappingUsesLatestFork() {
    config.shanghaiTime(0);
    config.cancunTime(0);
    final TimestampSchedule timestampSchedule = builder.createTimestampSchedule();

    assertThat(timestampSchedule.getChainId()).contains(chainId);
    assertThat(timestampSchedule.getByTimestamp(0))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Cancun");
    assertThat(timestampSchedule.getByTimestamp(1))
        .isPresent()
        .map(ProtocolSpec::getName)
        .hasValue("Cancun");
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

  @Test
  public void getByBlockHeader_whenSpecFound() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    final TimestampSchedule schedule = builder.createTimestampSchedule();

    assertThat(schedule.getByBlockHeader(BLOCK_HEADER)).isNotNull();
  }

  @Test
  public void getByBlockHeader_whenSpecNotFoundReturnsNull() {
    config.shanghaiTime(2L);
    builder =
        new TimestampScheduleBuilder(
            config,
            defaultChainId,
            ProtocolSpecAdapters.create(2L, modifier),
            privacyParameters,
            false,
            false,
            evmConfiguration);
    final TimestampSchedule schedule = builder.createTimestampSchedule();

    assertThat(schedule.getByBlockHeader(BLOCK_HEADER)).isNull();
  }

  @Test
  public void streamMilestoneBlocksReturnTimestampsInOrder() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    config.cancunTime(2L);
    config.experimentalEipsTime(5L);
    config.futureEipsTime(3L);
    final TimestampSchedule schedule = builder.createTimestampSchedule();

    assertThat(schedule.streamMilestoneBlocks()).containsExactly(FIRST_TIMESTAMP_FORK, 2L, 3L, 5L);
  }

  @Test
  public void isOnForkBoundary() {
    config.shanghaiTime(FIRST_TIMESTAMP_FORK);
    config.cancunTime(2L);
    config.experimentalEipsTime(4L);
    final HeaderBasedProtocolSchedule protocolSchedule = builder.createTimestampSchedule();

    assertThat(protocolSchedule.isOnForkBoundary(header(0))).isEqualTo(false);
    assertThat(protocolSchedule.isOnForkBoundary(header(FIRST_TIMESTAMP_FORK))).isEqualTo(true);
    assertThat(protocolSchedule.isOnForkBoundary(header(2))).isEqualTo(true);
    assertThat(protocolSchedule.isOnForkBoundary(header(3))).isEqualTo(false);
    assertThat(protocolSchedule.isOnForkBoundary(header(4))).isEqualTo(true);
  }

  private BlockHeader header(final long timestamp) {
    return new BlockHeaderTestFixture().timestamp(timestamp).buildHeader();
  }
}
