/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.qbft.core.types.QbftEventHandler;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BftEventHandlerAdaptorTest {
  @Mock private QbftEventHandler qbftEventHandler;
  @Mock private BftReceivedMessageEvent bftReceivedMessageEvent;
  @Mock private BlockTimerExpiry blockTimerExpiry;
  @Mock private RoundExpiry roundExpiry;
  @Mock private NewChainHead newChainHead;
  private BftEventHandlerAdaptor handler;

  @BeforeEach
  void start() {
    handler = new BftEventHandlerAdaptor(qbftEventHandler);
  }

  @Test
  void startDelegatesToQbftEventHandler() {
    handler.start();
    verify(qbftEventHandler).start();
  }

  @Test
  void handleMessageEventDelegatesToQbftEventHandler() {
    handler.handleMessageEvent(bftReceivedMessageEvent);
    verify(qbftEventHandler).handleMessageEvent(bftReceivedMessageEvent);
  }

  @Test
  void handleBlockTimerExpiryDelegatesToQbftEventHandler() {
    handler.handleBlockTimerExpiry(blockTimerExpiry);
    verify(qbftEventHandler).handleBlockTimerExpiry(blockTimerExpiry);
  }

  @Test
  void handleRoundExpiryDelegatesToQbftEventHandler() {
    handler.handleRoundExpiry(roundExpiry);
    verify(qbftEventHandler).handleRoundExpiry(roundExpiry);
  }

  @Test
  void handleNewBlockEventDelegatesToQbftEventHandler() {
    BlockHeader header = new BlockHeaderTestFixture().buildHeader();
    when(newChainHead.getNewChainHeadHeader()).thenReturn(header);

    handler.handleNewBlockEvent(newChainHead);
    verify(qbftEventHandler)
        .handleNewBlockEvent(
            argThat(
                argument ->
                    ((QbftBlockHeaderAdaptor) argument.newChainHeadHeader())
                        .getBesuBlockHeader()
                        .equals(header)));
  }
}
