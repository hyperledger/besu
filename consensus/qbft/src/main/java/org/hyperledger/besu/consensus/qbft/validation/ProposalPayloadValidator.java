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

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftContext;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraData;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.pki.cms.CmsValidator;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProposalPayloadValidator {

  private static final String ERROR_PREFIX = "Invalid Proposal Payload";

  private static final Logger LOG = LogManager.getLogger();
  private final Address expectedProposer;
  private final ConsensusRoundIdentifier targetRound;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;

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

    // Validate the CMS in the extraData (only for PKI-mode)
    if (!validateCms(block)) {
      return false;
    }

    return true;
  }

  private boolean validateBlock(final Block block) {
    final Optional<BlockProcessingOutputs> validationResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.LIGHT, HeaderValidationMode.FULL);

    if (validationResult.isEmpty()) {
      LOG.info("{}: block did not pass validation.", ERROR_PREFIX);
      return false;
    }

    return true;
  }

  /*
    Validate CMS in block header (only for PKI-mode)
  */
  private boolean validateCms(final Block block) {
    final PkiQbftContext pkiQbftContext;
    try {
      pkiQbftContext = protocolContext.getConsensusState(PkiQbftContext.class);
    } catch (final ClassCastException e) {
      // TODO-lucas Do we have a better way of doing this?
      // Not running on PKI-enabled mode
      return true;
    }

    final BftExtraData extraData =
        pkiQbftContext.getBlockInterface().getExtraData(block.getHeader());
    final PkiQbftExtraData pkiExtraData = (PkiQbftExtraData) extraData;
    final KeyStoreWrapper trustStore =
        pkiQbftContext.getPkiBlockCreationConfiguration().getTrustStore();

    if (pkiExtraData.getCms().isPresent()) {
      // TODO-lucas Do we need to do this or could I just use 'block.getHeader().getHash()'
      final Hash proposedBlockHash =
          BftBlockHeaderFunctions.forOnChainBlock(new PkiQbftExtraDataCodec())
              .hash(block.getHeader());

      LOG.info(">>> Validating CMS for block {}", proposedBlockHash);

      CertStore crlCertStore = null;
      if (trustStore.getCRLs() != null) {
        try {
          crlCertStore =
              CertStore.getInstance(
                  "Collection", new CollectionCertStoreParameters(trustStore.getCRLs()));
        } catch (final Exception e) {
          throw new RuntimeException("Error reading PKI Block Creation TrustStore CRL file", e);
        }
      }

      final CmsValidator cmsValidator = new CmsValidator(trustStore, crlCertStore);

      if (!cmsValidator.validate(pkiExtraData.getCms().get(), proposedBlockHash)) {
        LOG.info(">>> CMS Validation INVALID for block {}", proposedBlockHash);
        return false;
      } else {
        LOG.info(">>> CMS Validation VALID for block {}", proposedBlockHash);
        return true;
      }
    } else {
      LOG.info(">>> Skipping CMS Validation for block {}", block.getHeader().getHash());
    }

    return true;
  }
}
