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
package org.hyperledger.besu.consensus.ibft.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.IbftEventQueue;
import org.hyperledger.besu.consensus.ibft.IbftProcessor;
import org.hyperledger.besu.consensus.ibft.ibftevent.NewChainHead;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.concurrent.TimeUnit;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftMiningCoordinatorTest {
  @Mock private IbftProcessor ibftProcessor;
  @Mock private IbftBlockCreatorFactory ibftBlockCreatorFactory;
  @Mock private Blockchain blockChain;
  @Mock private Block block;
  @Mock private BlockBody blockBody;
  @Mock private BlockHeader blockHeader;
  private final IbftEventQueue eventQueue = new IbftEventQueue(1000);
  private IbftMiningCoordinator ibftMiningCoordinator;

  @Before
  public void setup() {
    ibftMiningCoordinator =
        new IbftMiningCoordinator(ibftProcessor, ibftBlockCreatorFactory, blockChain, eventQueue);
    when(block.getBody()).thenReturn(blockBody);
    when(block.getHeader()).thenReturn(blockHeader);
    when(blockBody.getTransactions()).thenReturn(Lists.emptyList());
  }

  @Test
  public void enablesMining() {
    ibftMiningCoordinator.enable();
  }

  @Test
  public void disablesMining() {
    ibftMiningCoordinator.disable();
    verify(ibftProcessor).stop();
  }

  @Test
  public void getsMinTransactionGasPrice() {
    final Wei minGasPrice = Wei.of(10);
    when(ibftBlockCreatorFactory.getMinTransactionGasPrice()).thenReturn(minGasPrice);
    assertThat(ibftMiningCoordinator.getMinTransactionGasPrice()).isEqualTo(minGasPrice);
  }

  @Test
  public void setsTheExtraData() {
    final BytesValue extraData = BytesValue.fromHexStringLenient("0x1234");
    ibftMiningCoordinator.setExtraData(extraData);
    verify(ibftBlockCreatorFactory).setExtraData(extraData);
  }

  @Test
  public void addsNewChainHeadEventWhenNewCanonicalHeadBlockEventReceived() throws Exception {
    BlockAddedEvent headAdvancement = BlockAddedEvent.createForHeadAdvancement(block);
    ibftMiningCoordinator.onBlockAdded(headAdvancement, blockChain);

    assertThat(eventQueue.size()).isEqualTo(1);
    final NewChainHead ibftEvent = (NewChainHead) eventQueue.poll(1, TimeUnit.SECONDS);
    assertThat(ibftEvent.getNewChainHeadHeader()).isEqualTo(blockHeader);
  }

  @Test
  public void doesntAddNewChainHeadEventWhenNotACanonicalHeadBlockEvent() {
    final BlockAddedEvent fork = BlockAddedEvent.createForFork(block);
    ibftMiningCoordinator.onBlockAdded(fork, blockChain);
    assertThat(eventQueue.isEmpty()).isTrue();
  }
}
