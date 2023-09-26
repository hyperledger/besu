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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftProcessor;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BftMiningCoordinatorTest {
  @Mock private BftEventHandler controller;
  @Mock private BftExecutors bftExecutors;
  @Mock private BftProcessor bftProcessor;
  @Mock private BftBlockCreatorFactory<?> bftBlockCreatorFactory;
  @Mock private Blockchain blockChain;
  @Mock private Block block;
  @Mock private BlockBody blockBody;
  @Mock private BlockHeader blockHeader;
  private final BftEventQueue eventQueue = new BftEventQueue(1000);
  private BftMiningCoordinator bftMiningCoordinator;

  @Before
  public void setup() {
    bftMiningCoordinator =
        new BftMiningCoordinator(
            bftExecutors, controller, bftProcessor, bftBlockCreatorFactory, blockChain, eventQueue);
    when(block.getBody()).thenReturn(blockBody);
    when(block.getHeader()).thenReturn(blockHeader);
    when(blockBody.getTransactions()).thenReturn(Collections.emptyList());
  }

  @Test
  public void startsMining() {
    bftMiningCoordinator.start();
  }

  @Test
  public void stopsMining() {
    // Shouldn't stop without first starting
    bftMiningCoordinator.stop();
    verify(bftProcessor, never()).stop();

    bftMiningCoordinator.start();
    bftMiningCoordinator.stop();
    verify(bftProcessor).stop();
  }

  @Test
  public void getsMinTransactionGasPrice() {
    final Wei minGasPrice = Wei.of(10);
    when(bftBlockCreatorFactory.getMinTransactionGasPrice()).thenReturn(minGasPrice);
    assertThat(bftMiningCoordinator.getMinTransactionGasPrice()).isEqualTo(minGasPrice);
  }

  @Test
  public void setsTheExtraData() {
    final Bytes extraData = Bytes.fromHexStringLenient("0x1234");
    bftMiningCoordinator.setExtraData(extraData);
    verify(bftBlockCreatorFactory).setExtraData(extraData);
  }

  @Test
  public void addsNewChainHeadEventWhenNewCanonicalHeadBlockEventReceived() throws Exception {
    BlockAddedEvent headAdvancement =
        BlockAddedEvent.createForHeadAdvancement(
            block, Collections.emptyList(), Collections.emptyList());
    bftMiningCoordinator.onBlockAdded(headAdvancement);

    assertThat(eventQueue.size()).isEqualTo(1);
    final NewChainHead ibftEvent = (NewChainHead) eventQueue.poll(1, TimeUnit.SECONDS);
    assertThat(ibftEvent.getNewChainHeadHeader()).isEqualTo(blockHeader);
  }

  @Test
  public void doesntAddNewChainHeadEventWhenNotACanonicalHeadBlockEvent() {
    final BlockAddedEvent fork = BlockAddedEvent.createForFork(block);
    bftMiningCoordinator.onBlockAdded(fork);
    assertThat(eventQueue.isEmpty()).isTrue();
  }
}
