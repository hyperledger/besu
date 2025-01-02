/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.payload.ProposalPayload;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Proposal payload validator. */
public class ProposalPayloadValidator {

  private static final String ERROR_PREFIX = "Invalid Proposal Payload";

  private static final Logger LOG = LoggerFactory.getLogger(ProposalPayloadValidator.class);
  private final Address expectedProposer;
  private final ConsensusRoundIdentifier targetRound;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;

  /**
   * Instantiates a new Proposal payload validator.
   *
   * @param expectedProposer the expected proposer
   * @param targetRound the target round
   * @param blockValidator the block validator
   * @param protocolContext the protocol context
   */
  @VisibleForTesting
  public ProposalPayloadValidator(
      final Address expectedProposer,
      final ConsensusRoundIdentifier targetRound,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext) {
    this.expectedProposer = expectedProposer;
    this.targetRound = targetRound;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
  }

  /**
   * Validate.
   *
   * @param signedPayload the signed Proposal payload
   * @return the boolean
   */
  public boolean validate(final SignedData<ProposalPayload> signedPayload) {

    if (!signedPayload.getAuthor().equals(expectedProposer)) {
      LOG.info("{}: proposal created by non-proposer", ERROR_PREFIX);
      return false;
    }

    final ProposalPayload payload = signedPayload.getPayload();

    if (!payload.getRoundIdentifier().equals(targetRound)) {
      LOG.info("{}: proposal is not for expected round", ERROR_PREFIX);
      return false;
    }

    final Block block = payload.getProposedBlock();
    if (!validateBlock(block)) {
      return false;
    }

    if (block.getHeader().getNumber() != payload.getRoundIdentifier().getSequenceNumber()) {
      LOG.info("{}: block number does not match sequence number", ERROR_PREFIX);
      return false;
    }

    return true;
  }

  private boolean validateBlock(final Block block) {
    checkState(blockValidator != null, "block validation not possible, no block validator.");

    final var validationResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.LIGHT, HeaderValidationMode.FULL, false);

    if (!validationResult.isSuccessful()) {
      LOG.info(
          "{}: block did not pass validation. Reason {}",
          ERROR_PREFIX,
          validationResult.errorMessage);
      return false;
    }

    return true;
  }
}
