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
package org.hyperledger.besu.consensus.ibft.validation;

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Round change message validator. */
public class RoundChangeMessageValidator {

  private static final Logger LOG = LoggerFactory.getLogger(RoundChangeMessageValidator.class);

  private final RoundChangePayloadValidator roundChangePayloadValidator;
  private final ProposalBlockConsistencyValidator proposalBlockConsistencyValidator;
  private final BftBlockInterface bftBlockInterface;

  /**
   * Instantiates a new Round change message validator.
   *
   * @param roundChangePayloadValidator the round change payload validator
   * @param proposalBlockConsistencyValidator the proposal block consistency validator
   * @param bftBlockInterface the bft block interface
   */
  public RoundChangeMessageValidator(
      final RoundChangePayloadValidator roundChangePayloadValidator,
      final ProposalBlockConsistencyValidator proposalBlockConsistencyValidator,
      final BftBlockInterface bftBlockInterface) {
    this.proposalBlockConsistencyValidator = proposalBlockConsistencyValidator;
    this.roundChangePayloadValidator = roundChangePayloadValidator;
    this.bftBlockInterface = bftBlockInterface;
  }

  /**
   * Validate round change.
   *
   * @param msg the msg
   * @return the boolean
   */
  public boolean validateRoundChange(final RoundChange msg) {

    if (!roundChangePayloadValidator.validateRoundChange(msg.getSignedPayload())) {
      LOG.info("Invalid RoundChange message, signed data did not validate correctly.");
      return false;
    }

    if (msg.getPreparedCertificate().isPresent() != msg.getProposedBlock().isPresent()) {
      LOG.info(
          "Invalid RoundChange message, availability of certificate does not correlate with availability of block.");
      return false;
    }

    if (msg.getPreparedCertificate().isPresent()) {
      if (!proposalBlockConsistencyValidator.validateProposalMatchesBlock(
          msg.getPreparedCertificate().get().getProposalPayload(),
          msg.getProposedBlock().get(),
          bftBlockInterface)) {
        LOG.info("Invalid RoundChange message, proposal did not align with supplied block.");
        return false;
      }
    }

    return true;
  }
}
