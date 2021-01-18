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

import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  @FunctionalInterface
  public interface PrepareValidatorFactory {
    PrepareValidator create(final Hash expectedDigest);
  }

  @FunctionalInterface
  public interface CommitValidatorFactory {
    CommitValidator create(final Hash expectedDigest);
  }

  private final PrepareValidatorFactory prepareValidatorFactory;
  private final CommitValidatorFactory commitValidatorFactory;
  private final ProposalValidator proposalValidator;

  private Optional<PrepareValidator> prepareValidator = Optional.empty();
  private Optional<CommitValidator> commitValidator = Optional.empty();

  public MessageValidator(
      final PrepareValidatorFactory prepareValidatorFactory,
      final CommitValidatorFactory commitValidatorFactory,
      final ProposalValidator proposalValidator) {
    this.prepareValidatorFactory = prepareValidatorFactory;
    this.commitValidatorFactory = commitValidatorFactory;
    this.proposalValidator = proposalValidator;
  }

  public boolean validateProposal(final Proposal msg) {
    if (prepareValidator.isPresent()) {
      LOG.info("Received subsequent Proposal for current round, discarding.");
      return false;
    }

    final boolean result = proposalValidator.validate(msg);
    if (result) {
      prepareValidator = Optional.of(prepareValidatorFactory.create(msg.getBlock().getHash()));
      commitValidator = Optional.of(commitValidatorFactory.create(msg.getBlock().getHash()));
    }

    return result;
  }

  public boolean validatePrepare(final Prepare msg) {
    return prepareValidator.map(pv -> pv.validate(msg)).orElse(false);
  }

  public boolean validateCommit(final Commit msg) {
    return commitValidator.map(cv -> cv.validate(msg)).orElse(false);
  }
}
