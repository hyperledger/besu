/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.ibft;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvent;
import org.hyperledger.besu.consensus.ibft.ibftevent.NewChainHead;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class IbftChainObserverTest {
  @Test
  public void newChainHeadHeaderEventIsAddedToTheQueue() {
    final Blockchain mockBlockchain = mock(Blockchain.class);
    final IbftEventQueue mockQueue = mock(IbftEventQueue.class);
    final BlockAddedEvent mockBlockAddedEvent = mock(BlockAddedEvent.class);

    final IbftChainObserver ibftChainObserver = new IbftChainObserver(mockQueue);

    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(1234)
            .coinbase(Address.ECREC)
            .parentHash(Hash.EMPTY_LIST_HASH)
            .buildHeader();

    final Block block = new Block(header, new BlockBody(emptyList(), emptyList()));

    when(mockBlockAddedEvent.getEventType()).thenReturn(BlockAddedEvent.EventType.HEAD_ADVANCED);
    when(mockBlockAddedEvent.getBlock()).thenReturn(block);

    ibftChainObserver.onBlockAdded(mockBlockAddedEvent, mockBlockchain);

    final ArgumentCaptor<IbftEvent> ibftEventArgumentCaptor =
        ArgumentCaptor.forClass(IbftEvent.class);
    verify(mockQueue).add(ibftEventArgumentCaptor.capture());

    assertThat(ibftEventArgumentCaptor.getValue() instanceof NewChainHead).isTrue();
    assertThat(((NewChainHead) ibftEventArgumentCaptor.getValue()).getNewChainHeadHeader())
        .isEqualTo(header);
  }

  @Test(expected = IllegalStateException.class)
  public void exceptionIsThrownWhenEventTypeIsFork() {
    final Blockchain mockBlockchain = mock(Blockchain.class);
    final IbftEventQueue mockQueue = mock(IbftEventQueue.class);
    final BlockAddedEvent mockBlockAddedEvent = mock(BlockAddedEvent.class);

    when(mockBlockAddedEvent.getEventType()).thenReturn(BlockAddedEvent.EventType.FORK);

    final IbftChainObserver ibftChainObserver = new IbftChainObserver(mockQueue);

    ibftChainObserver.onBlockAdded(mockBlockAddedEvent, mockBlockchain);
  }

  @Test(expected = IllegalStateException.class)
  public void exceptionIsThrownWhenEventTypeIsChainReorg() {
    final Blockchain mockBlockchain = mock(Blockchain.class);
    final IbftEventQueue mockQueue = mock(IbftEventQueue.class);
    final BlockAddedEvent mockBlockAddedEvent = mock(BlockAddedEvent.class);

    when(mockBlockAddedEvent.getEventType()).thenReturn(BlockAddedEvent.EventType.CHAIN_REORG);

    final IbftChainObserver ibftChainObserver = new IbftChainObserver(mockQueue);

    ibftChainObserver.onBlockAdded(mockBlockAddedEvent, mockBlockchain);
  }
}
