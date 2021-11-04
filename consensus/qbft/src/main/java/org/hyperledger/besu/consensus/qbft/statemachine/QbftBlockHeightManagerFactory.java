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
package org.hyperledger.besu.consensus.qbft.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidatorFactory;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class QbftBlockHeightManagerFactory {

  private final QbftRoundFactory roundFactory;
  private final BftFinalState finalState;
  private final MessageValidatorFactory messageValidatorFactory;
  private final MessageFactory messageFactory;
  private final ValidatorModeTransitionLogger validatorModeTransitionLogger;

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

  public BaseQbftBlockHeightManager create(final BlockHeader parentHeader) {
    validatorModeTransitionLogger.logTransitionChange(parentHeader);

    if (finalState.isLocalNodeValidator()) {
      return createFullBlockHeightManager(parentHeader);
    } else {
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
                parentHeader.getNumber() + 1L, parentHeader)),
        roundFactory,
        finalState.getClock(),
        messageValidatorFactory,
        messageFactory);
  }
}
