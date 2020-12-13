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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.consensus.common.bft.ibftevent.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.ibftevent.IbftEvent;
import org.hyperledger.besu.consensus.common.bft.ibftevent.IbftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.ibftevent.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.ibftevent.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EventMultiplexer {

  private static final Logger LOG = LogManager.getLogger();

  private final BftEventHandler eventHandler;

  public EventMultiplexer(final BftEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  public void handleIbftEvent(final IbftEvent ibftEvent) {
    try {
      switch (ibftEvent.getType()) {
        case MESSAGE:
          final IbftReceivedMessageEvent rxEvent = (IbftReceivedMessageEvent) ibftEvent;
          eventHandler.handleMessageEvent(rxEvent);
          break;
        case ROUND_EXPIRY:
          final RoundExpiry roundExpiryEvent = (RoundExpiry) ibftEvent;
          eventHandler.handleRoundExpiry(roundExpiryEvent);
          break;
        case NEW_CHAIN_HEAD:
          final NewChainHead newChainHead = (NewChainHead) ibftEvent;
          eventHandler.handleNewBlockEvent(newChainHead);
          break;
        case BLOCK_TIMER_EXPIRY:
          final BlockTimerExpiry blockTimerExpiry = (BlockTimerExpiry) ibftEvent;
          eventHandler.handleBlockTimerExpiry(blockTimerExpiry);
          break;
        default:
          throw new RuntimeException("Illegal event in queue.");
      }
    } catch (final Exception e) {
      LOG.error("State machine threw exception while processing event \\{" + ibftEvent + "\\}", e);
    }
  }
}
