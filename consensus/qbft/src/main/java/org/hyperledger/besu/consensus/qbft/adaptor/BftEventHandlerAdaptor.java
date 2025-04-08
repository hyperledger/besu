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

import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.consensus.qbft.core.types.QbftEventHandler;
import org.hyperledger.besu.consensus.qbft.core.types.QbftNewChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;

/** Adaptor class to allow a {@link QbftEventHandler} to be used as a {@link BftEventHandler}. */
public class BftEventHandlerAdaptor implements BftEventHandler {
  private final QbftEventHandler qbftEventHandler;

  /**
   * Create a new instance of the adaptor.
   *
   * @param qbftEventHandler The {@link QbftEventHandler} to adapt.
   */
  public BftEventHandlerAdaptor(final QbftEventHandler qbftEventHandler) {
    this.qbftEventHandler = qbftEventHandler;
  }

  @Override
  public void start() {
    qbftEventHandler.start();
  }

  @Override
  public void stop() {
    qbftEventHandler.stop();
  }

  @Override
  public void handleMessageEvent(final BftReceivedMessageEvent msg) {
    qbftEventHandler.handleMessageEvent(msg);
  }

  @Override
  public void handleNewBlockEvent(final NewChainHead newChainHead) {
    BlockHeader besuNewChainHeadHeader = newChainHead.getNewChainHeadHeader();
    var qbftChainHead = new QbftNewChainHead(new QbftBlockHeaderAdaptor(besuNewChainHeadHeader));
    qbftEventHandler.handleNewBlockEvent(qbftChainHead);
  }

  @Override
  public void handleBlockTimerExpiry(final BlockTimerExpiry blockTimerExpiry) {
    qbftEventHandler.handleBlockTimerExpiry(blockTimerExpiry);
  }

  @Override
  public void handleRoundExpiry(final RoundExpiry roundExpiry) {
    qbftEventHandler.handleRoundExpiry(roundExpiry);
  }
}
