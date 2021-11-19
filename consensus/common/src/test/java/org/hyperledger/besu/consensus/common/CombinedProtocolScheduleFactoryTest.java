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
package org.hyperledger.besu.consensus.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CombinedProtocolScheduleFactoryTest {

  private final CombinedProtocolScheduleFactory combinedProtocolScheduleFactory =
      new CombinedProtocolScheduleFactory();

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromSingleProtocolSchedule() {
    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.homesteadBlock(5L);
    genesisConfigOptions.constantinopleBlock(10L);
    genesisConfigOptions.chainId(BigInteger.TEN);
    final ProtocolSchedule protocolSchedule = createProtocolSchedule(genesisConfigOptions);

    final NavigableSet<ForkSpec<ProtocolSchedule>> consensusSchedule =
        new TreeSet<>(ForkSpec.COMPARATOR);
    consensusSchedule.add(new ForkSpec<>(0, protocolSchedule));

    final ProtocolSchedule combinedProtocolSchedule =
        combinedProtocolScheduleFactory.create(consensusSchedule, Optional.of(BigInteger.TEN));

    assertThat(combinedProtocolSchedule.getByBlockNumber(0L).getName()).isEqualTo("Frontier");
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L))
        .isSameAs(protocolSchedule.getByBlockNumber(0L));

    assertThat(combinedProtocolSchedule.getByBlockNumber(5L).getName()).isEqualTo("Homestead");
    assertThat(combinedProtocolSchedule.getByBlockNumber(5L))
        .isSameAs(protocolSchedule.getByBlockNumber(5L));

    assertThat(combinedProtocolSchedule.getByBlockNumber(10L).getName())
        .isEqualTo("Constantinople");
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L))
        .isSameAs(protocolSchedule.getByBlockNumber(10L));

    assertThat(combinedProtocolSchedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L));
  }

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromMultipleSchedules() {
    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.homesteadBlock(5L);
    genesisConfigOptions.constantinopleBlock(10L);
    genesisConfigOptions.byzantiumBlock(105L);
    genesisConfigOptions.berlinBlock(110L);
    genesisConfigOptions.londonBlock(220L);
    genesisConfigOptions.chainId(BigInteger.TEN);

    final ProtocolSchedule protocolSchedule1 = createProtocolSchedule(genesisConfigOptions);
    final ProtocolSchedule protocolSchedule2 = createProtocolSchedule(genesisConfigOptions);
    final ProtocolSchedule protocolSchedule3 = createProtocolSchedule(genesisConfigOptions);

    final NavigableSet<ForkSpec<ProtocolSchedule>> consensusSchedule =
        new TreeSet<>(ForkSpec.COMPARATOR);
    consensusSchedule.add(new ForkSpec<>(0, protocolSchedule1));
    consensusSchedule.add(new ForkSpec<>(100L, protocolSchedule2));
    consensusSchedule.add(new ForkSpec<>(200L, protocolSchedule3));

    final ProtocolSchedule combinedProtocolSchedule =
        combinedProtocolScheduleFactory.create(consensusSchedule, Optional.of(BigInteger.TEN));

    // consensus schedule 1
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L).getName()).isEqualTo("Frontier");
    assertThat(combinedProtocolSchedule.getByBlockNumber(0L))
        .isSameAs(protocolSchedule1.getByBlockNumber(0L));

    assertThat(combinedProtocolSchedule.getByBlockNumber(5L).getName()).isEqualTo("Homestead");
    assertThat(combinedProtocolSchedule.getByBlockNumber(5L))
        .isSameAs(protocolSchedule1.getByBlockNumber(5L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L).getName())
        .isEqualTo("Constantinople");
    assertThat(combinedProtocolSchedule.getByBlockNumber(10L))
        .isSameAs(protocolSchedule1.getByBlockNumber(10L));

    // consensus schedule 2 migration block
    assertThat(combinedProtocolSchedule.getByBlockNumber(100L).getName())
        .isEqualTo("Constantinople");
    assertThat(combinedProtocolSchedule.getByBlockNumber(100L))
        .isSameAs(protocolSchedule2.getByBlockNumber(10L));

    // consensus schedule 2
    assertThat(combinedProtocolSchedule.getByBlockNumber(105L).getName()).isEqualTo("Byzantium");
    assertThat(combinedProtocolSchedule.getByBlockNumber(105L))
        .isSameAs(protocolSchedule2.getByBlockNumber(105L));
    assertThat(combinedProtocolSchedule.getByBlockNumber(110L).getName()).isEqualTo("Berlin");
    assertThat(combinedProtocolSchedule.getByBlockNumber(110L))
        .isSameAs(protocolSchedule2.getByBlockNumber(110L));

    // consensus schedule 3 migration block
    assertThat(combinedProtocolSchedule.getByBlockNumber(200L).getName()).isEqualTo("Berlin");
    assertThat(combinedProtocolSchedule.getByBlockNumber(200L))
        .isSameAs(protocolSchedule3.getByBlockNumber(110L));

    // consensus schedule 3
    assertThat(combinedProtocolSchedule.getByBlockNumber(220L).getName()).isEqualTo("London");
    assertThat(combinedProtocolSchedule.getByBlockNumber(220L))
        .isSameAs(protocolSchedule3.getByBlockNumber(220L));

    assertThat(combinedProtocolSchedule.streamMilestoneBlocks().collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L, 100L, 105L, 110L, 200L, 220L));
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
