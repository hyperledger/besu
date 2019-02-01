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

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.Gossiper;
import tech.pegasys.pantheon.consensus.ibft.IbftGossip;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.IbftMessage;
import tech.pegasys.pantheon.consensus.ibft.payload.Authored;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftController {

  private static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;
  private final IbftFinalState ibftFinalState;
  private final IbftBlockHeightManagerFactory ibftBlockHeightManagerFactory;
  private final Map<Long, List<Message>> futureMessages;
  private BlockHeightManager currentHeightManager;
  private final Gossiper gossiper;

  public IbftController(
      final Blockchain blockchain,
      final IbftFinalState ibftFinalState,
      final IbftBlockHeightManagerFactory ibftBlockHeightManagerFactory,
      final IbftGossip gossiper) {
    this(blockchain, ibftFinalState, ibftBlockHeightManagerFactory, gossiper, Maps.newHashMap());
  }

  @VisibleForTesting
  public IbftController(
      final Blockchain blockchain,
      final IbftFinalState ibftFinalState,
      final IbftBlockHeightManagerFactory ibftBlockHeightManagerFactory,
      final Gossiper gossiper,
      final Map<Long, List<Message>> futureMessages) {
    this.blockchain = blockchain;
    this.ibftFinalState = ibftFinalState;
    this.ibftBlockHeightManagerFactory = ibftBlockHeightManagerFactory;
    this.futureMessages = futureMessages;
    this.gossiper = gossiper;
  }

  public void start() {
    startNewHeightManager(blockchain.getChainHeadHeader());
  }

  public void handleMessageEvent(final IbftReceivedMessageEvent msg) {
    handleMessage(msg.getMessage());
  }

  private void handleMessage(final Message message) {
    final MessageData messageData = message.getData();
    switch (messageData.getCode()) {
      case IbftV2.PROPOSAL:
        consumeMessage(
            message,
            ProposalMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleProposalPayload);
        break;

      case IbftV2.PREPARE:
        consumeMessage(
            message,
            PrepareMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handlePreparePayload);
        break;

      case IbftV2.COMMIT:
        consumeMessage(
            message,
            CommitMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleCommitPayload);
        break;

      case IbftV2.ROUND_CHANGE:
        consumeMessage(
            message,
            RoundChangeMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleRoundChangePayload);
        break;

      case IbftV2.NEW_ROUND:
        consumeMessage(
            message,
            NewRoundMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleNewRoundPayload);
        break;

      default:
        throw new IllegalArgumentException(
            String.format(
                "Received message with messageCode=%d does not conform to any recognised IBFT message structure",
                message.getData().getCode()));
    }
  }

  private <P extends IbftMessage<?>> void consumeMessage(
      final Message message, final P ibftMessage, final Consumer<P> handleMessage) {
    LOG.debug(
        "Received IBFT message messageType={} payload={}",
        ibftMessage.getMessageType(),
        ibftMessage);
    if (processMessage(ibftMessage, message)) {
      gossiper.send(message);
      handleMessage.accept(ibftMessage);
    }
  }

  public void handleNewBlockEvent(final NewChainHead newChainHead) {
    final BlockHeader newBlockHeader = newChainHead.getNewChainHeadHeader();
    final BlockHeader currentMiningParent = currentHeightManager.getParentBlockHeader();
    if (newBlockHeader.getNumber() < currentMiningParent.getNumber()) {
      LOG.debug(
          "Discarding NewChainHead event, was for previous block height. chainHeight={} eventHeight={}",
          currentMiningParent.getNumber(),
          newBlockHeader.getNumber());
      return;
    }

    if (newBlockHeader.getNumber() == currentMiningParent.getNumber()) {
      if (newBlockHeader.getHash().equals(currentMiningParent.getHash())) {
        LOG.debug(
            "Discarding duplicate NewChainHead event. chainHeight={} newBlockHash={} parentBlockHash",
            newBlockHeader.getNumber(),
            newBlockHeader.getHash(),
            currentMiningParent.getHash());
      } else {
        LOG.error(
            "Subsequent NewChainHead event at same block height indicates chain fork. chainHeight={}",
            currentMiningParent.getNumber());
      }
      return;
    }
    startNewHeightManager(newBlockHeader);
  }

  public void handleBlockTimerExpiry(final BlockTimerExpiry blockTimerExpiry) {
    final ConsensusRoundIdentifier roundIndentifier = blockTimerExpiry.getRoundIndentifier();
    if (isMsgForCurrentHeight(roundIndentifier)) {
      currentHeightManager.handleBlockTimerExpiry(roundIndentifier);
    } else {
      LOG.debug(
          "Block timer event discarded as it is not for current block height chainHeight={} eventHeight={}",
          currentHeightManager.getChainHeight(),
          roundIndentifier.getSequenceNumber());
    }
  }

  public void handleRoundExpiry(final RoundExpiry roundExpiry) {
    if (isMsgForCurrentHeight(roundExpiry.getView())) {
      currentHeightManager.roundExpired(roundExpiry);
    } else {
      LOG.debug(
          "Round expiry event discarded as it is not for current block height chainHeight={} eventHeight={}",
          currentHeightManager.getChainHeight(),
          roundExpiry.getView().getSequenceNumber());
    }
  }

  private void startNewHeightManager(final BlockHeader parentHeader) {
    currentHeightManager = ibftBlockHeightManagerFactory.create(parentHeader);
    currentHeightManager.start();
    final long newChainHeight = currentHeightManager.getChainHeight();
    futureMessages.getOrDefault(newChainHeight, emptyList()).forEach(this::handleMessage);
    futureMessages.remove(newChainHeight);
  }

  private boolean processMessage(final IbftMessage<?> msg, final Message rawMsg) {
    final ConsensusRoundIdentifier msgRoundIdentifier = msg.getRoundIdentifier();
    if (isMsgForCurrentHeight(msgRoundIdentifier)) {
      return isMsgFromKnownValidator(msg) && ibftFinalState.isLocalNodeValidator();
    } else if (isMsgForFutureChainHeight(msgRoundIdentifier)) {
      addMessageToFutureMessageBuffer(msgRoundIdentifier.getSequenceNumber(), rawMsg);
    } else {
      LOG.debug(
          "IBFT message discarded as it is from a previous block height messageType={} chainHeight={} eventHeight={}",
          msg.getMessageType(),
          currentHeightManager.getChainHeight(),
          msgRoundIdentifier.getSequenceNumber());
    }
    return false;
  }

  private boolean isMsgFromKnownValidator(final Authored msg) {
    return ibftFinalState.getValidators().contains(msg.getAuthor());
  }

  private boolean isMsgForCurrentHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() == currentHeightManager.getChainHeight();
  }

  private boolean isMsgForFutureChainHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() > currentHeightManager.getChainHeight();
  }

  private void addMessageToFutureMessageBuffer(final long chainHeight, final Message rawMsg) {
    if (!futureMessages.containsKey(chainHeight)) {
      futureMessages.put(chainHeight, Lists.newArrayList());
    }
    futureMessages.get(chainHeight).add(rawMsg);
  }
}
