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

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.findLatestPreparedCertificate;
import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.prepareMessageCountForQuorum;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.RoundChangeMessageValidator.MessageValidatorForHeightFactory;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewRoundMessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Collection<Address> validators;
  private final ProposerSelector proposerSelector;
  private final MessageValidatorForHeightFactory messageValidatorFactory;
  private final long quorum;
  private final long chainHeight;

  public NewRoundMessageValidator(
      final Collection<Address> validators,
      final ProposerSelector proposerSelector,
      final MessageValidatorForHeightFactory messageValidatorFactory,
      final long quorum,
      final long chainHeight) {
    this.validators = validators;
    this.proposerSelector = proposerSelector;
    this.messageValidatorFactory = messageValidatorFactory;
    this.quorum = quorum;
    this.chainHeight = chainHeight;
  }

  public boolean validateNewRoundMessage(final SignedData<NewRoundPayload> msg) {

    final NewRoundPayload payload = msg.getPayload();
    final ConsensusRoundIdentifier rootRoundIdentifier = payload.getRoundIdentifier();
    final Address expectedProposer = proposerSelector.selectProposerForRound(rootRoundIdentifier);
    final RoundChangeCertificate roundChangeCert = payload.getRoundChangeCertificate();

    if (!expectedProposer.equals(msg.getAuthor())) {
      LOG.info("Invalid NewRound message, did not originate from expected proposer.");
      return false;
    }

    if (msg.getPayload().getRoundIdentifier().getSequenceNumber() != chainHeight) {
      LOG.info("Invalid NewRound message, not valid for local chain height.");
      return false;
    }

    if (msg.getPayload().getRoundIdentifier().getRoundNumber() == 0) {
      LOG.info("Invalid NewRound message, illegally targets a new round of 0.");
      return false;
    }

    final SignedData<ProposalPayload> proposalPayload = payload.getProposalPayload();
    final MessageValidator proposalValidator =
        messageValidatorFactory.createAt(rootRoundIdentifier);
    if (!proposalValidator.addSignedProposalPayload(proposalPayload)) {
      LOG.info("Invalid NewRound message, embedded proposal failed validation");
      return false;
    }

    if (!validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
        rootRoundIdentifier, roundChangeCert)) {
      return false;
    }

    return validateProposalMessageMatchesLatestPrepareCertificate(payload);
  }

  private boolean validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
      final ConsensusRoundIdentifier expectedRound, final RoundChangeCertificate roundChangeCert) {

    final Collection<SignedData<RoundChangePayload>> roundChangeMsgs =
        roundChangeCert.getRoundChangePayloads();

    if (roundChangeMsgs.size() < quorum) {
      LOG.info(
          "Invalid NewRound message, RoundChange certificate has insufficient "
              + "RoundChange messages.");
      return false;
    }

    if (!roundChangeCert
        .getRoundChangePayloads()
        .stream()
        .allMatch(p -> p.getPayload().getRoundIdentifier().equals(expectedRound))) {
      LOG.info(
          "Invalid NewRound message, not all embedded RoundChange messages have a "
              + "matching target round.");
      return false;
    }

    for (final SignedData<RoundChangePayload> roundChangeMsg :
        roundChangeCert.getRoundChangePayloads()) {
      final RoundChangeMessageValidator roundChangeValidator =
          new RoundChangeMessageValidator(
              messageValidatorFactory,
              validators,
              prepareMessageCountForQuorum(quorum),
              chainHeight);

      if (!roundChangeValidator.validateMessage(roundChangeMsg)) {
        LOG.info("Invalid NewRound message, embedded RoundChange message failed validation.");
        return false;
      }
    }
    return true;
  }

  private boolean validateProposalMessageMatchesLatestPrepareCertificate(
      final NewRoundPayload payload) {

    final RoundChangeCertificate roundChangeCert = payload.getRoundChangeCertificate();
    final Collection<SignedData<RoundChangePayload>> roundChangeMsgs =
        roundChangeCert.getRoundChangePayloads();

    final Optional<PreparedCertificate> latestPreparedCertificate =
        findLatestPreparedCertificate(roundChangeMsgs);

    if (!latestPreparedCertificate.isPresent()) {
      LOG.info(
          "No round change messages have a preparedCertificate, any valid block may be proposed.");
      return true;
    }

    // Get the hash of the block in latest prepareCert, not including the Round field.
    final Hash roundAgnosticBlockHashPreparedCert =
        IbftBlockHashing.calculateHashOfIbftBlockOnChain(
            latestPreparedCertificate
                .get()
                .getProposalPayload()
                .getPayload()
                .getBlock()
                .getHeader());

    final Hash roundAgnosticBlockHashProposal =
        IbftBlockHashing.calculateHashOfIbftBlockOnChain(
            payload.getProposalPayload().getPayload().getBlock().getHeader());

    if (!roundAgnosticBlockHashPreparedCert.equals(roundAgnosticBlockHashProposal)) {
      LOG.info(
          "Invalid NewRound message, block in latest RoundChange does not match proposed block.");
      return false;
    }

    return true;
  }
}
