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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMessageTransmitter;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftRound {

  private static final Logger LOG = LogManager.getLogger();

  private final Subscribers<MinedBlockObserver> observers;
  private final RoundState roundState;
  private final IbftBlockCreator blockCreator;
  private final ProtocolContext<IbftContext> protocolContext;
  private final BlockImporter<IbftContext> blockImporter;
  private final KeyPair nodeKeys;
  private final MessageFactory messageFactory; // used only to create stored local msgs
  private final IbftMessageTransmitter transmitter;

  public IbftRound(
      final RoundState roundState,
      final IbftBlockCreator blockCreator,
      final ProtocolContext<IbftContext> protocolContext,
      final BlockImporter<IbftContext> blockImporter,
      final Subscribers<MinedBlockObserver> observers,
      final KeyPair nodeKeys,
      final MessageFactory messageFactory,
      final IbftMessageTransmitter transmitter,
      final RoundTimer roundTimer) {
    this.roundState = roundState;
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.blockImporter = blockImporter;
    this.observers = observers;
    this.nodeKeys = nodeKeys;
    this.messageFactory = messageFactory;
    this.transmitter = transmitter;

    roundTimer.startTimer(getRoundIdentifier());
  }

  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundState.getRoundIdentifier();
  }

  public void createAndSendProposalMessage(final long headerTimeStampSeconds) {
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    final IbftExtraData extraData = IbftExtraData.decode(block.getHeader().getExtraData());
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
      blockToPublish =
          IbftBlockInterface.replaceRoundInBlock(
              bestBlockFromRoundChange.get(),
              getRoundIdentifier().getRoundNumber(),
              IbftBlockHashing::calculateDataHashForCommittedSeal);
    }

    updateStateWithProposalAndTransmit(blockToPublish, Optional.of(roundChangeCertificate));
  }

  private void updateStateWithProposalAndTransmit(
      final Block block, final Optional<RoundChangeCertificate> roundChangeCertificate) {
    final Proposal proposal =
        messageFactory.createProposal(getRoundIdentifier(), block, roundChangeCertificate);

    transmitter.multicastProposal(
        proposal.getRoundIdentifier(), proposal.getBlock(), proposal.getRoundChangeCertificate());
    updateStateWithProposedBlock(proposal);
  }

  public void handleProposalMessage(final Proposal msg) {
    LOG.debug("Received a proposal message. round={}", roundState.getRoundIdentifier());
    final Block block = msg.getBlock();

    if (updateStateWithProposedBlock(msg)) {
      LOG.debug("Sending prepare message. round={}", roundState.getRoundIdentifier());
      final Prepare localPrepareMessage =
          messageFactory.createPrepare(getRoundIdentifier(), block.getHash());
      transmitter.multicastPrepare(
          localPrepareMessage.getRoundIdentifier(), localPrepareMessage.getDigest());
      peerIsPrepared(localPrepareMessage);
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
      // There are times handling a proposed block is enough to enter prepared.
      if (wasPrepared != roundState.isPrepared()) {
        LOG.debug("Sending commit message. round={}", roundState.getRoundIdentifier());
        final Block block = roundState.getProposedBlock().get();
        transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), createCommitSeal(block));
      }
      if (wasCommitted != roundState.isCommitted()) {
        importBlockToChain();
      }

      final Commit localCommitMessage =
          messageFactory.createCommit(
              roundState.getRoundIdentifier(),
              msg.getBlock().getHash(),
              createCommitSeal(roundState.getProposedBlock().get()));
      peerIsCommitted(localCommitMessage);
    }

    return blockAccepted;
  }

  private void peerIsPrepared(final Prepare msg) {
    final boolean wasPrepared = roundState.isPrepared();
    roundState.addPrepareMessage(msg);
    if (wasPrepared != roundState.isPrepared()) {
      LOG.debug("Sending commit message. round={}", roundState.getRoundIdentifier());
      final Block block = roundState.getProposedBlock().get();
      transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), createCommitSeal(block));
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
        IbftHelpers.createSealedBlock(
            roundState.getProposedBlock().get(), roundState.getCommitSeals());

    final long blockNumber = blockToImport.getHeader().getNumber();
    final IbftExtraData extraData = IbftExtraData.decode(blockToImport.getHeader().getExtraData());
    LOG.info(
        "Importing block to chain. round={}, hash={}",
        getRoundIdentifier(),
        blockToImport.getHash());
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

  private Signature createCommitSeal(final Block block) {
    final BlockHeader proposedHeader = block.getHeader();
    final IbftExtraData extraData = IbftExtraData.decode(proposedHeader.getExtraData());
    final Hash commitHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(proposedHeader, extraData);
    return SECP256K1.sign(commitHash, nodeKeys);
  }

  private void notifyNewBlockListeners(final Block block) {
    observers.forEach(obs -> obs.blockMined(block));
  }
}
