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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Message validator. */
public class MessageValidator {

  private static final Logger LOG = LoggerFactory.getLogger(MessageValidator.class);

  /** The Subsequent message validator. */
  public static class SubsequentMessageValidator {

    private final PrepareValidator prepareValidator;
    private final CommitValidator commitValidator;

    /**
     * Instantiates a new Subsequent message validator.
     *
     * @param validators the validators
     * @param targetRound the target round
     * @param proposalBlock the proposal block
     * @param blockInterface the block interface
     * @param bftExtraDataCodec the bft extra data codec
     */
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

    /**
     * Validate.
     *
     * @param msg the Prepare payload msg
     * @return the boolean
     */
    public boolean validate(final Prepare msg) {
      return prepareValidator.validate(msg);
    }

    /**
     * Validate.
     *
     * @param msg the Commit payload msg
     * @return the boolean
     */
    public boolean validate(final Commit msg) {
      return commitValidator.validate(msg);
    }
  }

  /** The interface Subsequent message validator factory. */
  @FunctionalInterface
  public interface SubsequentMessageValidatorFactory {
    /**
     * Create subsequent message validator.
     *
     * @param proposalBlock the proposal block
     * @return the subsequent message validator
     */
    SubsequentMessageValidator create(Block proposalBlock);
  }

  private final SubsequentMessageValidatorFactory subsequentMessageValidatorFactory;
  private final ProposalValidator proposalValidator;

  private Optional<SubsequentMessageValidator> subsequentMessageValidator = Optional.empty();

  /**
   * Instantiates a new Message validator.
   *
   * @param subsequentMessageValidatorFactory the subsequent message validator factory
   * @param proposalValidator the proposal validator
   */
  public MessageValidator(
      final SubsequentMessageValidatorFactory subsequentMessageValidatorFactory,
      final ProposalValidator proposalValidator) {
    this.subsequentMessageValidatorFactory = subsequentMessageValidatorFactory;
    this.proposalValidator = proposalValidator;
  }

  /**
   * Validate proposal payload.
   *
   * @param msg the Proposal payload msg
   * @return the boolean
   */
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

  /**
   * Validate Prepare payload.
   *
   * @param msg the Prepare msg
   * @return the boolean
   */
  public boolean validatePrepare(final Prepare msg) {
    return subsequentMessageValidator.map(pv -> pv.validate(msg)).orElse(false);
  }

  /**
   * Validate commit payload.
   *
   * @param msg the Commit payload
   * @return the boolean
   */
  public boolean validateCommit(final Commit msg) {
    return subsequentMessageValidator.map(cv -> cv.validate(msg)).orElse(false);
  }
}
