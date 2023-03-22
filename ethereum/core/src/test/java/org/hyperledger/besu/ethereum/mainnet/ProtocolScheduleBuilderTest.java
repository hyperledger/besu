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
import org.hyperledger.besu.ethereum.core.BlockNumberStreamingProtocolSchedule;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ProtocolScheduleBuilderTest {

  @Mock GenesisConfigOptions configOptions;
  @Mock private Function<ProtocolSpecBuilder, ProtocolSpecBuilder> modifier;
  private static final BigInteger CHAIN_ID = BigInteger.ONE;
  private ProtocolScheduleBuilder builder;

  @Before
  public void setup() {
    builder =
        new ProtocolScheduleBuilder(
            configOptions,
            CHAIN_ID,
            ProtocolSpecAdapters.create(0, Function.identity()),
            new PrivacyParameters(),
            false,
            false,
            EvmConfiguration.DEFAULT);
  }

  @Test
  public void createProtocolScheduleInOrder() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(1L));
    when(configOptions.getDaoForkBlock()).thenReturn(OptionalLong.of(2L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(13L));
    when(configOptions.getMergeNetSplitBlockNumber()).thenReturn(OptionalLong.of(15L));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockNumber(0).getName()).isEqualTo("Frontier");
    assertThat(protocolSchedule.getByBlockNumber(1).getName()).isEqualTo("Homestead");
    assertThat(protocolSchedule.getByBlockNumber(2).getName()).isEqualTo("DaoRecoveryInit");
    assertThat(protocolSchedule.getByBlockNumber(3).getName()).isEqualTo("DaoRecoveryTransition");
    assertThat(protocolSchedule.getByBlockNumber(12).getName()).isEqualTo("Homestead");
    assertThat(protocolSchedule.getByBlockNumber(13).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockNumber(14).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockNumber(15).getName()).isEqualTo("ParisFork");
    assertThat(protocolSchedule.getByBlockNumber(50).getName()).isEqualTo("ParisFork");
  }

  @Test
  public void createProtocolScheduleOverlappingUsesLatestFork() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(0L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(0L));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockNumber(0).getName()).isEqualTo("Byzantium");
    assertThat(protocolSchedule.getByBlockNumber(1).getName()).isEqualTo("Byzantium");
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
  public void modifierInsertedBetweenBlocksIsAppliedToLaterAndCreatesInterimMilestone() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));

    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final BlockNumberStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L, 5L);
    assertThat(schedule.getByBlockNumber(0).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockNumber(2).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockNumber(5).getName()).isEqualTo("Homestead");

    verify(modifier, times(2)).apply(any());
  }

  @Test
  public void modifierPastEndOfDefinedMilestonesGetsItsOwnMilestoneCreated() {
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final BlockNumberStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L);
    assertThat(schedule.getByBlockNumber(0).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockNumber(2).getName()).isEqualTo("Frontier");

    verify(modifier, times(1)).apply(any());
  }

  @Test
  public void modifierOnDefinedMilestoneIsAppliedButDoesNotGetAnExtraMilestoneCreated() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final BlockNumberStreamingProtocolSchedule schedule = createScheduleModifiedAt(5);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 5L);
    assertThat(schedule.getByBlockNumber(0).getName()).isEqualTo("Frontier");
    assertThat(schedule.getByBlockNumber(5).getName()).isEqualTo("Homestead");

    verify(modifier, times(1)).apply(any());
  }

  private BlockNumberStreamingProtocolSchedule createScheduleModifiedAt(final int blockNumber) {
    final ProtocolScheduleBuilder builder =
        new ProtocolScheduleBuilder(
            configOptions,
            CHAIN_ID,
            ProtocolSpecAdapters.create(blockNumber, modifier),
            new PrivacyParameters(),
            false,
            false,
            EvmConfiguration.DEFAULT);

    return new BlockNumberStreamingProtocolSchedule(
        (MutableProtocolSchedule) builder.createProtocolSchedule());
  }
}
