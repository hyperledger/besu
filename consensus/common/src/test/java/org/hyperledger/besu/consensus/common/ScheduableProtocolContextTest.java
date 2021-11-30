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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

public class ScheduableProtocolContextTest {

  @Test
  public void returnsContextForSpecificChainHeight() {
    final MutableBlockchain blockchain = Mockito.mock(MutableBlockchain.class);
    final WorldStateArchive worldStateArchive = Mockito.mock(WorldStateArchive.class);

    final ConsensusContext context1 = Mockito.mock(ConsensusContext.class);
    when(context1.as(any())).thenReturn(context1);

    final ConsensusContext context2 = Mockito.mock(ConsensusContext.class);
    when(context2.as(any())).thenReturn(context2);

    final BftForksSchedule<ConsensusContext> contextSchedule =
        new BftForksSchedule<>(
            new ForkSpec<>(0L, context1), List.of(new ForkSpec<>(10L, context2)));
    final ScheduableProtocolContext scheduableProtocolContext =
        new ScheduableProtocolContext(blockchain, worldStateArchive, contextSchedule);

    assertThat(scheduableProtocolContext.getConsensusContext(ConsensusContext.class))
        .isSameAs(context1);

    when(blockchain.getChainHeadBlockNumber()).thenReturn(2L);
    assertThat(scheduableProtocolContext.getConsensusContext(ConsensusContext.class))
        .isSameAs(context1);

    when(blockchain.getChainHeadBlockNumber()).thenReturn(10L);
    assertThat(scheduableProtocolContext.getConsensusContext(ConsensusContext.class))
        .isSameAs(context2);
  }
}
