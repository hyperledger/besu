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

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraData;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.pki.cms.CmsValidator;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProposalPayloadValidator {

  private static final String ERROR_PREFIX = "Invalid Proposal Payload";

  private static final Logger LOG = LoggerFactory.getLogger(ProposalPayloadValidator.class);
  private final Address expectedProposer;
  private final ConsensusRoundIdentifier targetRound;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;
  private final BftExtraDataCodec bftExtraDataCodec;
  private final Optional<CmsValidator> cmsValidator;

  public ProposalPayloadValidator(
      final Address expectedProposer,
      final ConsensusRoundIdentifier targetRound,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec) {
    this(
        expectedProposer,
        targetRound,
        blockValidator,
        protocolContext,
        bftExtraDataCodec,
        protocolContext
            .getConsensusContext(QbftContext.class)
            .getPkiBlockCreationConfiguration()
            .map(config -> new CmsValidator(config.getTrustStore())));
  }

  @VisibleForTesting
  public ProposalPayloadValidator(
      final Address expectedProposer,
      final ConsensusRoundIdentifier targetRound,
      final BlockValidator blockValidator,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec,
      final Optional<CmsValidator> cmsValidator) {
    this.expectedProposer = expectedProposer;
    this.targetRound = targetRound;
    this.blockValidator = blockValidator;
    this.protocolContext = protocolContext;
    this.bftExtraDataCodec = bftExtraDataCodec;
    this.cmsValidator = cmsValidator;
  }

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

    if (cmsValidator.isPresent()) {
      return validateCms(
          block,
          protocolContext.getConsensusContext(QbftContext.class).getBlockInterface(),
          cmsValidator.get());
    }

    return true;
  }

  private boolean validateBlock(final Block block) {
    final var validationResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.LIGHT, HeaderValidationMode.FULL);

    if (validationResult.blockProcessingOutputs.isEmpty()) {
      LOG.info(
          "{}: block did not pass validation. Reason {}",
          ERROR_PREFIX,
          validationResult.errorMessage);
      return false;
    }

    return true;
  }

  private boolean validateCms(
      final Block block,
      final BftBlockInterface bftBlockInterface,
      final CmsValidator cmsValidator) {
    final PkiQbftExtraData pkiExtraData =
        (PkiQbftExtraData) bftBlockInterface.getExtraData(block.getHeader());

    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature((PkiQbftExtraDataCodec) bftExtraDataCodec)
            .hash(block.getHeader());

    LOG.debug("Validating CMS with signed hash {} in block {}", hashWithoutCms, block.getHash());

    if (!cmsValidator.validate(pkiExtraData.getCms(), hashWithoutCms)) {
      LOG.info("{}: invalid CMS in block {}", ERROR_PREFIX, block.getHash());
      return false;
    } else {
      LOG.trace("Valid CMS in block {}", block.getHash());
      return true;
    }
  }
}
