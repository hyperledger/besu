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

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.ibft.validation.RoundChangePayloadValidator.MessageValidatorForHeightFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoundChangeCertificateValidator {

  private static final Logger LOG = LoggerFactory.getLogger(RoundChangeCertificateValidator.class);

  private final Collection<Address> validators;
  private final MessageValidatorForHeightFactory messageValidatorFactory;
  private final BftExtraDataCodec bftExtraDataCodec;
  private final BftBlockInterface bftBlockInterface;
  private final long quorum;
  private final long chainHeight;

  public RoundChangeCertificateValidator(
      final Collection<Address> validators,
      final MessageValidatorForHeightFactory messageValidatorFactory,
      final long chainHeight,
      final BftExtraDataCodec bftExtraDataCodec,
      final BftBlockInterface bftBlockInterface) {
    this.validators = validators;
    this.messageValidatorFactory = messageValidatorFactory;
    this.quorum = BftHelpers.calculateRequiredValidatorQuorum(validators.size());
    this.chainHeight = chainHeight;
    this.bftExtraDataCodec = bftExtraDataCodec;
    this.bftBlockInterface = bftBlockInterface;
  }

  public boolean validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
      final ConsensusRoundIdentifier expectedRound, final RoundChangeCertificate roundChangeCert) {

    final Collection<SignedData<RoundChangePayload>> roundChangeMsgs =
        roundChangeCert.getRoundChangePayloads();

    if (roundChangeMsgs.size() < quorum) {
      LOG.info("Invalid RoundChangeCertificate, insufficient RoundChange messages.");
      return false;
    }

    if (hasDuplicateAuthors(roundChangeMsgs)) {
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
            messageValidatorFactory,
            validators,
            BftHelpers.prepareMessageCountForQuorum(quorum),
            chainHeight);

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
        bftBlockInterface.replaceRoundInBlock(
            proposedBlock,
            latestPreparedCertificate
                .get()
                .getProposalPayload()
                .getPayload()
                .getRoundIdentifier()
                .getRoundNumber(),
            BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));

    if (!currentBlockWithOldRound
        .getHash()
        .equals(latestPreparedCertificate.get().getProposalPayload().getPayload().getDigest())) {
      LOG.info(
          "Invalid RoundChangeCertificate, block in latest RoundChange does not match proposed block.");
      return false;
    }

    return true;
  }

  public static Optional<PreparedCertificate> findLatestPreparedCertificate(
      final Collection<SignedData<RoundChangePayload>> msgs) {

    Optional<PreparedCertificate> result = Optional.empty();

    for (SignedData<RoundChangePayload> roundChangeMsg : msgs) {
      final RoundChangePayload payload = roundChangeMsg.getPayload();
      if (payload.getPreparedCertificate().isPresent()) {
        if (!result.isPresent()) {
          result = payload.getPreparedCertificate();
        } else {
          final PreparedCertificate currentLatest = result.get();
          final PreparedCertificate nextCert = payload.getPreparedCertificate().get();

          if (currentLatest.getProposalPayload().getPayload().getRoundIdentifier().getRoundNumber()
              < nextCert.getProposalPayload().getPayload().getRoundIdentifier().getRoundNumber()) {
            result = Optional.of(nextCert);
          }
        }
      }
    }
    return result;
  }
}
