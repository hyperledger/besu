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

import org.hyperledger.besu.consensus.common.bft.events.BftEvent;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Event multiplexer. */
public class EventMultiplexer {

  private static final Logger LOG = LoggerFactory.getLogger(EventMultiplexer.class);

  private final BftEventHandler eventHandler;

  /**
   * Instantiates a new Event multiplexer.
   *
   * @param eventHandler the event handler
   */
  public EventMultiplexer(final BftEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  /**
   * Handle bft event.
   *
   * @param bftEvent the bft event
   */
  public void handleBftEvent(final BftEvent bftEvent) {
    try {
      switch (bftEvent.getType()) {
        case MESSAGE:
          final BftReceivedMessageEvent rxEvent = (BftReceivedMessageEvent) bftEvent;
          eventHandler.handleMessageEvent(rxEvent);
          break;
        case ROUND_EXPIRY:
          final RoundExpiry roundExpiryEvent = (RoundExpiry) bftEvent;
          eventHandler.handleRoundExpiry(roundExpiryEvent);
          break;
        case NEW_CHAIN_HEAD:
          final NewChainHead newChainHead = (NewChainHead) bftEvent;
          eventHandler.handleNewBlockEvent(newChainHead);
          break;
        case BLOCK_TIMER_EXPIRY:
          final BlockTimerExpiry blockTimerExpiry = (BlockTimerExpiry) bftEvent;
          eventHandler.handleBlockTimerExpiry(blockTimerExpiry);
          break;
        default:
          throw new RuntimeException("Illegal event in queue.");
      }
    } catch (final Exception e) {
      LOG.error("State machine threw exception while processing event \\{" + bftEvent + "\\}", e);
    }
  }
}
