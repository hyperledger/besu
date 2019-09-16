/*
 * Copyright 2019 ConsenSys AG.
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

import org.hyperledger.besu.consensus.ibft.ibftevent.BlockTimerExpiry;
import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvent;
import org.hyperledger.besu.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import org.hyperledger.besu.consensus.ibft.ibftevent.NewChainHead;
import org.hyperledger.besu.consensus.ibft.ibftevent.RoundExpiry;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EventMultiplexer {

  private static final Logger LOG = LogManager.getLogger();

  private final IbftController ibftController;

  public EventMultiplexer(final IbftController ibftController) {
    this.ibftController = ibftController;
  }

  public void handleIbftEvent(final IbftEvent ibftEvent) {
    try {
      switch (ibftEvent.getType()) {
        case MESSAGE:
          final IbftReceivedMessageEvent rxEvent = (IbftReceivedMessageEvent) ibftEvent;
          ibftController.handleMessageEvent(rxEvent);
          break;
        case ROUND_EXPIRY:
          final RoundExpiry roundExpiryEvent = (RoundExpiry) ibftEvent;
          ibftController.handleRoundExpiry(roundExpiryEvent);
          break;
        case NEW_CHAIN_HEAD:
          final NewChainHead newChainHead = (NewChainHead) ibftEvent;
          ibftController.handleNewBlockEvent(newChainHead);
          break;
        case BLOCK_TIMER_EXPIRY:
          final BlockTimerExpiry blockTimerExpiry = (BlockTimerExpiry) ibftEvent;
          ibftController.handleBlockTimerExpiry(blockTimerExpiry);
          break;
        default:
          throw new RuntimeException("Illegal event in queue.");
      }
    } catch (final Exception e) {
      LOG.error("State machine threw exception while processing event {" + ibftEvent + "}", e);
    }
  }
}
