/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.validation;

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.findLatestPreparedCertificate;

import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.BlockValidator.BlockProcessingOutputs;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;

import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewRoundMessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final NewRoundPayloadValidator payloadValidator;
  private final ProposalBlockConsistencyValidator proposalConsistencyValidator;
  private final BlockValidator<IbftContext> blockValidator;
  private final ProtocolContext<IbftContext> protocolContext;

  public NewRoundMessageValidator(
      final NewRoundPayloadValidator payloadValidator,
      final ProposalBlockConsistencyValidator proposalConsistencyValidator,
      final BlockValidator<IbftContext> blockValidator,
      final ProtocolContext<IbftContext> protocolContext) {
    this.payloadValidator = payloadValidator;
    this.proposalConsistencyValidator = proposalConsistencyValidator;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
  }

  public boolean validateNewRoundMessage(final NewRound msg) {
    if (!payloadValidator.validateNewRoundMessage(msg.getSignedPayload())) {
      LOG.debug("Illegal NewRound message, embedded signed data failed validation.");
      return false;
    }

    if (!validateProposalMessageMatchesLatestPrepareCertificate(
        msg.getSignedPayload().getPayload(), msg.getBlock())) {
      LOG.debug(
          "Illegal NewRound message, piggybacked block does not match latest PrepareCertificate");
      return false;
    }

    if (!validateBlock(msg.getBlock())) {
      return false;
    }

    return proposalConsistencyValidator.validateProposalMatchesBlock(
        msg.getProposalPayload(), msg.getBlock());
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

  private boolean validateProposalMessageMatchesLatestPrepareCertificate(
      final NewRoundPayload payload, final Block proposedBlock) {

    final RoundChangeCertificate roundChangeCert = payload.getRoundChangeCertificate();
    final Collection<SignedData<RoundChangePayload>> roundChangePayloads =
        roundChangeCert.getRoundChangePayloads();

    final Optional<PreparedCertificate> latestPreparedCertificate =
        findLatestPreparedCertificate(roundChangePayloads);

    if (!latestPreparedCertificate.isPresent()) {
      LOG.trace(
          "No round change messages have a preparedCertificate, any valid block may be proposed.");
      return true;
    }

    // Need to check that if we substitute the LatestPrepareCert round number into the supplied
    // block that we get the SAME hash as PreparedCert.
    final Block currentBlockWithOldRound =
        IbftBlockInterface.replaceRoundInBlock(
            proposedBlock,
            latestPreparedCertificate
                .get()
                .getProposalPayload()
                .getPayload()
                .getRoundIdentifier()
                .getRoundNumber(),
            IbftBlockHashing::calculateDataHashForCommittedSeal);

    if (!currentBlockWithOldRound
        .getHash()
        .equals(latestPreparedCertificate.get().getProposalPayload().getPayload().getDigest())) {
      LOG.info(
          "Invalid NewRound message, block in latest RoundChange does not match proposed block.");
      return false;
    }

    return true;
  }
}
