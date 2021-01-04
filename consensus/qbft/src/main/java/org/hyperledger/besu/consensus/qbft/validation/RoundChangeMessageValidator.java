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

import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.allAuthorsBelongToValidatorList;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.allMessagesTargetRound;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.hasDuplicateAuthors;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationHelpers.hasSufficientEntries;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RoundChangeMessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final RoundChangePayloadValidator roundChangePayloadValidator;
  private final long quorumMessageCount;
  private final long chainHeight;
  private final Collection<Address> validators;

  public RoundChangeMessageValidator(
      final RoundChangePayloadValidator roundChangePayloadValidator,
      final long quorumMessageCount,
      final long chainHeight,
      final Collection<Address> validators) {
    this.roundChangePayloadValidator = roundChangePayloadValidator;
    this.quorumMessageCount = quorumMessageCount;
    this.chainHeight = chainHeight;
    this.validators = validators;
  }

  public boolean validateRoundChange(final RoundChange msg) {

    // Ensure msg is from a validator and is for the current height
    if (!roundChangePayloadValidator.validateRoundChange(msg.getSignedPayload())) {
      LOG.info("Invalid RoundChange message: signed data did not validate correctly.");
      return false;
    }

    if (msg.getProposedBlock().isPresent()) {
      return validateWithBlock(msg);
    }

    return msg.getPreparedRoundMetadata().isEmpty();
  }

  private boolean validateWithBlock(final RoundChange msg) {
    final Block block = msg.getProposedBlock().get();

    if (msg.getPreparedRoundMetadata().isEmpty()) {
      LOG.info(
          "Invalid RoundChange message: Prepared block specified, but prepared metaadata absent");
    }

    final PreparedRoundMetadata metadata = msg.getPreparedRoundMetadata().get();

    if (!metadata.getPreparedBlockHash().equals(block.getHash())) {
      LOG.info("Invalid RoundChange message: Prepared metadata does not align with supplied block");
      return false;
    }

    // NO NEED TO VERIFY PreparedRound is related to currentRound - its irrelevant.

    final BftExtraData extraData = BftExtraData.decode(block.getHeader());
    if (metadata.getPreparedRound() != extraData.getRound()) {
      LOG.info(
          "Invalid RoundChange message, Prepared metadata contains a different round to that in the supplied block");
      return false;
    }

    return validatePrepares(metadata, chainHeight, msg.getPrepares());
  }

  // THIS IS DUPLICATED IN ProposalValidator - love to make it common?!!!!
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
}
