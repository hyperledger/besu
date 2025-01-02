/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.ethereum.core.BlockHeader;

/** The Future round proposal message validator. */
public class FutureRoundProposalMessageValidator {

  private final MessageValidatorFactory messageValidatorFactory;
  private final long chainHeight;
  private final BlockHeader parentHeader;

  /**
   * Instantiates a new Future round proposal message validator.
   *
   * @param messageValidatorFactory the message validator factory
   * @param chainHeight the chain height
   * @param parentHeader the parent header
   */
  public FutureRoundProposalMessageValidator(
      final MessageValidatorFactory messageValidatorFactory,
      final long chainHeight,
      final BlockHeader parentHeader) {
    this.messageValidatorFactory = messageValidatorFactory;
    this.chainHeight = chainHeight;
    this.parentHeader = parentHeader;
  }

  /**
   * Validate proposal message.
   *
   * @param msg the msg
   * @return the boolean
   */
  public boolean validateProposalMessage(final Proposal msg) {
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(chainHeight, msg.getRoundIdentifier().getRoundNumber());

    final MessageValidator messageValidator =
        messageValidatorFactory.createMessageValidator(roundIdentifier, parentHeader);

    return messageValidator.validateProposal(msg);
  }
}
