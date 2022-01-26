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

import static org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.CURRENT_ROUND;
import static org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.FUTURE_ROUND;
import static org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.PRIOR_ROUND;

import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.ibft.network.IbftMessageTransmitter;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.validation.FutureRoundProposalMessageValidator;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidatorFactory;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for starting/clearing Consensus rounds at a given block height. One of these is
 * created when a new block is imported to the chain. It immediately then creates a Round-0 object,
 * and sends a Proposal message. If the round times out prior to importing a block, this class is
 * responsible for creating a RoundChange message and transmitting it.
 */
public class IbftBlockHeightManager implements BaseIbftBlockHeightManager {

  private static final Logger LOG = LoggerFactory.getLogger(IbftBlockHeightManager.class);

  private final IbftRoundFactory roundFactory;
  private final RoundChangeManager roundChangeManager;
  private final BlockHeader parentHeader;
  private final BlockTimer blockTimer;
  private final IbftMessageTransmitter transmitter;
  private final MessageFactory messageFactory;
  private final Map<Integer, RoundState> futureRoundStateBuffer = Maps.newHashMap();
  private final FutureRoundProposalMessageValidator futureRoundProposalMessageValidator;
  private final Clock clock;
  private final Function<ConsensusRoundIdentifier, RoundState> roundStateCreator;
  private final BftFinalState finalState;

  private Optional<PreparedRoundArtifacts> latestPreparedRoundArtifacts = Optional.empty();

  private IbftRound currentRound;

  public IbftBlockHeightManager(
      final BlockHeader parentHeader,
      final BftFinalState finalState,
      final RoundChangeManager roundChangeManager,
      final IbftRoundFactory ibftRoundFactory,
      final Clock clock,
      final MessageValidatorFactory messageValidatorFactory,
      final MessageFactory messageFactory) {
    this.parentHeader = parentHeader;
    this.roundFactory = ibftRoundFactory;
    this.blockTimer = finalState.getBlockTimer();
    this.transmitter =
        new IbftMessageTransmitter(messageFactory, finalState.getValidatorMulticaster());
    this.messageFactory = messageFactory;
    this.clock = clock;
    this.roundChangeManager = roundChangeManager;
    this.finalState = finalState;

    futureRoundProposalMessageValidator =
        messageValidatorFactory.createFutureRoundProposalMessageValidator(
            getChainHeight(), parentHeader);

    roundStateCreator =
        (roundIdentifier) ->
            new RoundState(
                roundIdentifier,
                finalState.getQuorum(),
                messageValidatorFactory.createMessageValidator(roundIdentifier, parentHeader));

    currentRound = roundFactory.createNewRound(parentHeader, 0);
    if (finalState.isLocalNodeProposerForRound(currentRound.getRoundIdentifier())) {
      blockTimer.startTimer(currentRound.getRoundIdentifier(), parentHeader);
    }
  }

  @Override
  public void handleBlockTimerExpiry(final ConsensusRoundIdentifier roundIdentifier) {
    if (roundIdentifier.equals(currentRound.getRoundIdentifier())) {
      final long headerTimeStampSeconds = Math.round(clock.millis() / 1000D);
      currentRound.createAndSendProposalMessage(headerTimeStampSeconds);
    } else {
      LOG.trace(
          "Block timer expired for a round ({}) other than current ({})",
          roundIdentifier,
          currentRound.getRoundIdentifier());
    }
  }

  @Override
  public void roundExpired(final RoundExpiry expire) {
    if (!expire.getView().equals(currentRound.getRoundIdentifier())) {
      LOG.trace(
          "Ignoring Round timer expired which does not match current round. round={}, timerRound={}",
          currentRound.getRoundIdentifier(),
          expire.getView());
      return;
    }

    LOG.debug(
        "Round has expired, creating PreparedCertificate and notifying peers. round={}",
        currentRound.getRoundIdentifier());
    final Optional<PreparedRoundArtifacts> preparedRoundArtifacts =
        currentRound.constructPreparedRoundArtifacts();

    if (preparedRoundArtifacts.isPresent()) {
      latestPreparedRoundArtifacts = preparedRoundArtifacts;
    }

    startNewRound(currentRound.getRoundIdentifier().getRoundNumber() + 1);

    try {
      final RoundChange localRoundChange =
          messageFactory.createRoundChange(
              currentRound.getRoundIdentifier(), latestPreparedRoundArtifacts);

      // Its possible the locally created RoundChange triggers the transmission of a NewRound
      // message - so it must be handled accordingly.
      handleRoundChangePayload(localRoundChange);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to create signed RoundChange message.", e);
    }

    transmitter.multicastRoundChange(
        currentRound.getRoundIdentifier(), latestPreparedRoundArtifacts);
  }

  @Override
  public void handleProposalPayload(final Proposal proposal) {
    LOG.trace("Received a Proposal Payload.");
    final MessageAge messageAge =
        determineAgeOfPayload(proposal.getRoundIdentifier().getRoundNumber());

    if (messageAge == PRIOR_ROUND) {
      LOG.trace("Received Proposal Payload for a prior round={}", proposal.getRoundIdentifier());
    } else {
      if (messageAge == FUTURE_ROUND) {
        if (!futureRoundProposalMessageValidator.validateProposalMessage(proposal)) {
          LOG.info("Received future Proposal which is illegal, no round change triggered.");
          return;
        }
        startNewRound(proposal.getRoundIdentifier().getRoundNumber());
      }
      currentRound.handleProposalMessage(proposal);
    }
  }

  @Override
  public void handlePreparePayload(final Prepare prepare) {
    LOG.trace("Received a Prepare Payload.");
    actionOrBufferMessage(
        prepare, currentRound::handlePrepareMessage, RoundState::addPrepareMessage);
  }

  @Override
  public void handleCommitPayload(final Commit commit) {
    LOG.trace("Received a Commit Payload.");
    actionOrBufferMessage(commit, currentRound::handleCommitMessage, RoundState::addCommitMessage);
  }

  private <P extends Payload, M extends BftMessage<P>> void actionOrBufferMessage(
      final M ibftMessage,
      final Consumer<M> inRoundHandler,
      final BiConsumer<RoundState, M> buffer) {
    final MessageAge messageAge =
        determineAgeOfPayload(ibftMessage.getRoundIdentifier().getRoundNumber());
    if (messageAge == CURRENT_ROUND) {
      inRoundHandler.accept(ibftMessage);
    } else if (messageAge == FUTURE_ROUND) {
      final ConsensusRoundIdentifier msgRoundId = ibftMessage.getRoundIdentifier();
      final RoundState roundstate =
          futureRoundStateBuffer.computeIfAbsent(
              msgRoundId.getRoundNumber(), k -> roundStateCreator.apply(msgRoundId));
      buffer.accept(roundstate, ibftMessage);
    }
  }

  @Override
  public void handleRoundChangePayload(final RoundChange message) {
    final ConsensusRoundIdentifier targetRound = message.getRoundIdentifier();
    LOG.trace("Received a RoundChange Payload for {}", targetRound);

    final MessageAge messageAge =
        determineAgeOfPayload(message.getRoundIdentifier().getRoundNumber());
    if (messageAge == PRIOR_ROUND) {
      LOG.trace("Received RoundChange Payload for a prior round. targetRound={}", targetRound);
      return;
    }

    final Optional<Collection<RoundChange>> result =
        roundChangeManager.appendRoundChangeMessage(message);
    if (result.isPresent()) {
      LOG.debug(
          "Received sufficient RoundChange messages to change round to targetRound={}",
          targetRound);
      if (messageAge == FUTURE_ROUND) {
        startNewRound(targetRound.getRoundNumber());
      }

      final RoundChangeArtifacts roundChangeArtifacts = RoundChangeArtifacts.create(result.get());

      if (finalState.isLocalNodeProposerForRound(targetRound)) {
        currentRound.startRoundWith(
            roundChangeArtifacts, TimeUnit.MILLISECONDS.toSeconds(clock.millis()));
      }
    }
  }

  private void startNewRound(final int roundNumber) {
    LOG.debug("Starting new round {}", roundNumber);
    if (futureRoundStateBuffer.containsKey(roundNumber)) {
      currentRound =
          roundFactory.createNewRoundWithState(
              parentHeader, futureRoundStateBuffer.get(roundNumber));
      futureRoundStateBuffer.keySet().removeIf(k -> k <= roundNumber);
    } else {
      currentRound = roundFactory.createNewRound(parentHeader, roundNumber);
    }
    // discard roundChange messages from the current and previous rounds
    roundChangeManager.discardRoundsPriorTo(currentRound.getRoundIdentifier());
  }

  @Override
  public long getChainHeight() {
    return parentHeader.getNumber() + 1;
  }

  @Override
  public BlockHeader getParentBlockHeader() {
    return parentHeader;
  }

  private MessageAge determineAgeOfPayload(final int messageRoundNumber) {
    final int currentRoundNumber = currentRound.getRoundIdentifier().getRoundNumber();
    if (messageRoundNumber > currentRoundNumber) {
      return FUTURE_ROUND;
    } else if (messageRoundNumber == currentRoundNumber) {
      return CURRENT_ROUND;
    }
    return PRIOR_ROUND;
  }

  public enum MessageAge {
    PRIOR_ROUND,
    CURRENT_ROUND,
    FUTURE_ROUND
  }
}
