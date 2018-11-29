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
package tech.pegasys.pantheon.consensus.ibft.validation;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftPreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrePrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedRoundChangeMessageData;
import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoundChangeMessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final MessageValidatorFactory messageValidatorFactory;
  private final Collection<Address> validators;
  private final long minimumPrepareMessages;
  private final ConsensusRoundIdentifier currentRound;

  public RoundChangeMessageValidator(
      final MessageValidatorFactory messageValidatorFactory,
      final Collection<Address> validators,
      final long minimumPrepareMessages,
      final ConsensusRoundIdentifier currentRound) {
    this.messageValidatorFactory = messageValidatorFactory;
    this.validators = validators;
    this.minimumPrepareMessages = minimumPrepareMessages;
    this.currentRound = currentRound;
  }

  public boolean validateMessage(
      final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg) {

    if (!validators.contains(msg.getSender())) {
      LOG.info(
          "Invalid RoundChange message, was not transmitted by a validator for the associated"
              + " round.");
      return false;
    }

    final ConsensusRoundIdentifier roundChangeTarget =
        msg.getUnsignedMessageData().getRoundChangeIdentifier();

    if (roundChangeTarget.getSequenceNumber() != currentRound.getSequenceNumber()) {
      LOG.info("Invalid RoundChange message, not valid for local chain height.");
      return false;
    }

    if (msg.getUnsignedMessageData().getPreparedCertificate().isPresent()) {
      final IbftPreparedCertificate certificate =
          msg.getUnsignedMessageData().getPreparedCertificate().get();

      return validatePrepareCertificate(certificate, roundChangeTarget);
    }

    return true;
  }

  private boolean validatePrepareCertificate(
      final IbftPreparedCertificate certificate, final ConsensusRoundIdentifier roundChangeTarget) {
    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMessage =
        certificate.getIbftPrePrepareMessage();

    final ConsensusRoundIdentifier prepareCertRound =
        preprepareMessage.getUnsignedMessageData().getRoundIdentifier();

    if (!validatePreprepareCertificateRound(prepareCertRound, roundChangeTarget)) {
      return false;
    }

    final MessageValidator messageValidator = messageValidatorFactory.createAt(prepareCertRound);
    return validateConsistencyOfPrepareCertificateMessages(certificate, messageValidator);
  }

  private boolean validateConsistencyOfPrepareCertificateMessages(
      final IbftPreparedCertificate certificate, final MessageValidator messageValidator) {

    if (!messageValidator.addPreprepareMessage(certificate.getIbftPrePrepareMessage())) {
      LOG.info("Invalid RoundChange message, embedded Preprepare message failed validation.");
      return false;
    }

    if (certificate.getIbftPrepareMessages().size() < minimumPrepareMessages) {
      LOG.info(
          "Invalid RoundChange message, insufficient prepare messages exist to justify "
              + "prepare certificate.");
      return false;
    }

    for (final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg :
        certificate.getIbftPrepareMessages()) {
      if (!messageValidator.validatePrepareMessage(prepareMsg)) {
        LOG.info("Invalid RoundChange message, embedded Prepare message failed validation.");
        return false;
      }
    }

    return true;
  }

  private boolean validatePreprepareCertificateRound(
      final ConsensusRoundIdentifier prepareCertRound,
      final ConsensusRoundIdentifier roundChangeTarget) {

    if (prepareCertRound.getSequenceNumber() != roundChangeTarget.getSequenceNumber()) {
      LOG.info("Invalid RoundChange message, PreprepareCertificate is not for local chain height.");
      return false;
    }

    if (prepareCertRound.getRoundNumber() >= roundChangeTarget.getRoundNumber()) {
      LOG.info(
          "Invalid RoundChange message, PreprepareCertificate is newer than RoundChange target.");
      return false;
    }
    return true;
  }

  @FunctionalInterface
  public interface MessageValidatorFactory {

    MessageValidator createAt(final ConsensusRoundIdentifier roundIdentifier);
  }
}
