/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.Authored;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockchain;
import org.hyperledger.besu.consensus.qbft.core.types.QbftEventHandler;
import org.hyperledger.besu.consensus.qbft.core.types.QbftFinalState;
import org.hyperledger.besu.consensus.qbft.core.types.QbftNewChainHead;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Qbft controller. */
public class QbftController implements QbftEventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(QbftController.class);
  private final QbftBlockchain blockchain;
  private final QbftFinalState finalState;
  private final FutureMessageBuffer futureMessageBuffer;
  private final Gossiper gossiper;
  private final MessageTracker duplicateMessageTracker;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final QbftBlockCodec blockEncoder;
  private BaseQbftBlockHeightManager currentHeightManager;
  private final QbftBlockHeightManagerFactory qbftBlockHeightManagerFactory;

  /**
   * Instantiates a new Qbft controller.
   *
   * @param blockchain the blockchain
   * @param finalState the qbft final state
   * @param qbftBlockHeightManagerFactory the qbft block height manager factory
   * @param gossiper the gossiper
   * @param duplicateMessageTracker the duplicate message tracker
   * @param futureMessageBuffer the future message buffer
   * @param blockEncoder the block encoder
   */
  public QbftController(
      final QbftBlockchain blockchain,
      final QbftFinalState finalState,
      final QbftBlockHeightManagerFactory qbftBlockHeightManagerFactory,
      final Gossiper gossiper,
      final MessageTracker duplicateMessageTracker,
      final FutureMessageBuffer futureMessageBuffer,
      final QbftBlockCodec blockEncoder) {

    this.blockchain = blockchain;
    this.finalState = finalState;
    this.futureMessageBuffer = futureMessageBuffer;
    this.gossiper = gossiper;
    this.duplicateMessageTracker = duplicateMessageTracker;
    this.qbftBlockHeightManagerFactory = qbftBlockHeightManagerFactory;
    this.blockEncoder = blockEncoder;
  }

  private void handleMessage(final Message message) {
    final MessageData messageData = message.getData();

    switch (messageData.getCode()) {
      case QbftV1.PROPOSAL:
        consumeMessage(
            message,
            ProposalMessageData.fromMessageData(messageData).decode(blockEncoder),
            currentHeightManager::handleProposalPayload);
        break;

      case QbftV1.PREPARE:
        consumeMessage(
            message,
            PrepareMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handlePreparePayload);
        break;

      case QbftV1.COMMIT:
        consumeMessage(
            message,
            CommitMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleCommitPayload);
        break;

      case QbftV1.ROUND_CHANGE:
        consumeMessage(
            message,
            RoundChangeMessageData.fromMessageData(messageData).decode(blockEncoder),
            currentHeightManager::handleRoundChangePayload);
        break;

      default:
        throw new IllegalArgumentException(
            String.format(
                "Received message with messageCode=%d does not conform to any recognised QBFT message structure",
                message.getData().getCode()));
    }
  }

  private void createNewHeightManager(final QbftBlockHeader parentHeader) {
    currentHeightManager = qbftBlockHeightManagerFactory.create(parentHeader);
  }

  private BaseQbftBlockHeightManager getCurrentHeightManager() {
    return currentHeightManager;
  }

  /* Replace the current height manager with a no-op height manager. */
  private void stopCurrentHeightManager(final QbftBlockHeader parentHeader) {
    currentHeightManager = qbftBlockHeightManagerFactory.createNoOpBlockHeightManager(parentHeader);
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      startNewHeightManager(blockchain.getChainHeadHeader());
    } else {
      // In normal circumstances the height manager should only be started once. If the caller
      // has stopped the height manager (e.g. while sync completes) they must call stop() before
      // starting the height manager again.
      throw new IllegalStateException(
          "Attempt to start new height manager without stopping previous manager");
    }
  }

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      stopCurrentHeightManager(blockchain.getChainHeadHeader());
      LOG.debug("QBFT height manager stop");
    }
  }

  @Override
  public void handleMessageEvent(final BftReceivedMessageEvent msg) {
    final MessageData data = msg.getMessage().getData();
    if (!duplicateMessageTracker.hasSeenMessage(data)) {
      duplicateMessageTracker.addSeenMessage(data);
      handleMessage(msg.getMessage());
    } else {
      LOG.trace("Discarded duplicate message");
    }
  }

  /**
   * Consume message.
   *
   * @param <P> the type parameter of BftMessage
   * @param message the message
   * @param bftMessage the bft message
   * @param handleMessage the handle message
   */
  protected <P extends BftMessage<?>> void consumeMessage(
      final Message message, final P bftMessage, final Consumer<P> handleMessage) {
    LOG.trace("Received BFT {} message", bftMessage.getClass().getSimpleName());

    // Discard all messages which target the BLOCKCHAIN height (which SHOULD be 1 less than
    // the currentHeightManager, but CAN be the same directly following import).
    if (bftMessage.getRoundIdentifier().getSequenceNumber()
        <= blockchain.getChainHeadBlockNumber()) {
      LOG.debug(
          "Discarding a message which targets a height {} not above current chain height {}.",
          bftMessage.getRoundIdentifier().getSequenceNumber(),
          blockchain.getChainHeadBlockNumber());
      return;
    }

    if (processMessage(bftMessage, message)) {
      gossiper.send(message);
      handleMessage.accept(bftMessage);
    }
  }

  @Override
  public void handleNewBlockEvent(final QbftNewChainHead newChainHead) {
    final QbftBlockHeader newBlockHeader = newChainHead.newChainHeadHeader();
    final QbftBlockHeader currentMiningParent = getCurrentHeightManager().getParentBlockHeader();
    LOG.debug(
        "New chain head detected (block number={})," + " currently mining on top of {}.",
        newBlockHeader.getNumber(),
        currentMiningParent.getNumber());
    if (newBlockHeader.getNumber() < currentMiningParent.getNumber()) {
      LOG.trace(
          "Discarding NewChainHead event, was for previous block height. chainHeight={} eventHeight={}",
          currentMiningParent.getNumber(),
          newBlockHeader.getNumber());
      return;
    }

    if (newBlockHeader.getNumber() == currentMiningParent.getNumber()) {
      if (newBlockHeader.getHash().equals(currentMiningParent.getHash())) {
        LOG.trace(
            "Discarding duplicate NewChainHead event. chainHeight={} newBlockHash={} parentBlockHash={}",
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

  @Override
  public void handleBlockTimerExpiry(final BlockTimerExpiry blockTimerExpiry) {
    final ConsensusRoundIdentifier roundIdentifier = blockTimerExpiry.getRoundIdentifier();
    if (isMsgForCurrentHeight(roundIdentifier)) {
      getCurrentHeightManager().handleBlockTimerExpiry(roundIdentifier);
    } else {
      LOG.trace(
          "Block timer event discarded as it is not for current block height chainHeight={} eventHeight={}",
          getCurrentHeightManager().getChainHeight(),
          roundIdentifier.getSequenceNumber());
    }
  }

  @Override
  public void handleRoundExpiry(final RoundExpiry roundExpiry) {
    // Discard all messages which target the BLOCKCHAIN height (which SHOULD be 1 less than
    // the currentHeightManager, but CAN be the same directly following import).
    if (roundExpiry.getView().getSequenceNumber() <= blockchain.getChainHeadBlockNumber()) {
      LOG.debug("Discarding a round-expiry which targets a height not above current chain height.");
      return;
    }

    if (isMsgForCurrentHeight(roundExpiry.getView())) {
      getCurrentHeightManager().roundExpired(roundExpiry);
    } else {
      LOG.trace(
          "Round expiry event discarded as it is not for current block height chainHeight={} eventHeight={}",
          getCurrentHeightManager().getChainHeight(),
          roundExpiry.getView().getSequenceNumber());
    }
  }

  private void startNewHeightManager(final QbftBlockHeader parentHeader) {
    createNewHeightManager(parentHeader);
    final long newChainHeight = getCurrentHeightManager().getChainHeight();
    futureMessageBuffer.retrieveMessagesForHeight(newChainHeight).forEach(this::handleMessage);
  }

  private boolean processMessage(final BftMessage<?> msg, final Message rawMsg) {
    final ConsensusRoundIdentifier msgRoundIdentifier = msg.getRoundIdentifier();
    if (isMsgForCurrentHeight(msgRoundIdentifier)) {
      return isMsgFromKnownValidator(msg) && finalState.isLocalNodeValidator();
    } else if (isMsgForFutureChainHeight(msgRoundIdentifier)) {
      LOG.trace("Received message for future block height round={}", msgRoundIdentifier);
      futureMessageBuffer.addMessage(msgRoundIdentifier.getSequenceNumber(), rawMsg);
    } else {
      LOG.trace(
          "BFT message discarded as it is from a previous block height messageType={} chainHeight={} eventHeight={}",
          msg.getMessageType(),
          getCurrentHeightManager().getChainHeight(),
          msgRoundIdentifier.getSequenceNumber());
    }
    return false;
  }

  private boolean isMsgFromKnownValidator(final Authored msg) {
    return finalState.getValidators().contains(msg.getAuthor());
  }

  private boolean isMsgForCurrentHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() == getCurrentHeightManager().getChainHeight();
  }

  private boolean isMsgForFutureChainHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() > getCurrentHeightManager().getChainHeight();
  }
}
