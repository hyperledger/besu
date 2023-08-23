/*
 * Copyright 2020 ConsenSys AG.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MilestoneStreamingProtocolSchedule;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class ProtocolScheduleBuilderTest {
  private final long PRE_SHANGHAI_TIMESTAMP = 1680488620L; // Mon, 03 Apr 2023 02:23:40 UTC
  @Mock GenesisConfigOptions configOptions;
  @Mock private Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier;
  private static final BigInteger CHAIN_ID = BigInteger.ONE;
  private ProtocolScheduleBuilder builder;

  @BeforeEach
  public void setup() {
    builder =
        new ProtocolScheduleBuilder(
            configOptions,
            CHAIN_ID,
            ProtocolSpecAdapters.create(0, Function.identity()),
            new PrivacyParameters(),
            false,
            EvmConfiguration.DEFAULT);
  }

  @Test
  public void createProtocolScheduleInOrder() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(1L));
    when(configOptions.getDaoForkBlock()).thenReturn(OptionalLong.of(2L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(13L));
    when(configOptions.getMergeNetSplitBlockNumber()).thenReturn(OptionalLong.of(15L));
    when(configOptions.getShanghaiTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 1));
    when(configOptions.getCancunTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 3));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Frontier");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(1)).getName()).isEqualTo("Homestead");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(2)).getName())
        .isEqualTo("DaoRecoveryInit");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(3)).getName())
        .isEqualTo("DaoRecoveryTransition");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(12)).getName()).isEqualTo("Homestead");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(13)).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(14)).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(15)).getName()).isEqualTo("ParisFork");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(50)).getName()).isEqualTo("ParisFork");
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(51, PRE_SHANGHAI_TIMESTAMP + 1))
                .getName())
        .isEqualTo("Shanghai");
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(52, PRE_SHANGHAI_TIMESTAMP + 2))
                .getName())
        .isEqualTo("Shanghai");
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(53, PRE_SHANGHAI_TIMESTAMP + 3))
                .getName())
        .isEqualTo("Cancun");
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(54, PRE_SHANGHAI_TIMESTAMP + 4))
                .getName())
        .isEqualTo("Cancun");
  }

  @Test
  public void createProtocolScheduleOverlappingUsesLatestFork() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(0L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(0L));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(1)).getName()).isEqualTo("Byzantium");
  }

  @Test
  public void createProtocolScheduleOutOfOrderThrows() {
    when(configOptions.getDaoForkBlock()).thenReturn(OptionalLong.of(0L));
    when(configOptions.getArrowGlacierBlockNumber()).thenReturn(OptionalLong.of(12L));
    when(configOptions.getGrayGlacierBlockNumber()).thenReturn(OptionalLong.of(11L));
    assertThatThrownBy(() -> builder.createProtocolSchedule())
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Genesis Config Error: 'GrayGlacier' is scheduled for milestone 11 but it must be on or after milestone 12.");
  }

  @Test
  public void createProtocolScheduleWithTimestampsOutOfOrderThrows() {
    when(configOptions.getDaoForkBlock()).thenReturn(OptionalLong.of(0L));
    when(configOptions.getShanghaiTime()).thenReturn(OptionalLong.of(3L));
    when(configOptions.getCancunTime()).thenReturn(OptionalLong.of(2L));
    assertThatThrownBy(() -> builder.createProtocolSchedule())
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Genesis Config Error: 'Cancun' is scheduled for milestone 2 but it must be on or after milestone 3.");
  }

  @Test
  public void modifierInsertedBetweenBlocksIsAppliedToLaterAndCreatesInterimMilestone() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));

    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L, 5L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockHeader(blockHeader(2)).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockHeader(blockHeader(5)).getName()).isEqualTo("Homestead");

    verify(modifier, times(2)).apply(any());
  }

  @Test
  public void modifierPastEndOfDefinedMilestonesGetsItsOwnMilestoneCreated() {
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockHeader(blockHeader(2)).getName()).isEqualTo("Frontier");

    verify(modifier, times(1)).apply(any());
  }

  @Test
  public void modifierOnDefinedMilestoneIsAppliedButDoesNotGetAnExtraMilestoneCreated() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(5);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 5L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockHeader(blockHeader(5)).getName()).isEqualTo("Homestead");
    verify(modifier, times(1)).apply(any());
  }

  private MilestoneStreamingProtocolSchedule createScheduleModifiedAt(final int blockNumber) {
    final ProtocolScheduleBuilder builder =
        new ProtocolScheduleBuilder(
            configOptions,
            CHAIN_ID,
            ProtocolSpecAdapters.create(blockNumber, modifier),
            new PrivacyParameters(),
            false,
            EvmConfiguration.DEFAULT);

    return new MilestoneStreamingProtocolSchedule(
        (DefaultProtocolSchedule) builder.createProtocolSchedule());
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture()
        .number(number)
        .timestamp(PRE_SHANGHAI_TIMESTAMP)
        .buildHeader();
  }

  private BlockHeader blockHeader(final long number, final long timestamp) {
    return new BlockHeaderTestFixture().number(number).timestamp(timestamp).buildHeader();
  }
}
