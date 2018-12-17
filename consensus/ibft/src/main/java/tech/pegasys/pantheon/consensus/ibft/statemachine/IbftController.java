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
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.CommitMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.NewRoundMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.PrepareMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.ProposalMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.RoundChangeMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.Payload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.List;
import java.util.Map;

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
  private final Map<Long, List<MessageData>> futureMessages;
  private IbftBlockHeightManager currentHeightManager;

  public IbftController(
      final Blockchain blockchain,
      final IbftFinalState ibftFinalState,
      final IbftBlockHeightManagerFactory ibftBlockHeightManagerFactory) {
    this(blockchain, ibftFinalState, ibftBlockHeightManagerFactory, Maps.newHashMap());
  }

  @VisibleForTesting
  IbftController(
      final Blockchain blockchain,
      final IbftFinalState ibftFinalState,
      final IbftBlockHeightManagerFactory ibftBlockHeightManagerFactory,
      final Map<Long, List<MessageData>> futureMessages) {
    this.blockchain = blockchain;
    this.ibftFinalState = ibftFinalState;
    this.ibftBlockHeightManagerFactory = ibftBlockHeightManagerFactory;
    this.futureMessages = futureMessages;
  }

  public void start() {
    startNewHeightManager(blockchain.getChainHeadHeader());
  }

  public void handleMessageEvent(final IbftReceivedMessageEvent msg) {
    handleMessage(msg.getMessageData());
  }

  private void handleMessage(final MessageData messageData) {
    switch (messageData.getCode()) {
      case IbftV2.PROPOSAL:
        final SignedData<ProposalPayload> proposalMsg =
            ProposalMessage.fromMessage(messageData).decode();
        if (processMessage(proposalMsg, messageData)) {
          currentHeightManager.handleProposalMessage(proposalMsg);
        }
        break;

      case IbftV2.PREPARE:
        final SignedData<PreparePayload> prepareMsg =
            PrepareMessage.fromMessage(messageData).decode();
        if (processMessage(prepareMsg, messageData)) {
          currentHeightManager.handlePrepareMessage(prepareMsg);
        }
        break;

      case IbftV2.COMMIT:
        final SignedData<CommitPayload> commitMsg = CommitMessage.fromMessage(messageData).decode();
        if (processMessage(commitMsg, messageData)) {
          currentHeightManager.handleCommitMessage(commitMsg);
        }
        break;

      case IbftV2.ROUND_CHANGE:
        final SignedData<RoundChangePayload> roundChangeMsg =
            RoundChangeMessage.fromMessage(messageData).decode();
        if (processMessage(roundChangeMsg, messageData)) {
          currentHeightManager.handleRoundChangeMessage(roundChangeMsg);
        }
        break;

      case IbftV2.NEW_ROUND:
        final SignedData<NewRoundPayload> newRoundMsg =
            NewRoundMessage.fromMessage(messageData).decode();
        if (processMessage(newRoundMsg, messageData)) {
          currentHeightManager.handleNewRoundMessage(newRoundMsg);
        }
        break;

      default:
        throw new IllegalArgumentException(
            "Received message does not conform to any recognised IBFT message structure.");
    }
  }

  public void handleNewBlockEvent(final NewChainHead newChainHead) {
    startNewHeightManager(newChainHead.getNewChainHeadHeader());
  }

  public void handleBlockTimerExpiry(final BlockTimerExpiry blockTimerExpiry) {
    if (isMsgForCurrentHeight(blockTimerExpiry.getRoundIndentifier())) {
      currentHeightManager.handleBlockTimerExpiry(blockTimerExpiry.getRoundIndentifier());
    } else {
      LOG.info("Block timer event discarded as it is not for current block height");
    }
  }

  public void handleRoundExpiry(final RoundExpiry roundExpiry) {
    if (isMsgForCurrentHeight(roundExpiry.getView())) {
      currentHeightManager.roundExpired(roundExpiry);
    } else {
      LOG.info("Round expiry event discarded as it is not for current block height");
    }
  }

  private void startNewHeightManager(final BlockHeader parentHeader) {
    currentHeightManager = ibftBlockHeightManagerFactory.create(parentHeader);
    currentHeightManager.start();
    final long newChainHeight = currentHeightManager.getChainHeight();
    List<MessageData> orDefault = futureMessages.getOrDefault(newChainHeight, emptyList());
    orDefault.forEach(this::handleMessage);
    futureMessages.remove(newChainHeight);
  }

  private boolean processMessage(
      final SignedData<? extends Payload> msg, final MessageData rawMsg) {
    final ConsensusRoundIdentifier msgRoundIdentifier = msg.getPayload().getRoundIdentifier();
    if (isMsgForCurrentHeight(msgRoundIdentifier)) {
      return isMsgFromKnownValidator(msg);
    } else if (isMsgForFutureChainHeight(msgRoundIdentifier)) {
      addMessageToFutureMessageBuffer(msgRoundIdentifier.getSequenceNumber(), rawMsg);
    } else {
      LOG.info("IBFT message discarded as it is not for the current block height");
    }
    return false;
  }

  private boolean isMsgFromKnownValidator(final SignedData<? extends Payload> msg) {
    return ibftFinalState.getValidators().contains(msg.getSender());
  }

  private boolean isMsgForCurrentHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() == currentHeightManager.getChainHeight();
  }

  private boolean isMsgForFutureChainHeight(final ConsensusRoundIdentifier roundIdentifier) {
    return roundIdentifier.getSequenceNumber() > currentHeightManager.getChainHeight();
  }

  private void addMessageToFutureMessageBuffer(final long chainHeight, final MessageData rawMsg) {
    if (!futureMessages.containsKey(chainHeight)) {
      futureMessages.put(chainHeight, Lists.newArrayList());
    }
    futureMessages.get(chainHeight).add(rawMsg);
  }
}
