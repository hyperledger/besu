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

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoundChangeMessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final RoundChangePayloadValidator roundChangePayloadValidator;
  private final MessageValidatorForHeightFactory messageValidatorFactory;

  private final long minimumPrepareMessages;

  public RoundChangeMessageValidator(
      final MessageValidatorForHeightFactory messageValidatorFactory,
      final RoundChangePayloadValidator roundChangePayloadValidator,
      final long minimumPrepareMessages) {
    this.messageValidatorFactory = messageValidatorFactory;
    this.roundChangePayloadValidator = roundChangePayloadValidator;
    this.minimumPrepareMessages = minimumPrepareMessages;
  }

  public boolean validateRoundChange(final RoundChange msg) {

    if (!roundChangePayloadValidator.validateRoundChange(msg.getSignedPayload())) {
      LOG.info("Invalid RoundChange message, signed data did not validate correctly.");
      return false;
    }

    if (msg.getProposedBlock().isPresent()) {
      validateWithBlock(msg);
    }

    return true;
  }

  private boolean validateWithBlock(final RoundChange msg) {
    final Block block = msg.getProposedBlock().get();

    if (!isPreparedRoundMetadataValid(
        msg.getPreparedRoundMetadata(), block, msg.getRoundIdentifier().getRoundNumber())) {
      return false;
    }

    final SignedDataValidator signedDataValidator =
        messageValidatorFactory.createAt(
            new ConsensusRoundIdentifier(
                msg.getSignedPayload().getPayload().getRoundIdentifier().getSequenceNumber(),
                msg.getPreparedRoundMetadata().get().getPreparedRound()));

    // TODO(tmm): need to insert the block to the validator prior to being able to verify subsequent
    // msgs

    if (validatePrepares(
        signedDataValidator, msg.getPrepares(), msg.getPreparedRoundMetadata().get())) {
      return false;
    }

    return true;
  }

  private boolean isPreparedRoundMetadataValid(
      final Optional<PreparedRoundMetadata> preparedRoundMetadata,
      final Block block,
      final int msgRound) {
    if (preparedRoundMetadata.isEmpty()) {
      LOG.info(
          "Invalid RoundChange message, block specified, but no corresponding prepared metadata");
      return false;
    }

    final PreparedRoundMetadata metadata = preparedRoundMetadata.get();

    if (!metadata.getPreparedBlockHash().equals(block.getHash())) {
      LOG.info("Invalid RoundChange message, Prepared metadata does not align with supplied block");
      return false;
    }

    if (metadata.getPreparedRound() >= msgRound) {
      LOG.info(
          "Invalid RoundChange message, preparedRound ({}) is not less than msgRound ({})",
          metadata.getPreparedRound(),
          msgRound);
      return false;
    }

    final BftExtraData extraData = BftExtraData.decode(block.getHeader());
    if (metadata.getPreparedRound() != extraData.getRound()) {
      LOG.info(
          "Invalid RoundChange message, Prepared metadata contains a different round to that in the supplied block");
      return false;
    }

    return true;
  }

  private boolean validatePrepares(
      final SignedDataValidator signedDataValidator,
      final List<SignedData<PreparePayload>> prepares,
      final PreparedRoundMetadata preparedRoundMetadata) {

    if (hasDuplicateAuthors(prepares)) {
      LOG.info("Invalid Roundchange message, Prepares list had multiple messages from same author");
      return false;
    }

    if (prepares.size() < minimumPrepareMessages) {
      LOG.info("Invalid RoundChange message, insufficient Prepare payloads to justify block.");
      return false;
    }

    for (final SignedData<PreparePayload> prepareMsg : prepares) {
      if (!signedDataValidator.validatePrepare(prepareMsg)) {
        LOG.info("Invalid RoundChange message, embedded Prepare message failed validation.");
        return false;
      }

      if (prepareMsg.getPayload().getRoundIdentifier().getRoundNumber()
          != preparedRoundMetadata.getPreparedRound()) {
        LOG.info(
            "Invalid RoundChange message, embedded prepares did not have same round as prepared round");
        return false;
      }
    }

    return true;
  }

  private boolean hasDuplicateAuthors(final Collection<SignedData<PreparePayload>> payloads) {
    final long distinctAuthorCount =
        payloads.stream().map(SignedData::getAuthor).distinct().count();
    return distinctAuthorCount != payloads.size();
  }

  @FunctionalInterface
  public interface MessageValidatorForHeightFactory {

    SignedDataValidator createAt(final ConsensusRoundIdentifier roundIdentifier);
  }
}
