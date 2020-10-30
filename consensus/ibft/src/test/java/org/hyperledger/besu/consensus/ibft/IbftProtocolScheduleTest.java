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

package org.hyperledger.besu.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.IbftConfigOptions;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;

public class IbftProtocolScheduleTest {

  private final GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);

  @Test
  public void ensureBlockRewardAndMiningBeneficiaryInProtocolSpecMatchConfig() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(5);
    final String miningBeneficiary = Address.fromHexString("0x1").toString();
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.of(miningBeneficiary));
    when(ibftConfig.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(ibftConfig.getEpochLength()).thenReturn(3000L);

    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);

    final ProtocolSchedule schedule = IbftProtocolSchedule.create(genesisConfig);
    final ProtocolSpec spec = schedule.getByBlockNumber(1);

    spec.getBlockReward();

    assertThat(spec.getBlockReward()).isEqualTo(Wei.of(arbitraryBlockReward));
    assertThat(spec.getMiningBeneficiaryCalculator().calculateBeneficiary(null))
        .isEqualTo(Address.fromHexString(miningBeneficiary));
  }

  @Test
  public void illegalMiningBeneficiaryStringThrowsException() {
    final String miningBeneficiary = "notHexStringOfTwentyBytes";
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.of(miningBeneficiary));
    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);
    when(ibftConfig.getEpochLength()).thenReturn(3000L);
    when(ibftConfig.getBlockRewardWei()).thenReturn(BigInteger.ZERO);

    assertThatThrownBy(() -> IbftProtocolSchedule.create(genesisConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mining beneficiary in config is not a valid ethereum address");
  }

  @Test
  public void missingMiningBeneficiaryInConfigWillPayCoinbaseInHeader() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(ibftConfig.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(ibftConfig.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);

    final ProtocolSchedule schedule = IbftProtocolSchedule.create(genesisConfig);
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
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(ibftConfig.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(ibftConfig.getEpochLength()).thenReturn(3000L);
    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);

    assertThatThrownBy(() -> IbftProtocolSchedule.create(genesisConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ibft2 Block reward in config cannot be negative");
  }

  @Test
  public void zeroEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(ibftConfig.getEpochLength()).thenReturn(0L);
    when(ibftConfig.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);

    assertThatThrownBy(() -> IbftProtocolSchedule.create(genesisConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void negativeEpochLengthThrowsException() {
    final BigInteger arbitraryBlockReward = BigInteger.valueOf(3);
    final IbftConfigOptions ibftConfig = mock(IbftConfigOptions.class);
    when(ibftConfig.getMiningBeneficiary()).thenReturn(Optional.empty());
    when(ibftConfig.getEpochLength()).thenReturn(-3000L);
    when(ibftConfig.getBlockRewardWei()).thenReturn(arbitraryBlockReward);
    when(genesisConfig.getIbft2ConfigOptions()).thenReturn(ibftConfig);

    assertThatThrownBy(() -> IbftProtocolSchedule.create(genesisConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }
}
