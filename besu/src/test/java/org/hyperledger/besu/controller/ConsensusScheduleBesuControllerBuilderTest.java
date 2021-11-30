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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.SchedulableContext;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConsensusScheduleBesuControllerBuilderTest {
  private @Mock BiFunction<
          NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>
      combinedProtocolScheduleFactory;
  private @Mock GenesisConfigFile genesisConfigFile;
  private @Mock BesuControllerBuilder besuControllerBuilder1;
  private @Mock BesuControllerBuilder besuControllerBuilder2;
  private @Mock BesuControllerBuilder besuControllerBuilder3;
  private @Mock ProtocolSchedule protocolSchedule1;
  private @Mock ProtocolSchedule protocolSchedule2;
  private @Mock ProtocolSchedule protocolSchedule3;

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
  public void mustCreateCombinedProtocolScheduleUsingProtocolSchedulesOrderedByBlock() {
    // Use an ordered map with keys in the incorrect order so that we can show that set is created
    // with the correct order
    final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule = new TreeMap<>();
    besuControllerBuilderSchedule.put(30L, besuControllerBuilder3);
    besuControllerBuilderSchedule.put(10L, besuControllerBuilder2);
    besuControllerBuilderSchedule.put(0L, besuControllerBuilder1);

    when(besuControllerBuilder1.createProtocolSchedule()).thenReturn(protocolSchedule1);
    when(besuControllerBuilder2.createProtocolSchedule()).thenReturn(protocolSchedule2);
    when(besuControllerBuilder3.createProtocolSchedule()).thenReturn(protocolSchedule3);

    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.chainId(BigInteger.TEN);
    when(genesisConfigFile.getConfigOptions()).thenReturn(genesisConfigOptions);

    final ConsensusScheduleBesuControllerBuilder consensusScheduleBesuControllerBuilder =
        new ConsensusScheduleBesuControllerBuilder(
            besuControllerBuilderSchedule, combinedProtocolScheduleFactory);
    consensusScheduleBesuControllerBuilder.genesisConfigFile(genesisConfigFile);
    consensusScheduleBesuControllerBuilder.createProtocolSchedule();

    final NavigableSet<ForkSpec<ProtocolSchedule>> expectedProtocolSchedulesSpecs =
        new TreeSet<>(ForkSpec.COMPARATOR);
    expectedProtocolSchedulesSpecs.add(new ForkSpec<>(0L, protocolSchedule1));
    expectedProtocolSchedulesSpecs.add(new ForkSpec<>(10L, protocolSchedule2));
    expectedProtocolSchedulesSpecs.add(new ForkSpec<>(30L, protocolSchedule3));
    Mockito.verify(combinedProtocolScheduleFactory)
        .apply(expectedProtocolSchedulesSpecs, Optional.of(BigInteger.TEN));
  }

  @Test
  public void mustCreateSchedulableContext() {
    final ConsensusContext context1 = Mockito.mock(ConsensusContext.class);
    final ConsensusContext context2 = Mockito.mock(ConsensusContext.class);

    final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule = new TreeMap<>();
    besuControllerBuilderSchedule.put(0L, besuControllerBuilder1);
    besuControllerBuilderSchedule.put(10L, besuControllerBuilder2);

    when(besuControllerBuilder1.createConsensusContext(any(), any(), any())).thenReturn(context1);
    when(besuControllerBuilder2.createConsensusContext(any(), any(), any())).thenReturn(context2);

    final ConsensusScheduleBesuControllerBuilder controllerBuilder =
        new ConsensusScheduleBesuControllerBuilder(besuControllerBuilderSchedule);
    final ConsensusContext consensusContext =
        controllerBuilder.createConsensusContext(
            Mockito.mock(Blockchain.class),
            Mockito.mock(WorldStateArchive.class),
            Mockito.mock(ProtocolSchedule.class));

    assertThat(consensusContext).isInstanceOf(SchedulableContext.class);
    final SchedulableContext schedulableContext = (SchedulableContext) consensusContext;

    final BftForksSchedule<ConsensusContext> contextSchedule =
        schedulableContext.getConsensusContextSchedule();

    final NavigableSet<ForkSpec<ConsensusContext>> expectedConsensusContextSpecs =
        new TreeSet<>(ForkSpec.COMPARATOR);
    expectedConsensusContextSpecs.add(new ForkSpec<>(0L, context1));
    expectedConsensusContextSpecs.add(new ForkSpec<>(10L, context2));
    assertThat(contextSchedule.getForks()).isEqualTo(expectedConsensusContextSpecs);

    assertThat(contextSchedule.getFork(0).getValue()).isSameAs(context1);
    assertThat(contextSchedule.getFork(1).getValue()).isSameAs(context1);
    assertThat(contextSchedule.getFork(9).getValue()).isSameAs(context1);
    assertThat(contextSchedule.getFork(10).getValue()).isSameAs(context2);
    assertThat(contextSchedule.getFork(11).getValue()).isSameAs(context2);
  }
}
