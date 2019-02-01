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

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.findLatestPreparedCertificate;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMessageTransmitter;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
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
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    final IbftExtraData extraData = IbftExtraData.decode(block.getHeader().getExtraData());
    LOG.info(
        "Creating proposed block. round={} extraData={} blockHeader={}",
        roundState.getRoundIdentifier(),
        extraData,
        block.getHeader());
    transmitter.multicastProposal(roundState.getRoundIdentifier(), block);

    updateStateWithProposedBlock(
        messageFactory.createSignedProposalPayload(roundState.getRoundIdentifier(), block));
  }

  public void startRoundWith(
      final RoundChangeCertificate roundChangeCertificate, final long headerTimestamp) {
    final Optional<PreparedCertificate> latestCertificate =
        findLatestPreparedCertificate(roundChangeCertificate.getRoundChangePayloads());

    Proposal proposal;
    if (!latestCertificate.isPresent()) {
      LOG.trace("Multicasting NewRound with new block. round={}", roundState.getRoundIdentifier());
      final Block block = blockCreator.createBlock(headerTimestamp);
      proposal = messageFactory.createSignedProposalPayload(getRoundIdentifier(), block);
    } else {
      LOG.trace(
          "Multicasting NewRound from PreparedCertificate. round={}",
          roundState.getRoundIdentifier());
      proposal = createProposalFromPreparedCertificate(latestCertificate.get());
    }
    transmitter.multicastNewRound(
        getRoundIdentifier(), roundChangeCertificate, proposal.getSignedPayload());
    updateStateWithProposedBlock(proposal);
  }

  private Proposal createProposalFromPreparedCertificate(
      final PreparedCertificate preparedCertificate) {
    final Block block = preparedCertificate.getProposalPayload().getPayload().getBlock();

    final IbftExtraData prevExtraData = IbftExtraData.decode(block.getHeader().getExtraData());
    final IbftExtraData extraDataToPublish =
        new IbftExtraData(
            prevExtraData.getVanityData(),
            prevExtraData.getSeals(),
            prevExtraData.getVote(),
            getRoundIdentifier().getRoundNumber(),
            prevExtraData.getValidators());

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(block.getHeader());
    headerBuilder
        .extraData(extraDataToPublish.encode())
        .blockHashFunction(
            blockHeader ->
                IbftBlockHashing.calculateDataHashForCommittedSeal(
                    blockHeader, extraDataToPublish));
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();
    final Block newBlock = new Block(newHeader, block.getBody());
    LOG.debug(
        "Created proposal from prepared certificate blockHeader={} extraData={}",
        block.getHeader(),
        extraDataToPublish);
    return messageFactory.createSignedProposalPayload(getRoundIdentifier(), newBlock);
  }

  public void handleProposalMessage(final Proposal msg) {
    LOG.info("Handling a Proposal message.");

    if (getRoundIdentifier().getRoundNumber() != 0) {
      LOG.error("Illegally received a Proposal message when not in Round 0.");
      return;
    }
    actionReceivedProposal(msg);
  }

  public void handleProposalFromNewRound(final NewRound msg) {
    LOG.info("Handling a New Round Proposal.");

    if (getRoundIdentifier().getRoundNumber() == 0) {
      LOG.error("Illegally received a NewRound message when in Round 0.");
      return;
    }
    actionReceivedProposal(new Proposal(msg.getSignedPayload().getPayload().getProposalPayload()));
  }

  private void actionReceivedProposal(final Proposal msg) {
    final Block block = msg.getSignedPayload().getPayload().getBlock();

    if (updateStateWithProposedBlock(msg)) {
      LOG.info("Sending prepare message.");
      transmitter.multicastPrepare(getRoundIdentifier(), block.getHash());
      final Prepare localPrepareMessage =
          messageFactory.createSignedPreparePayload(
              roundState.getRoundIdentifier(), block.getHash());
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

  public Optional<PreparedCertificate> createPrepareCertificate() {
    return roundState.constructPreparedCertificate();
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
          messageFactory.createSignedCommitPayload(
              roundState.getRoundIdentifier(),
              msg.getSignedPayload().getPayload().getBlock().getHash(),
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
    LOG.info("Importing block to chain. block={} extraData={}", blockNumber, extraData);
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
