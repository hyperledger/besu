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
package org.hyperledger.besu.consensus.qbft.validation;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageValidator {

  private static final Logger LOG = LoggerFactory.getLogger(MessageValidator.class);

  public static class SubsequentMessageValidator {

    private final PrepareValidator prepareValidator;
    private final CommitValidator commitValidator;

    public SubsequentMessageValidator(
        final Collection<Address> validators,
        final ConsensusRoundIdentifier targetRound,
        final Block proposalBlock,
        final BftBlockInterface blockInterface,
        final BftExtraDataCodec bftExtraDataCodec) {
      final Block commitBlock =
          blockInterface.replaceRoundInBlock(
              proposalBlock,
              targetRound.getRoundNumber(),
              BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
      prepareValidator = new PrepareValidator(validators, targetRound, proposalBlock.getHash());
      commitValidator =
          new CommitValidator(
              validators, targetRound, proposalBlock.getHash(), commitBlock.getHash());
    }

    public boolean validate(final Prepare msg) {
      return prepareValidator.validate(msg);
    }

    public boolean validate(final Commit msg) {
      return commitValidator.validate(msg);
    }
  }

  @FunctionalInterface
  public interface SubsequentMessageValidatorFactory {
    SubsequentMessageValidator create(Block proposalBlock);
  }

  private final SubsequentMessageValidatorFactory subsequentMessageValidatorFactory;
  private final ProposalValidator proposalValidator;

  private Optional<SubsequentMessageValidator> subsequentMessageValidator = Optional.empty();

  public MessageValidator(
      final SubsequentMessageValidatorFactory subsequentMessageValidatorFactory,
      final ProposalValidator proposalValidator) {
    this.subsequentMessageValidatorFactory = subsequentMessageValidatorFactory;
    this.proposalValidator = proposalValidator;
  }

  public boolean validateProposal(final Proposal msg) {
    if (subsequentMessageValidator.isPresent()) {
      LOG.info("Received subsequent Proposal for current round, discarding.");
      return false;
    }

    final boolean result = proposalValidator.validate(msg);
    if (result) {
      subsequentMessageValidator =
          Optional.of(subsequentMessageValidatorFactory.create(msg.getBlock()));
    }

    return result;
  }

  public boolean validatePrepare(final Prepare msg) {
    return subsequentMessageValidator.map(pv -> pv.validate(msg)).orElse(false);
  }

  public boolean validateCommit(final Commit msg) {
    return subsequentMessageValidator.map(cv -> cv.validate(msg)).orElse(false);
  }
}
