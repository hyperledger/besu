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
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BERLIN;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BPO1;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BPO2;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BPO3;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BPO4;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BPO5;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BYZANTIUM;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.CANCUN;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.DAO_RECOVERY_INIT;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.DAO_RECOVERY_TRANSITION;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.FRONTIER;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.HOMESTEAD;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.LONDON;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.OSAKA;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.PARIS;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.PRAGUE;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.SHANGHAI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MilestoneStreamingProtocolSchedule;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
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
class ProtocolScheduleBuilderTest {
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
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(0, Function.identity()),
            false,
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            false,
            new NoOpMetricsSystem());
  }

  @Test
  void createProtocolScheduleInOrder() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(1L));
    when(configOptions.getDaoForkBlock()).thenReturn(OptionalLong.of(2L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(13L));
    when(configOptions.getMergeNetSplitBlockNumber()).thenReturn(OptionalLong.of(15L));
    when(configOptions.getShanghaiTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 1));
    when(configOptions.getCancunTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 3));
    when(configOptions.getPragueTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 5));
    when(configOptions.getOsakaTime()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 7));
    when(configOptions.getBpo1Time()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 9));
    when(configOptions.getBpo2Time()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 11));
    when(configOptions.getBpo3Time()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 13));
    when(configOptions.getBpo4Time()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 15));
    when(configOptions.getBpo5Time()).thenReturn(OptionalLong.of(PRE_SHANGHAI_TIMESTAMP + 17));
    when(configOptions.getDepositContractAddress()).thenReturn(Optional.of(Address.ZERO));
    when(configOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));
    when(configOptions.getWithdrawalRequestContractAddress()).thenReturn(Optional.of(Address.ZERO));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(0)).getHardforkId())
        .isEqualTo(FRONTIER);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(1)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(2)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_INIT);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(3)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_TRANSITION);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(12)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(13)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(14)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(15)).getHardforkId()).isEqualTo(PARIS);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(50)).getHardforkId()).isEqualTo(PARIS);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(51, PRE_SHANGHAI_TIMESTAMP + 1))
                .getHardforkId())
        .isEqualTo(SHANGHAI);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(52, PRE_SHANGHAI_TIMESTAMP + 2))
                .getHardforkId())
        .isEqualTo(SHANGHAI);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(53, PRE_SHANGHAI_TIMESTAMP + 3))
                .getHardforkId())
        .isEqualTo(CANCUN);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(54, PRE_SHANGHAI_TIMESTAMP + 4))
                .getHardforkId())
        .isEqualTo(CANCUN);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(55, PRE_SHANGHAI_TIMESTAMP + 5))
                .getHardforkId())
        .isEqualTo(PRAGUE);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(56, PRE_SHANGHAI_TIMESTAMP + 7))
                .getHardforkId())
        .isEqualTo(OSAKA);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(57, PRE_SHANGHAI_TIMESTAMP + 9))
                .getHardforkId())
        .isEqualTo(BPO1);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(58, PRE_SHANGHAI_TIMESTAMP + 11))
                .getHardforkId())
        .isEqualTo(BPO2);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(59, PRE_SHANGHAI_TIMESTAMP + 13))
                .getHardforkId())
        .isEqualTo(BPO3);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(60, PRE_SHANGHAI_TIMESTAMP + 15))
                .getHardforkId())
        .isEqualTo(BPO4);
    assertThat(
            protocolSchedule
                .getByBlockHeader(blockHeader(61, PRE_SHANGHAI_TIMESTAMP + 17))
                .getHardforkId())
        .isEqualTo(BPO5);
  }

  @Test
  void milestoneForShouldQueryAllAvailableHardforks() {
    final long BPO5_TIME = 1722333828L;

    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getConstantinopleBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getPetersburgBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getIstanbulBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBerlinBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getLondonBlockNumber()).thenReturn(OptionalLong.of(0));
    when(configOptions.getShanghaiTime()).thenReturn(OptionalLong.of(0));
    when(configOptions.getCancunTime()).thenReturn(OptionalLong.of(0));
    when(configOptions.getPragueTime()).thenReturn(OptionalLong.of(0));
    when(configOptions.getOsakaTime()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBpo1Time()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBpo2Time()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBpo3Time()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBpo4Time()).thenReturn(OptionalLong.of(0));
    when(configOptions.getBpo5Time()).thenReturn(OptionalLong.of(BPO5_TIME));
    when(configOptions.getDepositContractAddress()).thenReturn(Optional.of(Address.ZERO));
    when(configOptions.getConsolidationRequestContractAddress())
        .thenReturn(Optional.of(Address.ZERO));
    when(configOptions.getWithdrawalRequestContractAddress()).thenReturn(Optional.of(Address.ZERO));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    final Optional<Long> maybeBerlinMileStone = protocolSchedule.milestoneFor(BERLIN);
    assertThat(maybeBerlinMileStone).isPresent();
    assertThat(maybeBerlinMileStone.get()).isEqualTo(0);

    final Optional<Long> maybeLondonMileStone = protocolSchedule.milestoneFor(LONDON);
    assertThat(maybeLondonMileStone).isPresent();
    assertThat(maybeLondonMileStone.get()).isEqualTo(0);

    final Optional<Long> maybeShanghaiMileStone = protocolSchedule.milestoneFor(SHANGHAI);
    assertThat(maybeShanghaiMileStone).isPresent();
    assertThat(maybeShanghaiMileStone.get()).isEqualTo(0);

    final Optional<Long> maybeCancunMileStone = protocolSchedule.milestoneFor(CANCUN);
    assertThat(maybeCancunMileStone).isPresent();
    assertThat(maybeCancunMileStone.get()).isEqualTo(0);

    final Optional<Long> maybePragueMileStone = protocolSchedule.milestoneFor(PRAGUE);
    assertThat(maybePragueMileStone).isPresent();
    assertThat(maybePragueMileStone.get()).isEqualTo(0);

    final Optional<Long> maybeOsakaMileStone = protocolSchedule.milestoneFor(OSAKA);
    assertThat(maybeOsakaMileStone).isPresent();
    assertThat(maybeOsakaMileStone.get()).isEqualTo(0);

    final Optional<Long> maybeBpo1MileStone = protocolSchedule.milestoneFor(BPO1);
    assertThat(maybeBpo1MileStone).isPresent();
    assertThat(maybeBpo1MileStone.get()).isEqualTo(0);

    final Optional<Long> maybeBpo2MileStone = protocolSchedule.milestoneFor(BPO2);
    assertThat(maybeBpo2MileStone).isPresent();
    assertThat(maybeBpo2MileStone.get()).isEqualTo(0);

    final Optional<Long> maybeBpo3MileStone = protocolSchedule.milestoneFor(BPO3);
    assertThat(maybeBpo3MileStone).isPresent();
    assertThat(maybeBpo3MileStone.get()).isEqualTo(0);

    final Optional<Long> maybeBpo4MileStone = protocolSchedule.milestoneFor(BPO4);
    assertThat(maybeBpo4MileStone).isPresent();
    assertThat(maybeBpo4MileStone.get()).isEqualTo(0);

    final Optional<Long> maybeBpo5MileStone = protocolSchedule.milestoneFor(BPO5);
    assertThat(maybeBpo5MileStone).isPresent();
    assertThat(maybeBpo5MileStone.get()).isEqualTo(BPO5_TIME);
  }

  @Test
  void createProtocolScheduleOverlappingUsesLatestFork() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(0L));
    when(configOptions.getByzantiumBlockNumber()).thenReturn(OptionalLong.of(0L));
    final ProtocolSchedule protocolSchedule = builder.createProtocolSchedule();

    assertThat(protocolSchedule.getChainId()).contains(CHAIN_ID);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(0)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    assertThat(protocolSchedule.getByBlockHeader(blockHeader(1)).getHardforkId())
        .isEqualTo(BYZANTIUM);
  }

  @Test
  void createProtocolScheduleOutOfOrderThrows() {
    when(configOptions.getArrowGlacierBlockNumber()).thenReturn(OptionalLong.of(12L));
    when(configOptions.getGrayGlacierBlockNumber()).thenReturn(OptionalLong.of(11L));
    assertThatThrownBy(() -> builder.createProtocolSchedule())
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Genesis Config Error: 'GRAY_GLACIER' is scheduled for milestone 11 but it must be on or after milestone 12.");
  }

  @Test
  void createProtocolScheduleWithTimestampsOutOfOrderThrows() {
    when(configOptions.getShanghaiTime()).thenReturn(OptionalLong.of(3L));
    when(configOptions.getCancunTime()).thenReturn(OptionalLong.of(2L));
    assertThatThrownBy(() -> builder.createProtocolSchedule())
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Genesis Config Error: 'CANCUN' is scheduled for milestone 2 but it must be on or after milestone 3.");
  }

  @Test
  void modifierInsertedBetweenBlocksIsAppliedToLaterAndCreatesInterimMilestone() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));

    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L, 5L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getHardforkId()).isEqualTo(FRONTIER);
    assertThat(schedule.getByBlockHeader(blockHeader(2)).getHardforkId()).isEqualTo(FRONTIER);
    assertThat(schedule.getByBlockHeader(blockHeader(5)).getHardforkId()).isEqualTo(HOMESTEAD);

    verify(modifier, times(2)).apply(any());
  }

  @Test
  void modifierPastEndOfDefinedMilestonesGetsItsOwnMilestoneCreated() {
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(2);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 2L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getHardforkId()).isEqualTo(FRONTIER);
    assertThat(schedule.getByBlockHeader(blockHeader(2)).getHardforkId()).isEqualTo(FRONTIER);

    verify(modifier, times(1)).apply(any());
  }

  @Test
  void modifierOnDefinedMilestoneIsAppliedButDoesNotGetAnExtraMilestoneCreated() {
    when(configOptions.getHomesteadBlockNumber()).thenReturn(OptionalLong.of(5L));
    when(modifier.apply(any()))
        .thenAnswer((Answer<ProtocolSpecBuilder>) invocation -> invocation.getArgument(0));

    final MilestoneStreamingProtocolSchedule schedule = createScheduleModifiedAt(5);

    // A default spec exists at 0 (frontier), then the spec as requested in config, then another
    // added at the point at which the modifier is applied.
    assertThat(schedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .containsExactly(0L, 5L);
    assertThat(schedule.getByBlockHeader(blockHeader(0)).getHardforkId()).isEqualTo(FRONTIER);
    assertThat(schedule.getByBlockHeader(blockHeader(5)).getHardforkId()).isEqualTo(HOMESTEAD);
    verify(modifier, times(1)).apply(any());
  }

  private MilestoneStreamingProtocolSchedule createScheduleModifiedAt(final int blockNumber) {
    final ProtocolScheduleBuilder builder =
        new ProtocolScheduleBuilder(
            configOptions,
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(blockNumber, modifier),
            false,
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            false,
            new NoOpMetricsSystem());

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
