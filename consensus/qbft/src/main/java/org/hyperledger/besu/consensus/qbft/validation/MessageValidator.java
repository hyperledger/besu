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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final SignedDataValidator signedDataValidator;
  private final ProposalBlockConsistencyValidator proposalConsistencyValidator;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;

  public MessageValidator(
      final SignedDataValidator signedDataValidator,
      final ProposalBlockConsistencyValidator proposalConsistencyValidator,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext) {
    this.signedDataValidator = signedDataValidator;
    this.proposalConsistencyValidator = proposalConsistencyValidator;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
  }

  public boolean validateProposal(final Proposal msg) {

    if (!signedDataValidator.validateProposal(msg.getSignedPayload())) {
      LOG.info("Illegal Proposal message, embedded signed data failed validation");
      return false;
    }

    if (!validateBlock(msg.getSignedPayload().getPayload().getProposedBlock())) {
      return false;
    }

    if (!validateProposalAndRoundChangeAreConsistent(msg)) {
      LOG.info("Illegal Proposal message, embedded roundChange does not match proposal.");
      return false;
    }

    return proposalConsistencyValidator.validateProposalMatchesBlock(
        msg.getSignedPayload(), msg.getBlock());
  }

  private boolean validateBlock(final Block block) {
    final Optional<BlockProcessingOutputs> validationResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.LIGHT, HeaderValidationMode.FULL);

    if (!validationResult.isPresent()) {
      LOG.info("Invalid Proposal message, block did not pass validation.");
      return false;
    }

    return true;
  }

  private boolean validateProposalAndRoundChangeAreConsistent(final Proposal proposal) {
    final ConsensusRoundIdentifier proposalRoundIdentifier = proposal.getRoundIdentifier();

    if (proposalRoundIdentifier.getRoundNumber() == 0) {
      return validateRoundZeroProposalHasNoRoundChangeCertificate(proposal);
    } else {
      return validateRoundChangesAndPreparesAreValidAndMatchProposedBlock(proposal);
    }
  }

  private boolean validateRoundZeroProposalHasNoRoundChangeCertificate(final Proposal proposal) {
    if (proposal.getRoundChangeCertificate().isPresent()) {
      LOG.info(
          "Illegal Proposal message, round-0 proposal must not contain a round change certificate.");
      return false;
    }
    return true;
  }

  private boolean validateRoundChangesAndPreparesAreValidAndMatchProposedBlock(
      final Proposal proposal) {

    return signedDataValidator.validateProposal(proposal.getSignedPayload());

    // TODO(tmm): WORK OUT HOW TO BEST VALIDATE THE PROPOSAL!!!! (and piggy-backed data)

    /*
       final List<SignedData<RoundChangePayload>> roundchanges = proposal.getRoundChanges();
       proposalPiggyBackValidator.validateProposalMessageMatchesLatestPrepareCertificate()


       if (!proposalPiggyBackValidator.validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
           proposal.getRoundIdentifier(), roundChangeCert)) {
         LOG.info("Illegal Proposal message, embedded RoundChangeCertificate is not self-consistent");
         return false;
       }

       if (!proposalPiggyBackValidator.validateProposalMessageMatchesLatestPrepareCertificate(
           roundChangeCert, proposal.getBlock())) {
         LOG.info(
             "Illegal Proposal message, piggybacked block does not match latest PrepareCertificate");
         return false;
       }

    */
  }

  public boolean validatePrepare(final Prepare msg) {
    return signedDataValidator.validatePrepare(msg.getSignedPayload());
  }

  public boolean validateCommit(final Commit msg) {
    return signedDataValidator.validateCommit(msg.getSignedPayload());
  }
}
