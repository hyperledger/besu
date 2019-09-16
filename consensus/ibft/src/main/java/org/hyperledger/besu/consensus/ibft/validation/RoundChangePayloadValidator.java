/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.validation;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.payload.PreparePayload;
import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.consensus.ibft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoundChangePayloadValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final MessageValidatorForHeightFactory messageValidatorFactory;
  private final Collection<Address> validators;
  private final long minimumPrepareMessages;
  private final long chainHeight;

  public RoundChangePayloadValidator(
      final MessageValidatorForHeightFactory messageValidatorFactory,
      final Collection<Address> validators,
      final long minimumPrepareMessages,
      final long chainHeight) {
    this.messageValidatorFactory = messageValidatorFactory;
    this.validators = validators;
    this.minimumPrepareMessages = minimumPrepareMessages;
    this.chainHeight = chainHeight;
  }

  public boolean validateRoundChange(final SignedData<RoundChangePayload> msg) {

    if (!validators.contains(msg.getAuthor())) {
      LOG.info(
          "Invalid RoundChange message, was not transmitted by a validator for the associated"
              + " round.");
      return false;
    }

    final ConsensusRoundIdentifier targetRound = msg.getPayload().getRoundIdentifier();

    if (targetRound.getSequenceNumber() != chainHeight) {
      LOG.info("Invalid RoundChange message, not valid for local chain height.");
      return false;
    }

    if (msg.getPayload().getPreparedCertificate().isPresent()) {
      final PreparedCertificate certificate = msg.getPayload().getPreparedCertificate().get();

      return validatePrepareCertificate(certificate, targetRound);
    }

    return true;
  }

  private boolean validatePrepareCertificate(
      final PreparedCertificate certificate, final ConsensusRoundIdentifier roundChangeTarget) {
    final SignedData<ProposalPayload> proposalMessage = certificate.getProposalPayload();

    final ConsensusRoundIdentifier proposalRoundIdentifier =
        proposalMessage.getPayload().getRoundIdentifier();

    if (!validatePreparedCertificateRound(proposalRoundIdentifier, roundChangeTarget)) {
      return false;
    }

    final SignedDataValidator signedDataValidator =
        messageValidatorFactory.createAt(proposalRoundIdentifier);
    return validateConsistencyOfPrepareCertificateMessages(certificate, signedDataValidator);
  }

  private boolean validateConsistencyOfPrepareCertificateMessages(
      final PreparedCertificate certificate, final SignedDataValidator signedDataValidator) {

    if (!signedDataValidator.validateProposal(certificate.getProposalPayload())) {
      LOG.info("Invalid RoundChange message, embedded Proposal message failed validation.");
      return false;
    }

    if (certificate.getPreparePayloads().size() < minimumPrepareMessages) {
      LOG.info(
          "Invalid RoundChange message, insufficient Prepare messages exist to justify "
              + "prepare certificate.");
      return false;
    }

    for (final SignedData<PreparePayload> prepareMsg : certificate.getPreparePayloads()) {
      if (!signedDataValidator.validatePrepare(prepareMsg)) {
        LOG.info("Invalid RoundChange message, embedded Prepare message failed validation.");
        return false;
      }
    }

    return true;
  }

  private boolean validatePreparedCertificateRound(
      final ConsensusRoundIdentifier prepareCertRound,
      final ConsensusRoundIdentifier roundChangeTarget) {

    if (prepareCertRound.getSequenceNumber() != roundChangeTarget.getSequenceNumber()) {
      LOG.info("Invalid RoundChange message, PreparedCertificate is not for local chain height.");
      return false;
    }

    if (prepareCertRound.getRoundNumber() >= roundChangeTarget.getRoundNumber()) {
      LOG.info(
          "Invalid RoundChange message, PreparedCertificate not older than RoundChange target.");
      return false;
    }
    return true;
  }

  @FunctionalInterface
  public interface MessageValidatorForHeightFactory {
    SignedDataValidator createAt(final ConsensusRoundIdentifier roundIdentifier);
  }
}
