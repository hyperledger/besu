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
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
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
      final IbftMessageTransmitter transmitter) {
    this.roundState = roundState;
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.blockImporter = blockImporter;
    this.observers = observers;
    this.nodeKeys = nodeKeys;
    this.messageFactory = messageFactory;
    this.transmitter = transmitter;
  }

  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundState.getRoundIdentifier();
  }

  public void createAndSendProposalMessage(final long headerTimeStampSeconds) {
    LOG.info("Creating proposed block.");
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    transmitter.multicastProposal(roundState.getRoundIdentifier(), block);

    updateStateWithProposedBlock(
        messageFactory.createSignedProposalPayload(roundState.getRoundIdentifier(), block));
  }

  public void handleProposalMessage(final SignedData<ProposalPayload> msg) {
    LOG.info("Received a Proposal message.");
    final Block block = msg.getPayload().getBlock();
    final boolean wasCommitted = roundState.isCommitted();

    if (updateStateWithProposedBlock(msg)) {
      LOG.info("Sending prepare message.");
      transmitter.multicastPrepare(getRoundIdentifier(), block.getHash());
      final SignedData<PreparePayload> localPrepareMessage =
          messageFactory.createSignedPreparePayload(
              roundState.getRoundIdentifier(), block.getHash());
      peerIsPrepared(localPrepareMessage);
    }

    if (wasCommitted != roundState.isCommitted()) {
      importBlockToChain();
    }
  }

  public void handlePrepareMessage(final SignedData<PreparePayload> msg) {
    LOG.info("Received a prepare message.");
    peerIsPrepared(msg);
  }

  public void handleCommitMessage(final SignedData<CommitPayload> msg) {
    LOG.info("Received a commit message.");
    peerIsCommitted(msg);
  }

  public Optional<PreparedCertificate> createPrepareCertificate() {
    return roundState.constructPreparedCertificate();
  }

  private boolean updateStateWithProposedBlock(final SignedData<ProposalPayload> msg) {
    final boolean wasPrepared = roundState.isPrepared();
    final boolean blockAccepted = roundState.setProposedBlock(msg);
    if (blockAccepted) {
      // There are times handling a proposed block is enough to enter prepared.
      if (wasPrepared != roundState.isPrepared()) {
        LOG.info("Sending commit message.");
        final Block block = roundState.getProposedBlock().get();
        transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), createCommitSeal(block));
      }
      final SignedData<CommitPayload> localCommitMessage =
          messageFactory.createSignedCommitPayload(
              roundState.getRoundIdentifier(),
              msg.getPayload().getBlock().getHash(),
              createCommitSeal(roundState.getProposedBlock().get()));
      peerIsCommitted(localCommitMessage);
    }

    return blockAccepted;
  }

  private void peerIsPrepared(final SignedData<PreparePayload> msg) {
    final boolean wasPrepared = roundState.isPrepared();
    roundState.addPrepareMessage(msg);
    if (wasPrepared != roundState.isPrepared()) {
      LOG.info("Sending commit message.");
      final Block block = roundState.getProposedBlock().get();
      transmitter.multicastCommit(getRoundIdentifier(), block.getHash(), createCommitSeal(block));
    }
  }

  private void peerIsCommitted(final SignedData<CommitPayload> msg) {
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

    LOG.info("Importing block to chain.");
    boolean result =
        blockImporter.importBlock(protocolContext, blockToImport, HeaderValidationMode.FULL);
    if (!result) {
      LOG.info("Failed to import block to chain.");
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
