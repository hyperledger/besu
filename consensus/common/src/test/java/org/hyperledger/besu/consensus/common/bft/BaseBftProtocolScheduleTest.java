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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.JsonBftConfigOptions;
import org.hyperledger.besu.config.TransitionsConfigOptions;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class BaseBftProtocolScheduleTest {

  private final GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);
  private final BftExtraDataCodec bftExtraDataCodec = mock(BftExtraDataCodec.class);

  @Test
  public void ensureBlockRewardAndMiningBeneficiaryInProtocolSpecMatchConfig() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(5);
    final Address miningBeneficiary = Address.fromHexString("0x1");
    final BftConfigOptions configOptions =
        createBftConfig(arbitraryBlockReward, Optional.of(miningBeneficiary));
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);

    final ProtocolSchedule schedule =
        createProtocolSchedule(List.of(new ForkSpec<>(0, configOptions)));
    final ProtocolSpec spec = schedule.getByBlockNumber(1);

    assertThat(spec.getBlockReward()).isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(mock(BlockHeader.class)))
        .isEqualTo(miningBeneficiary);
  }

  @Test
  public void missingMiningBeneficiaryInConfigWillPayCoinbaseInHeader() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = createBftConfig(arbitraryBlockReward);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    final ProtocolSchedule schedule =
        createProtocolSchedule(List.of(new ForkSpec<>(0, configOptions)));
    final ProtocolSpec spec = schedule.getByBlockNumber(1);

    final Address headerCoinbase = Address.fromHexString("0x123");
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getCoinbase()).thenReturn(headerCoinbase);

    assertThat(spec.getBlockReward()).isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header))
        .isEqualTo(headerCoinbase);
  }

  @Test
  public void ensureForksAreRespected_beginWithNonEmptyMiningBeneficiary() {
    ensureForksAreRespected(false);
  }

  @Test
  public void ensureForksAreRespected_beginWithEmptyMiningBeneficiary() {
    ensureForksAreRespected(true);
  }

  private void ensureForksAreRespected(final boolean initialBeneficiaryIsEmpty) {
    final Address headerCoinbase = Address.fromHexString("0x123");
    final Address beneficiary1 = Address.fromHexString("0x01");
    final Address beneficiary2 = Address.fromHexString("0x02");
    final Address beneficiary3 = Address.fromHexString("0x03");
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getCoinbase()).thenReturn(headerCoinbase);

    // Alternate empty and non-empty mining beneficiary config
    final BftConfigOptions initialConfig =
        createBftConfig(
            BigInteger.valueOf(3),
            Optional.of(beneficiary1).filter(__ -> !initialBeneficiaryIsEmpty));
    final BftConfigOptions fork1 =
        createBftConfig(
            BigInteger.valueOf(2),
            Optional.of(beneficiary2).filter(__ -> initialBeneficiaryIsEmpty));
    final BftConfigOptions fork2 =
        createBftConfig(
            BigInteger.valueOf(1),
            Optional.of(beneficiary3).filter(__ -> !initialBeneficiaryIsEmpty));

    when(genesisConfig.getBftConfigOptions()).thenReturn(initialConfig);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    final ProtocolSchedule schedule =
        createProtocolSchedule(
            List.of(
                new ForkSpec<>(0, initialConfig),
                new ForkSpec<>(2, fork1),
                new ForkSpec<>(5, fork2)));

    // Check initial config
    for (int i = 0; i < 2; i++) {
      final ProtocolSpec spec = schedule.getByBlockNumber(i);
      final Address expectedBeneficiary = initialBeneficiaryIsEmpty ? headerCoinbase : beneficiary1;
      assertThat(spec.getBlockReward()).isEqualTo(Wei.of(BigInteger.valueOf(3)));
      assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header))
          .isEqualTo(expectedBeneficiary);
    }

    // Check fork1
    for (int i = 2; i < 5; i++) {
      final ProtocolSpec spec = schedule.getByBlockNumber(i);
      final Address expectedBeneficiary = initialBeneficiaryIsEmpty ? beneficiary2 : headerCoinbase;
      assertThat(spec.getBlockReward()).isEqualTo(Wei.of(BigInteger.valueOf(2)));
      assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header))
          .isEqualTo(expectedBeneficiary);
    }

    // Check fork2
    for (int i = 5; i < 8; i++) {
      final ProtocolSpec spec = schedule.getByBlockNumber(i);
      final Address expectedBeneficiary = initialBeneficiaryIsEmpty ? headerCoinbase : beneficiary3;
      assertThat(spec.getBlockReward()).isEqualTo(Wei.of(BigInteger.valueOf(1)));
      assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header))
          .isEqualTo(expectedBeneficiary);
    }
  }

  @Test
  public void negativeBlockRewardThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(-3);
    final BftConfigOptions configOptions = createBftConfig(arbitraryBlockReward);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(() -> createProtocolSchedule(List.of(new ForkSpec<>(0, configOptions))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bft Block reward in config cannot be negative");
  }

  @Test
  public void zeroEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = createBftConfig(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(0L);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(() -> createProtocolSchedule(List.of(new ForkSpec<>(0, configOptions))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void negativeEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = createBftConfig(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(-3000L);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(() -> createProtocolSchedule(List.of(new ForkSpec<>(0, configOptions))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void blockRewardSpecifiedInTransitionCreatesNewMilestone() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(5);
    final Address miningBeneficiary = Address.fromHexString("0x1");
    final BftConfigOptions configOptions =
        createBftConfig(arbitraryBlockReward, Optional.of(miningBeneficiary));
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.isIbft2()).thenReturn(true);

    final long transitionBlock = 5L;
    final BigInteger forkBlockReward = arbitraryBlockReward.multiply(BigInteger.valueOf(2));
    final MutableBftConfigOptions blockRewardTransition =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    blockRewardTransition.setBlockRewardWei(forkBlockReward);

    final ProtocolSchedule schedule =
        createProtocolSchedule(
            List.of(
                new ForkSpec<>(0, configOptions),
                new ForkSpec<>(transitionBlock, blockRewardTransition)));

    assertThat(schedule.streamMilestoneBlocks().count()).isEqualTo(2);
    assertThat(schedule.getByBlockNumber(0).getBlockReward())
        .isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(schedule.getByBlockNumber(transitionBlock).getBlockReward())
        .isEqualTo(Wei.of(forkBlockReward));
  }

  private ProtocolSchedule createProtocolSchedule(final List<ForkSpec<BftConfigOptions>> forks) {
    final BaseBftProtocolSchedule bftProtocolSchedule =
        new BaseBftProtocolSchedule() {
          @Override
          protected BlockHeaderValidator.Builder createBlockHeaderRuleset(
              final BftConfigOptions config, final FeeMarket feeMarket) {
            return new BlockHeaderValidator.Builder();
          }
        };
    return bftProtocolSchedule.createProtocolSchedule(
        genesisConfig,
        new ForksSchedule<>(forks),
        PrivacyParameters.DEFAULT,
        false,
        bftExtraDataCodec,
        EvmConfiguration.DEFAULT);
  }

  private BftConfigOptions createBftConfig(final BigInteger blockReward) {
    return createBftConfig(blockReward, Optional.empty());
  }

  private BftConfigOptions createBftConfig(
      final BigInteger blockReward, final Optional<Address> miningBeneficiary) {
    final BftConfigOptions bftConfig = mock(JsonBftConfigOptions.class);
    when(bftConfig.getMiningBeneficiary()).thenReturn(miningBeneficiary);
    when(bftConfig.getBlockRewardWei()).thenReturn(blockReward);
    when(bftConfig.getEpochLength()).thenReturn(3000L);

    return bftConfig;
  }
}
