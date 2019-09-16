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
package org.hyperledger.besu.consensus.ibft.validation;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.ethereum.core.Block;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProposalBlockConsistencyValidator {

  private static final Logger LOG = LogManager.getLogger();

  public boolean validateProposalMatchesBlock(
      final SignedData<ProposalPayload> signedPayload, final Block proposedBlock) {

    if (!signedPayload.getPayload().getDigest().equals(proposedBlock.getHash())) {
      LOG.info("Invalid Proposal, embedded digest does not match block's hash.");
      return false;
    }

    if (proposedBlock.getHeader().getNumber()
        != signedPayload.getPayload().getRoundIdentifier().getSequenceNumber()) {
      LOG.info("Invalid proposal/block - message sequence does not align with block number.");
      return false;
    }

    if (!validateBlockMatchesProposalRound(signedPayload.getPayload(), proposedBlock)) {
      return false;
    }

    return true;
  }

  private boolean validateBlockMatchesProposalRound(
      final ProposalPayload payload, final Block block) {
    final ConsensusRoundIdentifier msgRound = payload.getRoundIdentifier();
    final IbftExtraData extraData = IbftExtraData.decode(block.getHeader());
    if (extraData.getRound() != msgRound.getRoundNumber()) {
      LOG.info("Invalid Proposal message, round number in block does not match that in message.");
      return false;
    }
    return true;
  }
}
