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
import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.prepareMessageCountForQuorum;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.RoundChangePayloadValidator.MessageValidatorForHeightFactory;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoundChangeCertificateValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Collection<Address> validators;
  private final MessageValidatorForHeightFactory messageValidatorFactory;
  private final long quorum;
  private final long chainHeight;

  public RoundChangeCertificateValidator(
      final Collection<Address> validators,
      final MessageValidatorForHeightFactory messageValidatorFactory,
      final long chainHeight) {
    this.validators = validators;
    this.messageValidatorFactory = messageValidatorFactory;
    this.quorum = IbftHelpers.calculateRequiredValidatorQuorum(validators.size());
    this.chainHeight = chainHeight;
  }

  public boolean validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
      final ConsensusRoundIdentifier expectedRound, final RoundChangeCertificate roundChangeCert) {

    final Collection<SignedData<RoundChangePayload>> roundChangeMsgs =
        roundChangeCert.getRoundChangePayloads();

    if (hasDuplicateAuthors(roundChangeMsgs)) {
      return false;
    }

    if (roundChangeMsgs.size() < quorum) {
      LOG.info("Invalid RoundChangeCertificate, insufficient RoundChange messages.");
      return false;
    }

    if (!roundChangeCert.getRoundChangePayloads().stream()
        .allMatch(p -> p.getPayload().getRoundIdentifier().equals(expectedRound))) {
      LOG.info(
          "Invalid RoundChangeCertificate, not all embedded RoundChange messages have a "
              + "matching target round.");
      return false;
    }

    final RoundChangePayloadValidator roundChangeValidator =
        new RoundChangePayloadValidator(
            messageValidatorFactory, validators, prepareMessageCountForQuorum(quorum), chainHeight);

    if (!roundChangeCert.getRoundChangePayloads().stream()
        .allMatch(roundChangeValidator::validateRoundChange)) {
      LOG.info("Invalid RoundChangeCertificate, embedded RoundChange message failed validation.");
      return false;
    }

    return true;
  }

  private boolean hasDuplicateAuthors(
      final Collection<SignedData<RoundChangePayload>> roundChangeMsgs) {
    final long distinctAuthorCount =
        roundChangeMsgs.stream().map(SignedData::getAuthor).distinct().count();

    if (distinctAuthorCount != roundChangeMsgs.size()) {
      LOG.info("Invalid RoundChangeCertificate, multiple RoundChanges from the same author.");
      return true;
    }
    return false;
  }

  public boolean validateProposalMessageMatchesLatestPrepareCertificate(
      final RoundChangeCertificate roundChangeCert, final Block proposedBlock) {

    final Collection<SignedData<RoundChangePayload>> roundChangePayloads =
        roundChangeCert.getRoundChangePayloads();

    final Optional<PreparedCertificate> latestPreparedCertificate =
        findLatestPreparedCertificate(roundChangePayloads);

    if (!latestPreparedCertificate.isPresent()) {
      LOG.debug(
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
          "Invalid RoundChangeCertificate, block in latest RoundChange does not match proposed block.");
      return false;
    }

    return true;
  }
}
