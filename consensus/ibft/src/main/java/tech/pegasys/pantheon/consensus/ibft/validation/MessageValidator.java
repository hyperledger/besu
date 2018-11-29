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
package tech.pegasys.pantheon.consensus.ibft.validation;

import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.FULL;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.AbstractIbftUnsignedInRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedCommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrePrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrepareMessageData;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;

import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessageValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Collection<Address> validators;
  private final Address expectedProposer;
  private final ConsensusRoundIdentifier roundIdentifier;
  private final BlockHeaderValidator<IbftContext> headerValidator;
  private final ProtocolContext<IbftContext> protocolContext;
  private final BlockHeader parentHeader;

  private Optional<IbftSignedMessageData<IbftUnsignedPrePrepareMessageData>> preprepareMessage =
      Optional.empty();

  public MessageValidator(
      final Collection<Address> validators,
      final Address expectedProposer,
      final ConsensusRoundIdentifier roundIdentifier,
      final BlockHeaderValidator<IbftContext> headerValidator,
      final ProtocolContext<IbftContext> protocolContext,
      final BlockHeader parentHeader) {
    this.validators = validators;
    this.expectedProposer = expectedProposer;
    this.roundIdentifier = roundIdentifier;
    this.headerValidator = headerValidator;
    this.protocolContext = protocolContext;
    this.parentHeader = parentHeader;
  }

  public boolean addPreprepareMessage(
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> msg) {

    if (preprepareMessage.isPresent()) {
      return handleSubsequentPreprepareMessage(preprepareMessage.get(), msg);
    }

    if (!validatePreprepareMessage(msg)) {
      return false;
    }

    if (!validateBlocKMatchesPrepareMessageRound(msg.getUnsignedMessageData())) {
      return false;
    }

    preprepareMessage = Optional.of(msg);
    return true;
  }

  private boolean validatePreprepareMessage(
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> msg) {

    if (!msg.getUnsignedMessageData().getRoundIdentifier().equals(roundIdentifier)) {
      LOG.info("Invalid Preprepare message, does not match current round.");
      return false;
    }

    if (!msg.getSender().equals(expectedProposer)) {
      LOG.info(
          "Invalid Preprepare message, was not created by the proposer expected for the "
              + "associated round.");
      return false;
    }

    final Block proposedBlock = msg.getUnsignedMessageData().getBlock();
    if (!headerValidator.validateHeader(
        proposedBlock.getHeader(), parentHeader, protocolContext, FULL)) {
      LOG.info("Invalid Prepare message, block did not pass header validation.");
      return false;
    }

    return true;
  }

  private boolean handleSubsequentPreprepareMessage(
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> existingMsg,
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> newMsg) {
    if (!existingMsg.getSender().equals(newMsg.getSender())) {
      LOG.debug("Received subsequent invalid Preprepare message; sender differs from original.");
      return false;
    }

    final IbftUnsignedPrePrepareMessageData existingData = existingMsg.getUnsignedMessageData();
    final IbftUnsignedPrePrepareMessageData newData = newMsg.getUnsignedMessageData();

    if (!preprepareMessagesAreIdentical(existingData, newData)) {
      LOG.debug("Received subsequent invalid Preprepare message; content differs from original.");
      return false;
    }

    return true;
  }

  public boolean validatePrepareMessage(
      final IbftSignedMessageData<IbftUnsignedPrepareMessageData> msg) {
    final String msgType = "Prepare";

    if (!isMessageForCurrentRoundFromValidatorAndPreprareMessageAvailable(msg, msgType)) {
      return false;
    }

    if (msg.getSender().equals(expectedProposer)) {
      LOG.info("Illegal Prepare message; was sent by the round's proposer.");
      return false;
    }

    return validateDigestMatchesPreprepareBlock(msg.getUnsignedMessageData().getDigest(), msgType);
  }

  public boolean validateCommmitMessage(
      final IbftSignedMessageData<IbftUnsignedCommitMessageData> msg) {
    final String msgType = "Commit";

    if (!isMessageForCurrentRoundFromValidatorAndPreprareMessageAvailable(msg, msgType)) {
      return false;
    }

    final Block proposedBlock = preprepareMessage.get().getUnsignedMessageData().getBlock();
    final Address commitSealCreator =
        Util.signatureToAddress(
            msg.getUnsignedMessageData().getCommitSeal(), proposedBlock.getHash());

    if (!commitSealCreator.equals(msg.getSender())) {
      LOG.info("Invalid Commit message. Seal was not created by the message transmitter.");
      return false;
    }

    return validateDigestMatchesPreprepareBlock(msg.getUnsignedMessageData().getDigest(), msgType);
  }

  private boolean isMessageForCurrentRoundFromValidatorAndPreprareMessageAvailable(
      final IbftSignedMessageData<? extends AbstractIbftUnsignedInRoundMessageData> msg,
      final String msgType) {

    if (!msg.getUnsignedMessageData().getRoundIdentifier().equals(roundIdentifier)) {
      LOG.info("Invalid {} message, does not match current round.", msgType);
      return false;
    }

    if (!validators.contains(msg.getSender())) {
      LOG.info(
          "Invalid {} message, was not transmitted by a validator for the " + "associated round.",
          msgType);
      return false;
    }

    if (!preprepareMessage.isPresent()) {
      LOG.info(
          "Unable to validate {} message. No Preprepare message exists against "
              + "which to validate block digest.",
          msgType);
      return false;
    }
    return true;
  }

  private boolean validateDigestMatchesPreprepareBlock(final Hash digest, final String msgType) {
    final Block proposedBlock = preprepareMessage.get().getUnsignedMessageData().getBlock();
    if (!digest.equals(proposedBlock.getHash())) {
      LOG.info(
          "Illegal {} message, digest does not match the block in the Prepare Message.", msgType);
      return false;
    }
    return true;
  }

  private boolean preprepareMessagesAreIdentical(
      final IbftUnsignedPrePrepareMessageData right, final IbftUnsignedPrePrepareMessageData left) {
    return right.getBlock().getHash().equals(left.getBlock().getHash())
        && right.getRoundIdentifier().equals(left.getRoundIdentifier());
  }

  private boolean validateBlocKMatchesPrepareMessageRound(
      final IbftUnsignedPrePrepareMessageData msgData) {
    final ConsensusRoundIdentifier msgRound = msgData.getRoundIdentifier();
    final IbftExtraData extraData =
        IbftExtraData.decode(msgData.getBlock().getHeader().getExtraData());
    if (extraData.getRound() != msgRound.getRoundNumber()) {
      LOG.info("Invalid Preprepare message, round number in block does not match that in message.");
      return false;
    }
    return true;
  }
}
