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
package org.hyperledger.besu.consensus.qbft.validation;

import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.allAuthorsBelongToValidatorList;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.allMessagesTargetRound;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.hasDuplicateAuthors;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.hasSufficientEntries;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProposalValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final SignedDataValidator signedDataValidator;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;
  private final int quorumMessageCount;
  private final Collection<Address> validators;

  public ProposalValidator(
      final SignedDataValidator signedDataValidator,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext,
      final int quorumMessageCount,
      final Collection<Address> validators) {
    this.signedDataValidator = signedDataValidator;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
    this.quorumMessageCount = quorumMessageCount;
    this.validators = validators;
  }

  public boolean validateProposal(final Proposal msg) {
    // Ensure proposal is from proposer, and targets THIS round
    if (!signedDataValidator.validateProposal(msg.getSignedPayload())) {
      LOG.info("Illegal Proposal message, embedded signed data failed validation");
      return false;
    }

    // Ensure block is valid in accordance with "light" header validation rules & txn content
    if (!validateBlock(msg.getBlock())) {
      return false;
    }

    if (!validateProposalAndRoundChangeAreConsistent(msg)) {
      LOG.info("Illegal Proposal message, embedded roundChange does not match proposal.");
      return false;
    }

    return true;
  }

  private boolean validateBlock(final Block block) {
    final Optional<BlockProcessingOutputs> validationResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.LIGHT, HeaderValidationMode.FULL);

    if (!validationResult.isPresent()) {
      LOG.info("Invalid Proposal message: block did not pass validation.");
      return false;
    }

    return true;
  }

  private boolean validateProposalAndRoundChangeAreConsistent(final Proposal proposal) {
    final ConsensusRoundIdentifier proposalRoundIdentifier = proposal.getRoundIdentifier();

    if (proposalRoundIdentifier.getRoundNumber() == 0) {
      return validateRoundZeroProposalHasNoRoundChangesOrPrepares(proposal);
    } else {
      return validateRoundChangesAndPreparesAreValidAndMatchProposedBlock(proposal);
    }
  }

  private boolean validateRoundZeroProposalHasNoRoundChangesOrPrepares(final Proposal proposal) {

    if ((proposal.getRoundChanges().size() != 0) || proposal.getPrepares().size() != 0) {
      LOG.info(
          "Invalid Proposal message: round-0 proposal must not contain any prepares or roundchanges");
      return false;
    }
    return true;
  }

  private boolean validateRoundChangesAndPreparesAreValidAndMatchProposedBlock(
      final Proposal proposal) {
    final List<SignedData<RoundChangePayload>> roundChanges = proposal.getRoundChanges();

    if (hasDuplicateAuthors(roundChanges)) {
      LOG.info("Invalid Proposal message: multiple RoundChanges from the same author.");
      return false;
    }

    if (!allAuthorsBelongToValidatorList(roundChanges, validators)) {
      LOG.info(
          "Invalid Proposal message: not all roundChange payloads came from recognized validators");
      return false;
    }

    if (!hasSufficientEntries(roundChanges, quorumMessageCount)) {
      LOG.info(
          "Invalid Proposal message: Insufficient roundchange payloads for a none-round-0 proposal");
      return false;
    }

    if (!allMessagesTargetRound(roundChanges, proposal.getRoundIdentifier())) {
      LOG.info("Invalid Proposal message: not all roundChange payloads target the proposal round.");
      return false;
    }

    // If any roundchange's prepared round is greater than or equal to current proposal - reject.
    if (!roundChanges.stream()
        .allMatch(
            rc ->
                rc.getPayload()
                    .getPreparedRoundMetadata()
                    .map(
                        metadata ->
                            metadata.getPreparedRound()
                                >= proposal.getRoundIdentifier().getRoundNumber())
                    .orElse(true))) {
      LOG.info("Invalid Proposal message: round change has prepared round >= proposal");
      return false;
    }

    final Optional<SignedData<RoundChangePayload>> roundChangeWithLatestPreparedRound =
        getRoundChangeWithLatestPreparedRound(roundChanges);

    if (roundChangeWithLatestPreparedRound.isEmpty()) {
      // no msg had a prepared round, so this is now valid, and prepares should be empty
      return proposal.getPrepares().isEmpty();
    } else {
      final PreparedRoundMetadata metaData =
          roundChangeWithLatestPreparedRound.get().getPayload().getPreparedRoundMetadata().get();

      if (!metaData.getPreparedBlockHash().equals(proposal.getBlock().getHash())) {
        LOG.info(
            "Invalid Proposal message: prepared round block hash doesn't match proposed block");
        return false;
      }

      return validatePrepares(
          roundChangeWithLatestPreparedRound.get().getPayload().getPreparedRoundMetadata().get(),
          proposal.getRoundIdentifier().getSequenceNumber(),
          proposal.getPrepares());
    }
  }

  private boolean validatePrepares(
      final PreparedRoundMetadata metaData,
      final long currentHeight,
      final List<SignedData<PreparePayload>> prepares) {

    if (hasDuplicateAuthors(prepares)) {
      LOG.info("Invalid Proposal message: multiple prepares from the same author.");
      return false;
    }

    if (!allAuthorsBelongToValidatorList(prepares, validators)) {
      LOG.info("Invalid Proposal message: Not all prepares are from current validators");
      return false;
    }

    if (!hasSufficientEntries(prepares, quorumMessageCount)) {
      LOG.info(
          "Invalid Proposal message: Insufficient prepare payloads for a none-round-0 proposal");
      return false;
    }

    final ConsensusRoundIdentifier preparedRoundIdentifier =
        new ConsensusRoundIdentifier(currentHeight, metaData.getPreparedRound());

    if (!allMessagesTargetRound(prepares, preparedRoundIdentifier)) {
      LOG.info("Invalid Proposal message: Not all prepares target a the specified prepared-round.");
      return false;
    }

    if (!prepares.stream()
        .allMatch(p -> p.getPayload().getDigest().equals(metaData.getPreparedBlockHash()))) {
      LOG.info(
          "Invalid Proposal message: Not all prepares are for latest round change's prepared block hash");
      return false;
    }

    return true;
  }

  private Optional<SignedData<RoundChangePayload>> getRoundChangeWithLatestPreparedRound(
      final List<SignedData<RoundChangePayload>> roundChanges) {

    final Comparator<SignedData<RoundChangePayload>> preparedRoundComparator =
        (o1, o2) -> {
          if (o1.getPayload().getPreparedRoundMetadata().isEmpty()) {
            return -1;
          }
          if (o2.getPayload().getPreparedRoundMetadata().isEmpty()) {
            return 1;
          }

          int o1Round = o1.getPayload().getPreparedRoundMetadata().get().getPreparedRound();
          int o2Round = o2.getPayload().getPreparedRoundMetadata().get().getPreparedRound();

          return Integer.compare(o1Round, o2Round);
        };

    return roundChanges.stream().max(preparedRoundComparator);
  }
}
