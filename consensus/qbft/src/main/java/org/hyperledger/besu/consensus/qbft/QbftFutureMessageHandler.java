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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer.FutureMessageHandler;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Future message handler for QBFT messages that logs future message reception. Note: This handler
 * does not perform peer synchronization updates since QbftMessage does not contain connection
 * information.
 */
public class QbftFutureMessageHandler implements FutureMessageHandler<QbftMessage> {
  private static final Logger LOG = LoggerFactory.getLogger(QbftFutureMessageHandler.class);

  /** Default constructor. */
  public QbftFutureMessageHandler() {}

  @Override
  public void handleFutureMessage(final long msgChainHeight, final QbftMessage message) {
    LOG.debug("Received future QBFT message for chain height {}", msgChainHeight);
    // No peer synchronization updates needed for QbftMessage since it lacks connection info
  }
}
