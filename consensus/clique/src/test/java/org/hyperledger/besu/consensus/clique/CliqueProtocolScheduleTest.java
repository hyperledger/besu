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
package org.hyperledger.besu.consensus.clique;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.time.Instant;

import org.junit.Test;

public class CliqueProtocolScheduleTest {

  private static final NodeKey NODE_KEY = NodeKeyUtils.generate();
  private final GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);

  @Test
  public void protocolSpecsAreCreatedAtBlockDefinedInJson() {
    final String jsonInput =
        "{\"config\": "
            + "{\"chainId\": 4,\n"
            + "\"homesteadBlock\": 1,\n"
            + "\"eip150Block\": 2,\n"
            + "\"eip158Block\": 3,\n"
            + "\"byzantiumBlock\": 1035301}"
            + "}";

    final GenesisConfigOptions config = GenesisConfigFile.fromConfig(jsonInput).getConfigOptions();
    final ProtocolSchedule protocolSchedule =
        CliqueProtocolSchedule.create(config, NODE_KEY, false, EvmConfiguration.DEFAULT);

    final ProtocolSpec homesteadSpec = protocolSchedule.getByBlockNumber(1);
    final ProtocolSpec tangerineWhistleSpec = protocolSchedule.getByBlockNumber(2);
    final ProtocolSpec spuriousDragonSpec = protocolSchedule.getByBlockNumber(3);
    final ProtocolSpec byzantiumSpec = protocolSchedule.getByBlockNumber(1035301);

    assertThat(homesteadSpec.equals(tangerineWhistleSpec)).isFalse();
    assertThat(tangerineWhistleSpec.equals(spuriousDragonSpec)).isFalse();
    assertThat(spuriousDragonSpec.equals(byzantiumSpec)).isFalse();
  }

  @Test
  public void parametersAlignWithMainnetWithAdjustments() {
    final ProtocolSpec homestead =
        CliqueProtocolSchedule.create(
                GenesisConfigFile.DEFAULT.getConfigOptions(),
                NODE_KEY,
                false,
                EvmConfiguration.DEFAULT)
            .getByBlockNumber(0);

    assertThat(homestead.getName()).isEqualTo("Frontier");
    assertThat(homestead.getBlockReward()).isEqualTo(Wei.ZERO);
    assertThat(homestead.isSkipZeroBlockRewards()).isEqualTo(true);
    assertThat(homestead.getDifficultyCalculator()).isInstanceOf(CliqueDifficultyCalculator.class);
  }

  @Test
  public void zeroEpochLengthThrowsException() {
    final CliqueConfigOptions cliqueOptions = mock(CliqueConfigOptions.class);
    when(cliqueOptions.getEpochLength()).thenReturn(0L);
    when(genesisConfig.getCliqueConfigOptions()).thenReturn(cliqueOptions);

    assertThatThrownBy(
            () ->
                CliqueProtocolSchedule.create(
                    genesisConfig, NODE_KEY, false, EvmConfiguration.DEFAULT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void negativeEpochLengthThrowsException() {
    final CliqueConfigOptions cliqueOptions = mock(CliqueConfigOptions.class);
    when(cliqueOptions.getEpochLength()).thenReturn(-3000L);
    when(genesisConfig.getCliqueConfigOptions()).thenReturn(cliqueOptions);

    assertThatThrownBy(
            () ->
                CliqueProtocolSchedule.create(
                    genesisConfig, NODE_KEY, false, EvmConfiguration.DEFAULT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Epoch length in config must be greater than zero");
  }

  @Test
  public void shouldValidateBaseFeeMarketTransition() {

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final String jsonInput =
        "{\"config\": " + "\t{\"chainId\": 1337,\n" + "\t\"londonBlock\": 2}\n" + "}";

    final GenesisConfigOptions config = GenesisConfigFile.fromConfig(jsonInput).getConfigOptions();
    final ProtocolSchedule protocolSchedule =
        CliqueProtocolSchedule.create(config, NODE_KEY, false, EvmConfiguration.DEFAULT);

    BlockHeader emptyFrontierParent =
        headerBuilder
            .number(0)
            .mixHash(Hash.fromHexStringLenient("0x0"))
            .gasLimit(5000L)
            .timestamp(Instant.now().getEpochSecond() - 30L)
            .buildHeader();

    // legacy FeeMarket block
    BlockHeader emptyFrontierBlock1 =
        headerBuilder
            .number(1)
            .timestamp(Instant.now().getEpochSecond() - 15L)
            .parentHash(emptyFrontierParent.getHash())
            .buildHeader();

    // premature BaseFeeMarket block
    BlockHeader emptyLondonBlock1 =
        headerBuilder.baseFeePerGas(Wei.of(1000000000L)).gasLimit(10000L).buildHeader();

    // first BaseFeeMarket block
    BlockHeader emptyLondonBlock2 =
        headerBuilder
            .number(2)
            .timestamp(Instant.now().getEpochSecond())
            .parentHash(emptyFrontierBlock1.getHash())
            .buildHeader();

    // assert block 1 validates (no fee market)
    assertThat(
            validateHeaderByProtocolSchedule(
                protocolSchedule, emptyFrontierBlock1, emptyFrontierParent))
        .isTrue();

    // assert block 1 with a base fee fails
    assertThat(
            validateHeaderByProtocolSchedule(
                protocolSchedule, emptyLondonBlock1, emptyFrontierParent))
        .isFalse();

    // assert block 2 with a base fee validates (has fee market)
    assertThat(
            validateHeaderByProtocolSchedule(
                protocolSchedule, emptyLondonBlock2, emptyFrontierBlock1))
        .isTrue();
  }

  private boolean validateHeaderByProtocolSchedule(
      final ProtocolSchedule schedule,
      final BlockHeader blockHeader,
      final BlockHeader parentBlockHeader) {

    return schedule
        .getByBlockNumber(blockHeader.getNumber())
        .getBlockHeaderValidator()
        .validateHeader(blockHeader, parentBlockHeader, null, HeaderValidationMode.LIGHT);
  }
}
