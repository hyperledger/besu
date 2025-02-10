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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import static java.util.Collections.emptyList;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Qbft round. */
public class QbftRound {

  private static final Logger LOG = LoggerFactory.getLogger(QbftRound.class);

  private final Subscribers<MinedBlockObserver> observers;

  /** The Round state. */
  protected final RoundState roundState;

  /** The Block creator. */
  protected final BlockCreator blockCreator;

  /** The Protocol context. */
  protected final ProtocolContext protocolContext;

  /** The Protocol schedule. */
  protected final ProtocolSchedule protocolSchedule;

  private final NodeKey nodeKey;
  private final MessageFactory messageFactory; // used only to create stored local msgs
  private final QbftMessageTransmitter transmitter;

  /** The Bft extra data codec. */
  protected final BftExtraDataCodec bftExtraDataCodec;

  private final BlockHeader parentHeader;

  /**
   * Instantiates a new Qbft round.
   *
   * @param roundState the round state
   * @param blockCreator the block creator
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param observers the observers
   * @param nodeKey the node key
   * @param messageFactory the message factory
   * @param transmitter the transmitter
   * @param roundTimer the round timer
   * @param bftExtraDataCodec the bft extra data codec
   * @param parentHeader the parent header
   */
  public QbftRound(
      final RoundState roundState,
      final BlockCreator blockCreator,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final Subscribers<MinedBlockObserver> observers,
      final NodeKey nodeKey,
      final MessageFactory messageFactory,
      final QbftMessageTransmitter transmitter,
      final RoundTimer roundTimer,
      final BftExtraDataCodec bftExtraDataCodec,
      final BlockHeader parentHeader) {
    this.roundState = roundState;
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.observers = observers;
    this.nodeKey = nodeKey;
    this.messageFactory = messageFactory;
    this.transmitter = transmitter;
    this.bftExtraDataCodec = bftExtraDataCodec;
    this.parentHeader = parentHeader;
    roundTimer.startTimer(getRoundIdentifier());
  }

  /**
   * Gets round identifier.
   *
   * @return the round identifier
   */
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundState.getRoundIdentifier();
  }

  /**
   * Create a block
   *
   * @param headerTimeStampSeconds of the block
   * @return a Block
   */
  public Block createBlock(final long headerTimeStampSeconds) {
    LOG.debug("Creating proposed block. round={}", roundState.getRoundIdentifier());
    return blockCreator.createBlock(headerTimeStampSeconds, this.parentHeader).getBlock();
  }

  /**
   * Start round with.
   *
   * @param roundChangeArtifacts the round change artifacts
   * @param headerTimestamp the header timestamp
   */
  public void startRoundWith(
      final RoundChangeArtifacts roundChangeArtifacts, final long headerTimestamp) {
    final Optional<PreparedCertificate> bestPreparedCertificate =
        roundChangeArtifacts.getBestPreparedPeer();

    final Block blockToPublish;
    if (bestPreparedCertificate.isEmpty()) {
      LOG.debug("Sending proposal with new block. round={}", roundState.getRoundIdentifier());
      blockToPublish = blockCreator.createBlock(headerTimestamp, this.parentHeader).getBlock();
    } else {
      LOG.debug(
          "Sending proposal from PreparedCertificate. round={}", roundState.getRoundIdentifier());
      Block preparedBlock = bestPreparedCertificate.get().getBlock();
      final BftBlockInterface bftBlockInterface =
          protocolContext.getConsensusContext(BftContext.class).getBlockInterface();
      blockToPublish =
          bftBlockInterface.replaceRoundInBlock(
              preparedBlock,
              roundState.getRoundIdentifier().getRoundNumber(),
              BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    }

    LOG.debug(" proposal - new/prepared block hash : {}", blockToPublish.getHash());

    updateStateWithProposalAndTransmit(
        blockToPublish,
        roundChangeArtifacts.getRoundChanges(),
        bestPreparedCertificate.map(PreparedCertificate::getPrepares).orElse(emptyList()));
  }

  /**
   * Update state with proposal and transmit.
   *
   * @param block the block
   */
  protected void updateStateWithProposalAndTransmit(final Block block) {
    updateStateWithProposalAndTransmit(block, emptyList(), emptyList());
  }

  /**
   * Update state with proposal and transmit.
   *
   * @param block the block
   * @param roundChanges the round changes
   * @param prepares the prepares
   */
  protected void updateStateWithProposalAndTransmit(
      final Block block,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares) {
    final Proposal proposal;
    try {
      proposal = messageFactory.createProposal(getRoundIdentifier(), block, roundChanges, prepares);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to create a signed Proposal, waiting for next round.", e);
      return;
    }

    transmitter.multicastProposal(
        proposal.getRoundIdentifier(),
        proposal.getSignedPayload().getPayload().getProposedBlock(),
        roundChanges,
        prepares);
    if (updateStateWithProposedBlock(proposal)) {
      sendPrepare(block);
    }
  }

  /**
   * Handle proposal message.
   *
   * @param msg the msg
   */
  public void handleProposalMessage(final Proposal msg) {
    LOG.debug(
        "Received a proposal message. round={}. author={}",
        roundState.getRoundIdentifier(),
        msg.getAuthor());
    final Block block = msg.getSignedPayload().getPayload().getProposedBlock();
    if (updateStateWithProposedBlock(msg)) {
      sendPrepare(block);
    }
  }

  private void sendPrepare(final Block block) {
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

  /**
   * Handle prepare message.
   *
   * @param msg the msg
   */
  public void handlePrepareMessage(final Prepare msg) {
    LOG.debug(
        "Received a prepare message. round={}. author={}",
        roundState.getRoundIdentifier(),
        msg.getAuthor());
    peerIsPrepared(msg);
  }

  /**
   * Handle commit message.
   *
   * @param msg the msg
   */
  public void handleCommitMessage(final Commit msg) {
    LOG.debug(
        "Received a commit message. round={}. author={}",
        roundState.getRoundIdentifier(),
        msg.getAuthor());
    peerIsCommitted(msg);
  }

  /**
   * Construct prepared certificate.
   *
   * @return the optional PreparedCertificate
   */
  public Optional<PreparedCertificate> constructPreparedCertificate() {
    return roundState.constructPreparedCertificate();
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
          "Importing proposed block to chain. round={}, hash={}",
          getRoundIdentifier(),
          blockToImport.getHash());
    } else {
      LOG.debug(
          "Importing proposed block to chain. round={}, hash={}",
          getRoundIdentifier(),
          blockToImport.getHash());
    }

    LOG.trace("Importing proposed block with extraData={}", extraData);
    final BlockImporter blockImporter =
        protocolSchedule.getByBlockHeader(blockToImport.getHeader()).getBlockImporter();
    final BlockImportResult result =
        blockImporter.importBlock(protocolContext, blockToImport, HeaderValidationMode.FULL);
    if (!result.isImported()) {
      LOG.error(
          "Failed to import proposed block to chain. block={} extraData={} blockHeader={}",
          blockNumber,
          extraData,
          blockToImport.getHeader());
    } else {
      notifyNewBlockListeners(blockToImport);
    }
  }

  private SECPSignature createCommitSeal(final Block block) {
    final Block commitBlock = createCommitBlock(block);
    final BlockHeader proposedHeader = commitBlock.getHeader();
    final BftExtraData extraData = bftExtraDataCodec.decode(proposedHeader);
    final Hash commitHash =
        new BftBlockHashing(bftExtraDataCodec)
            .calculateDataHashForCommittedSeal(proposedHeader, extraData);
    return nodeKey.sign(commitHash);
  }

  private Block createCommitBlock(final Block block) {
    final BftBlockInterface bftBlockInterface =
        protocolContext.getConsensusContext(BftContext.class).getBlockInterface();
    return bftBlockInterface.replaceRoundInBlock(
        block,
        getRoundIdentifier().getRoundNumber(),
        BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
  }

  private void notifyNewBlockListeners(final Block block) {
    observers.forEach(obs -> obs.blockMined(block));
  }
}
