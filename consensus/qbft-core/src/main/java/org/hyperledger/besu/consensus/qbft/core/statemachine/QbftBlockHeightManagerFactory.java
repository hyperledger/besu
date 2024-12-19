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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidatorFactory;
import org.hyperledger.besu.consensus.qbft.core.validator.ValidatorModeTransitionLogger;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Qbft block height manager factory. */
public class QbftBlockHeightManagerFactory {

  private static final Logger LOG = LoggerFactory.getLogger(QbftBlockHeightManagerFactory.class);

  private final QbftRoundFactory roundFactory;
  private final BftFinalState finalState;
  private final MessageValidatorFactory messageValidatorFactory;
  private final MessageFactory messageFactory;
  private final ValidatorModeTransitionLogger validatorModeTransitionLogger;

  /**
   * Instantiates a new Qbft block height manager factory.
   *
   * @param finalState the final state
   * @param roundFactory the round factory
   * @param messageValidatorFactory the message validator factory
   * @param messageFactory the message factory
   * @param validatorModeTransitionLogger the validator mode transition logger
   */
  public QbftBlockHeightManagerFactory(
      final BftFinalState finalState,
      final QbftRoundFactory roundFactory,
      final MessageValidatorFactory messageValidatorFactory,
      final MessageFactory messageFactory,
      final ValidatorModeTransitionLogger validatorModeTransitionLogger) {
    this.roundFactory = roundFactory;
    this.finalState = finalState;
    this.messageValidatorFactory = messageValidatorFactory;
    this.messageFactory = messageFactory;
    this.validatorModeTransitionLogger = validatorModeTransitionLogger;
  }

  /**
   * Create base qbft block height manager.
   *
   * @param parentHeader the parent header
   * @return the base qbft block height manager
   */
  public BaseQbftBlockHeightManager create(final BlockHeader parentHeader) {
    validatorModeTransitionLogger.logTransitionChange(parentHeader);

    if (finalState.isLocalNodeValidator()) {
      LOG.debug("Local node is a validator");
      return createFullBlockHeightManager(parentHeader);
    } else {
      LOG.debug("Local node is a non-validator");
      return createNoOpBlockHeightManager(parentHeader);
    }
  }

  private BaseQbftBlockHeightManager createNoOpBlockHeightManager(final BlockHeader parentHeader) {
    return new NoOpBlockHeightManager(parentHeader);
  }

  private BaseQbftBlockHeightManager createFullBlockHeightManager(final BlockHeader parentHeader) {
    return new QbftBlockHeightManager(
        parentHeader,
        finalState,
        new RoundChangeManager(
            BftHelpers.calculateRequiredValidatorQuorum(finalState.getValidators().size()),
            messageValidatorFactory.createRoundChangeMessageValidator(
                parentHeader.getNumber() + 1L, parentHeader),
            finalState.getLocalAddress()),
        roundFactory,
        finalState.getClock(),
        messageValidatorFactory,
        messageFactory);
  }
}
