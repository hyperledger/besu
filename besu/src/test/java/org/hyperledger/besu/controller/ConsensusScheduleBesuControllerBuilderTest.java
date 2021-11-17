/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConsensusScheduleBesuControllerBuilderTest {
  @Mock private GenesisConfigFile genesisConfigFile;
  @Mock private BesuControllerBuilder delegateBesuControllerBuilder1;
  @Mock private BesuControllerBuilder delegateBesuControllerBuilder2;

  @Test
  public void mustProvideNonNullConsensusScheduleWhenInstantiatingNew() {
    assertThatThrownBy(() -> new ConsensusScheduleBesuControllerBuilder(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("BesuControllerBuilder schedule can't be null");
  }

  @Test
  public void mustProvideNonEmptyConsensusScheduleWhenInstantiatingNew() {
    assertThatThrownBy(() -> new ConsensusScheduleBesuControllerBuilder(Collections.emptyMap()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("BesuControllerBuilder schedule can't be empty");
  }

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromSingleProtocolSchedule() {
    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.homesteadBlock(5L);
    genesisConfigOptions.constantinopleBlock(10L);
    final ProtocolSchedule protocolSchedule = createProtocolSchedule(genesisConfigOptions);

    final Map<Long, BesuControllerBuilder> schedule = new HashMap<>();
    schedule.put(0L, delegateBesuControllerBuilder1);
    when(delegateBesuControllerBuilder1.createProtocolSchedule()).thenReturn(protocolSchedule);

    final StubGenesisConfigOptions genesisConfigOptionsForChainId = new StubGenesisConfigOptions();
    genesisConfigOptionsForChainId.chainId(BigInteger.TEN);
    when(genesisConfigFile.getConfigOptions()).thenReturn(genesisConfigOptionsForChainId);

    final ConsensusScheduleBesuControllerBuilder controllerBuilder =
        new ConsensusScheduleBesuControllerBuilder(schedule);
    controllerBuilder.genesisConfigFile(genesisConfigFile);
    final ProtocolSchedule combinedProtocolSchedule = controllerBuilder.createProtocolSchedule();
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule.getByBlockNumber(0L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(5L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule.getByBlockNumber(5L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule.getByBlockNumber(10L));
    assertThat(combinedProtocolSchedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L));
  }

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromMultipleSchedules() {
    final StubGenesisConfigOptions genesisConfigOptions1 = new StubGenesisConfigOptions();
    genesisConfigOptions1.homesteadBlock(5L);
    genesisConfigOptions1.constantinopleBlock(10L);
    genesisConfigOptions1.chainId(BigInteger.TEN);
    final ProtocolSchedule protocolSchedule1 = createProtocolSchedule(genesisConfigOptions1);

    final StubGenesisConfigOptions genesisConfigOptions2 = new StubGenesisConfigOptions();
    genesisConfigOptions2.byzantiumBlock(0L);
    genesisConfigOptions2.berlinBlock(5L);
    final ProtocolSchedule protocolSchedule2 = createProtocolSchedule(genesisConfigOptions2);

    final Map<Long, BesuControllerBuilder> schedule = new HashMap<>();
    schedule.put(0L, delegateBesuControllerBuilder1);
    schedule.put(100L, delegateBesuControllerBuilder2);
    when(delegateBesuControllerBuilder2.createProtocolSchedule()).thenReturn(protocolSchedule2);
    when(delegateBesuControllerBuilder1.createProtocolSchedule()).thenReturn(protocolSchedule1);

    final StubGenesisConfigOptions genesisConfigOptionsForChainId = new StubGenesisConfigOptions();
    genesisConfigOptionsForChainId.chainId(BigInteger.TEN);
    when(genesisConfigFile.getConfigOptions()).thenReturn(genesisConfigOptionsForChainId);

    final ConsensusScheduleBesuControllerBuilder controllerBuilder =
        new ConsensusScheduleBesuControllerBuilder(schedule);
    controllerBuilder.genesisConfigFile(genesisConfigFile);
    final ProtocolSchedule combinedProtocolSchedule = controllerBuilder.createProtocolSchedule();

    // schedule 1
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L).getName()).isEqualTo("Frontier");
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule1.getByBlockNumber(0L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(5L).getName()).isEqualTo("Homestead");
    assertThat(combinedProtocolSchedule.getByBlockNumber(5L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule1.getByBlockNumber(5L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L).getName())
        .isEqualTo("Constantinople");
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule1.getByBlockNumber(10L));

    // schedule 2
    assertThat(combinedProtocolSchedule.getByBlockNumber(100L).getName()).isEqualTo("Byzantium");
    assertThat(combinedProtocolSchedule.getByBlockNumber(100L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule2.getByBlockNumber(0L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(105L).getName()).isEqualTo("Berlin");
    assertThat(combinedProtocolSchedule.getByBlockNumber(105L))
        .usingRecursiveComparison()
        .isEqualTo(protocolSchedule2.getByBlockNumber(5L));

    assertThat(combinedProtocolSchedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L, 100L, 105L));
  }

  private ProtocolSchedule createProtocolSchedule(final GenesisConfigOptions genesisConfigOptions) {
    final ProtocolScheduleBuilder protocolScheduleBuilder =
        new ProtocolScheduleBuilder(
            genesisConfigOptions,
            BigInteger.ONE,
            ProtocolSpecAdapters.create(0, Function.identity()),
            new PrivacyParameters(),
            false,
            false,
            EvmConfiguration.DEFAULT);

    return protocolScheduleBuilder.createProtocolSchedule();
  }
}
