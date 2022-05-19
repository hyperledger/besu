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
package org.hyperledger.besu.consensus.ibft.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.network.IbftMessageTransmitter;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IbftRound {

  private static final Logger LOG = LoggerFactory.getLogger(IbftRound.class);

  private final Subscribers<MinedBlockObserver> observers;
  private final RoundState roundState;
  private final BlockCreator blockCreator;
  private final ProtocolContext protocolContext;
  private final BlockImporter blockImporter;
  private final NodeKey nodeKey;
  private final MessageFactory messageFactory; // used only to create stored local msgs
  private final IbftMessageTransmitter transmitter;
  private final BftExtraDataCodec bftExtraDataCodec;

  public IbftRound(
      final RoundState roundState,
      final BlockCreator blockCreator,
      final ProtocolContext protocolContext,
      final BlockImporter blockImporter,
      final Subscribers<MinedBlockObserver> observers,
      final NodeKey nodeKey,
      final MessageFactory messageFactory,
      final IbftMessageTransmitter transmitter,
      final RoundTimer roundTimer,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.roundState = roundState;
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.blockImporter = blockImporter;
    this.observers = observers;
    this.nodeKey = nodeKey;
    this.messageFactory = messageFactory;
    this.transmitter = transmitter;
    this.bftExtraDataCodec = bftExtraDataCodec;
    roundTimer.startTimer(getRoundIdentifier());
  }

  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundState.getRoundIdentifier();
  }

  public void createAndSendProposalMessage(final long headerTimeStampSeconds) {
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    final BftExtraData extraData = bftExtraDataCodec.decode(block.getHeader());
    LOG.debug("Creating proposed block. round={}", roundState.getRoundIdentifier());
    LOG.trace(
        "Creating proposed block with extraData={} blockHeader={}", extraData, block.getHeader());
    updateStateWithProposalAndTransmit(block, Optional.empty());
  }

  public void startRoundWith(
      final RoundChangeArtifacts roundChangeArtifacts, final long headerTimestamp) {
    final Optional<Block> bestBlockFromRoundChange = roundChangeArtifacts.getBlock();

    final RoundChangeCertificate roundChangeCertificate =
        roundChangeArtifacts.getRoundChangeCertificate();
    Block blockToPublish;
    if (!bestBlockFromRoundChange.isPresent()) {
      LOG.debug("Sending proposal with new block. round={}", roundState.getRoundIdentifier());
      blockToPublish = blockCreator.createBlock(headerTimestamp);
    } else {
      LOG.debug(
          "Sending proposal from PreparedCertificate. round={}", roundState.getRoundIdentifier());

      final BftBlockInterface bftBlockInterface =
          protocolContext.getConsensusContext(BftContext.class).getBlockInterface();
      blockToPublish =
          bftBlockInterface.replaceRoundInBlock(
              bestBlockFromRoundChange.get(),
              getRoundIdentifier().getRoundNumber(),
              BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    }

    updateStateWithProposalAndTransmit(blockToPublish, Optional.of(roundChangeCertificate));
  }

  private void updateStateWithProposalAndTransmit(
      final Block block, final Optional<RoundChangeCertificate> roundChangeCertificate) {
    final Proposal proposal;
    try {
      proposal = messageFactory.createProposal(getRoundIdentifier(), block, roundChangeCertificate);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to create a signed Proposal, waiting for next round.", e);
      return;
    }

    transmitter.multicastProposal(
        proposal.getRoundIdentifier(), proposal.getBlock(), proposal.getRoundChangeCertificate());
    updateStateWithProposedBlock(proposal);
  }

  public void handleProposalMessage(final Proposal msg) {
    LOG.debug("Received a proposal message. round={}", roundState.getRoundIdentifier());
    final Block block = msg.getBlock();

    if (updateStateWithProposedBlock(msg)) {
      LOG.debug("Sending prepare message. round={}", roundState.getRoundIdentifier());
      try {
        final Prepare localPrepareMessage =
            messageFactory.createPrepare(getRoundIdentifier(), block.getHash());
        peerIsPrepared(localPrepareMessage);
        transmitter.multicastPrepare(
            localPrepareMessage.getRoundIdentifier(), localPrepareMessage.getDigest());
      } catch (final SecurityModuleException e) {
        LOG.warn("Failed to create a signed Prepare; {}", e.getMessage());
      }
    }
  }

  public void handlePrepareMessage(final Prepare msg) {
    LOG.debug("Received a prepare message. round={}", roundState.getRoundIdentifier());
    peerIsPrepared(msg);
  }

  public void handleCommitMessage(final Commit msg) {
    LOG.debug("Received a commit message. round={}", roundState.getRoundIdentifier());
    peerIsCommitted(msg);
  }

  public Optional<PreparedRoundArtifacts> constructPreparedRoundArtifacts() {
    return roundState.constructPreparedRoundArtifacts();
  }

  private boolean updateStateWithProposedBlock(final Proposal msg) {
    final boolean wasPrepared = roundState.isPrepared();
    final boolean wasCommitted = roundState.isCommitted();
    final boolean blockAccepted = roundState.setProposedBlock(msg);

    if (blockAccepted) {
      final Block block = roundState.getProposedBlock().get();

      final SECPSignature commitSeal;
      try {
        commitSeal = createCommitSeal(block);
      } catch (final SecurityModuleException e) {
        LOG.warn("Failed to construct commit seal; {}", e.getMessage());
        return true;
      }

      // There are times handling a proposed block is enough to enter prepared.
      if (wasPrepared != roundState.isPrepared()) {
        LOG.debug("Sending commit message. round={}", roundState.getRoundIdentifier());
        transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), commitSeal);
      }

      // can automatically add _our_ commit message to the roundState
      // cannot create a prepare message here, as it may be _our_ proposal, and thus we cannot also
      // prepare
      try {
        final Commit localCommitMessage =
            messageFactory.createCommit(
                roundState.getRoundIdentifier(), msg.getBlock().getHash(), commitSeal);
        roundState.addCommitMessage(localCommitMessage);
      } catch (final SecurityModuleException e) {
        LOG.warn("Failed to create signed Commit message; {}", e.getMessage());
        return true;
      }

      // It is possible sufficient commit seals are now available and the block should be imported
      if (wasCommitted != roundState.isCommitted()) {
        importBlockToChain();
      }
    }

    return blockAccepted;
  }

  private void peerIsPrepared(final Prepare msg) {
    final boolean wasPrepared = roundState.isPrepared();
    roundState.addPrepareMessage(msg);
    if (wasPrepared != roundState.isPrepared()) {
      LOG.debug("Sending commit message. round={}", roundState.getRoundIdentifier());
      final Block block = roundState.getProposedBlock().get();
      try {
        transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), createCommitSeal(block));
        // Note: the local-node's commit message was added to RoundState on block acceptance
        // and thus does not need to be done again here.
      } catch (final SecurityModuleException e) {
        LOG.warn("Failed to construct a commit seal: {}", e.getMessage());
      }
    }
  }

  private void peerIsCommitted(final Commit msg) {
    final boolean wasCommitted = roundState.isCommitted();
    roundState.addCommitMessage(msg);
    if (wasCommitted != roundState.isCommitted()) {
      importBlockToChain();
    }
  }

  private void importBlockToChain() {
    final Block blockToImport =
        BftHelpers.createSealedBlock(
            bftExtraDataCodec,
            roundState.getProposedBlock().get(),
            roundState.getRoundIdentifier().getRoundNumber(),
            roundState.getCommitSeals());

    final long blockNumber = blockToImport.getHeader().getNumber();
    final BftExtraData extraData = bftExtraDataCodec.decode(blockToImport.getHeader());
    if (getRoundIdentifier().getRoundNumber() > 0) {
      LOG.info(
          "Importing block to chain. round={}, hash={}",
          getRoundIdentifier(),
          blockToImport.getHash());
    } else {
      LOG.debug(
          "Importing block to chain. round={}, hash={}",
          getRoundIdentifier(),
          blockToImport.getHash());
    }
    LOG.trace("Importing block with extraData={}", extraData);
    final boolean result =
        blockImporter.importBlock(protocolContext, blockToImport, HeaderValidationMode.FULL);
    if (!result) {
      LOG.error(
          "Failed to import block to chain. block={} extraData={} blockHeader={}",
          blockNumber,
          extraData,
          blockToImport.getHeader());
    } else {
      notifyNewBlockListeners(blockToImport);
    }
  }

  private SECPSignature createCommitSeal(final Block block) {
    final BlockHeader proposedHeader = block.getHeader();
    final BftExtraData extraData = bftExtraDataCodec.decode(proposedHeader);
    final Hash commitHash =
        new BftBlockHashing(bftExtraDataCodec)
            .calculateDataHashForCommittedSeal(proposedHeader, extraData);
    return nodeKey.sign(commitHash);
  }

  private void notifyNewBlockListeners(final Block block) {
    observers.forEach(obs -> obs.blockMined(block));
  }
}
