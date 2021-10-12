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
import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.TransitionsConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.Test;

public class BaseBftProtocolScheduleTest {

  private final GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);
  private final BftExtraDataCodec bftExtraDataCodec = mock(BftExtraDataCodec.class);

  @Test
  public void ensureBlockRewardAndMiningBeneficiaryInProtocolSpecMatchConfig() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(5);
    final String miningBeneficiary = Address.fromHexString("0x1").toString();
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.of(miningBeneficiary));
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);

    final ProtocolSchedule schedule = createProtocolSchedule();
    final ProtocolSpec spec = schedule.getByBlockNumber(1);

    assertThat(spec.getBlockReward()).isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(null))
        .isEqualTo(Address.fromHexString(miningBeneficiary));
  }

  @Test
  public void illegalMiningBeneficiaryStringThrowsException() {
    final String miningBeneficiary = "notHexStringOfTwentyBytes";
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.of(miningBeneficiary));
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(configOptions.getEpochLength()).thenReturn(3000L);
    when(configOptions.getBlockRewardWei()).thenReturn(BigInteger.ZERO);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);
    assertThatThrownBy(this::createProtocolSchedule)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mining beneficiary in config is not a valid ethereum address");
  }

  @Test
  public void missingMiningBeneficiaryInConfigWillPayCoinbaseInHeader() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    final ProtocolSchedule schedule = createProtocolSchedule();
    final ProtocolSpec spec = schedule.getByBlockNumber(1);

    final Address headerCoinbase = Address.fromHexString("0x123");
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getCoinbase()).thenReturn(headerCoinbase);

    assertThat(spec.getBlockReward()).isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header))
        .isEqualTo(headerCoinbase);
  }

  @Test
  public void negativeBlockRewardThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(-3);
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(this::createProtocolSchedule)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bft Block reward in config cannot be negative");
  }

  @Test
  public void zeroEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(configOptions.getEpochLength()).thenReturn(0L);
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(this::createProtocolSchedule)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void negativeEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(configOptions.getEpochLength()).thenReturn(-3000L);
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.getTransitions()).thenReturn(TransitionsConfigOptions.DEFAULT);

    assertThatThrownBy(this::createProtocolSchedule)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void blockRewardSpecifiedInTransitionCreatesNewMilestone() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(5);
    final String miningBeneficiary = Address.fromHexString("0x1").toString();
    final BftConfigOptions configOptions = mock(BftConfigOptions.class);
    when(configOptions.getMiningBeneficiary()).thenReturn(Optional.of(miningBeneficiary));
    when(configOptions.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(configOptions.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getBftConfigOptions()).thenReturn(configOptions);
    when(genesisConfig.isIbft2()).thenReturn(true);

    final long transitionBlock = 5L;
    final BftFork fork = mock(BftFork.class);
    when(fork.getForkBlock()).thenReturn(transitionBlock);
    final BigInteger forkBlockReward = arbitraryBlockReward.multiply(BigInteger.valueOf(2));
    when(fork.getBlockRewardWei()).thenReturn(Optional.of(forkBlockReward));
    final TransitionsConfigOptions transitions = mock(TransitionsConfigOptions.class);
    when(transitions.getIbftForks()).thenReturn(List.of(fork));
    when(genesisConfig.getTransitions()).thenReturn(transitions);

    final MutableProtocolSchedule schedule = (MutableProtocolSchedule) createProtocolSchedule();

    assertThat(schedule.streamMilestoneBlocks().count()).isEqualTo(2);
    assertThat(schedule.getByBlockNumber(0).getBlockReward())
        .isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(schedule.getByBlockNumber(transitionBlock).getBlockReward())
        .isEqualTo(Wei.of(forkBlockReward));
  }

  private ProtocolSchedule createProtocolSchedule() {
    final BaseBftProtocolSchedule bftProtocolSchedule =
        new BaseBftProtocolSchedule() {
          @Override
          protected Supplier<BlockHeaderValidator.Builder> createForkBlockHeaderRuleset(
              final GenesisConfigOptions config, final BftFork fork) {
            return BlockHeaderValidator.Builder::new;
          }

          @Override
          protected Supplier<BlockHeaderValidator.Builder> createGenesisBlockHeaderRuleset(
              final GenesisConfigOptions config) {
            return BlockHeaderValidator.Builder::new;
          }
        };
    return bftProtocolSchedule.createProtocolSchedule(
        genesisConfig,
        PrivacyParameters.DEFAULT,
        false,
        bftExtraDataCodec,
        EvmConfiguration.DEFAULT);
  }
}
