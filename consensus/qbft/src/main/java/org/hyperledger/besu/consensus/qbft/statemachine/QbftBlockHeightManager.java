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
package org.hyperledger.besu.consensus.qbft.statemachine;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.validation.FutureRoundProposalMessageValidator;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidatorFactory;
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
public class QbftBlockHeightManager implements BaseQbftBlockHeightManager {

  private static final Logger LOG = LoggerFactory.getLogger(QbftBlockHeightManager.class);

  private final QbftRoundFactory roundFactory;
  private final RoundChangeManager roundChangeManager;
  private final BlockHeader parentHeader;
  private final QbftMessageTransmitter transmitter;
  private final MessageFactory messageFactory;
  private final Map<Integer, RoundState> futureRoundStateBuffer = Maps.newHashMap();
  private final FutureRoundProposalMessageValidator futureRoundProposalMessageValidator;
  private final Clock clock;
  private final Function<ConsensusRoundIdentifier, RoundState> roundStateCreator;
  private final BftFinalState finalState;

  private Optional<PreparedCertificate> latestPreparedCertificate = Optional.empty();
  private Optional<QbftRound> currentRound = Optional.empty();

  public QbftBlockHeightManager(
      final BlockHeader parentHeader,
      final BftFinalState finalState,
      final RoundChangeManager roundChangeManager,
      final QbftRoundFactory qbftRoundFactory,
      final Clock clock,
      final MessageValidatorFactory messageValidatorFactory,
      final MessageFactory messageFactory) {
    this.parentHeader = parentHeader;
    this.roundFactory = qbftRoundFactory;
    this.transmitter =
        new QbftMessageTransmitter(messageFactory, finalState.getValidatorMulticaster());
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

    final long nextBlockHeight = parentHeader.getNumber() + 1;
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(nextBlockHeight, 0);

    finalState.getBlockTimer().startTimer(roundIdentifier, parentHeader);
  }

  @Override
  public void handleBlockTimerExpiry(final ConsensusRoundIdentifier roundIdentifier) {
    if (currentRound.isPresent()) {
      // It is possible for the block timer to take longer than it should due to the precision of
      // the timer in Java and the OS. This means occasionally the proposal can arrive before the
      // block timer expiry and hence the round has already been set. There is no negative impact
      // on the protocol in this case.
      return;
    }

    startNewRound(0);

    final QbftRound qbftRound = currentRound.get();
    // mining will be checked against round 0 as the current round is initialised to 0 above
    final boolean isProposer =
        finalState.isLocalNodeProposerForRound(qbftRound.getRoundIdentifier());

    if (isProposer) {
      if (roundIdentifier.equals(qbftRound.getRoundIdentifier())) {
        final long headerTimeStampSeconds = Math.round(clock.millis() / 1000D);
        qbftRound.createAndSendProposalMessage(headerTimeStampSeconds);
      } else {
        LOG.trace(
            "Block timer expired for a round ({}) other than current ({})",
            roundIdentifier,
            qbftRound.getRoundIdentifier());
      }
    }
  }

  @Override
  public void roundExpired(final RoundExpiry expire) {
    if (currentRound.isEmpty()) {
      LOG.error(
          "Received Round timer expiry before round is created timerRound={}", expire.getView());
      return;
    }

    QbftRound qbftRound = currentRound.get();
    if (!expire.getView().equals(qbftRound.getRoundIdentifier())) {
      LOG.trace(
          "Ignoring Round timer expired which does not match current round. round={}, timerRound={}",
          qbftRound.getRoundIdentifier(),
          expire.getView());
      return;
    }

    LOG.debug(
        "Round has expired, creating PreparedCertificate and notifying peers. round={}",
        qbftRound.getRoundIdentifier());
    final Optional<PreparedCertificate> preparedCertificate =
        qbftRound.constructPreparedCertificate();

    if (preparedCertificate.isPresent()) {
      latestPreparedCertificate = preparedCertificate;
    }

    startNewRound(qbftRound.getRoundIdentifier().getRoundNumber() + 1);
    qbftRound = currentRound.get();

    try {
      final RoundChange localRoundChange =
          messageFactory.createRoundChange(
              qbftRound.getRoundIdentifier(), latestPreparedCertificate);

      // Its possible the locally created RoundChange triggers the transmission of a NewRound
      // message - so it must be handled accordingly.
      handleRoundChangePayload(localRoundChange);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to create signed RoundChange message.", e);
    }

    transmitter.multicastRoundChange(qbftRound.getRoundIdentifier(), latestPreparedCertificate);
  }

  @Override
  public void handleProposalPayload(final Proposal proposal) {
    LOG.trace("Received a Proposal Payload.");
    final MessageAge messageAge =
        determineAgeOfPayload(proposal.getRoundIdentifier().getRoundNumber());

    if (messageAge == MessageAge.PRIOR_ROUND) {
      LOG.trace("Received Proposal Payload for a prior round={}", proposal.getRoundIdentifier());
    } else {
      if (messageAge == MessageAge.FUTURE_ROUND) {
        if (!futureRoundProposalMessageValidator.validateProposalMessage(proposal)) {
          LOG.info("Received future Proposal which is illegal, no round change triggered.");
          return;
        }
        startNewRound(proposal.getRoundIdentifier().getRoundNumber());
      }
      currentRound.ifPresent(r -> r.handleProposalMessage(proposal));
    }
  }

  @Override
  public void handlePreparePayload(final Prepare prepare) {
    LOG.trace("Received a Prepare Payload.");
    actionOrBufferMessage(
        prepare,
        currentRound.isPresent() ? currentRound.get()::handlePrepareMessage : (ignore) -> {},
        RoundState::addPrepareMessage);
  }

  @Override
  public void handleCommitPayload(final Commit commit) {
    LOG.trace("Received a Commit Payload.");
    actionOrBufferMessage(
        commit,
        currentRound.isPresent() ? currentRound.get()::handleCommitMessage : (ignore) -> {},
        RoundState::addCommitMessage);
  }

  private <P extends Payload, M extends BftMessage<P>> void actionOrBufferMessage(
      final M qbftMessage,
      final Consumer<M> inRoundHandler,
      final BiConsumer<RoundState, M> buffer) {
    final MessageAge messageAge =
        determineAgeOfPayload(qbftMessage.getRoundIdentifier().getRoundNumber());
    if (messageAge == MessageAge.CURRENT_ROUND) {
      inRoundHandler.accept(qbftMessage);
    } else if (messageAge == MessageAge.FUTURE_ROUND) {
      final ConsensusRoundIdentifier msgRoundId = qbftMessage.getRoundIdentifier();
      final RoundState roundstate =
          futureRoundStateBuffer.computeIfAbsent(
              msgRoundId.getRoundNumber(), k -> roundStateCreator.apply(msgRoundId));
      buffer.accept(roundstate, qbftMessage);
    }
  }

  @Override
  public void handleRoundChangePayload(final RoundChange message) {
    final ConsensusRoundIdentifier targetRound = message.getRoundIdentifier();
    LOG.trace("Received a RoundChange Payload for {}", targetRound);

    final MessageAge messageAge =
        determineAgeOfPayload(message.getRoundIdentifier().getRoundNumber());
    if (messageAge == MessageAge.PRIOR_ROUND) {
      LOG.trace("Received RoundChange Payload for a prior round. targetRound={}", targetRound);
      return;
    }

    final Optional<Collection<RoundChange>> result =
        roundChangeManager.appendRoundChangeMessage(message);
    if (result.isPresent()) {
      LOG.debug(
          "Received sufficient RoundChange messages to change round to targetRound={}",
          targetRound);
      if (messageAge == MessageAge.FUTURE_ROUND) {
        startNewRound(targetRound.getRoundNumber());
      }

      final RoundChangeArtifacts roundChangeMetadata = RoundChangeArtifacts.create(result.get());

      if (finalState.isLocalNodeProposerForRound(targetRound)) {
        if (currentRound.isEmpty()) {
          startNewRound(0);
        }
        currentRound
            .get()
            .startRoundWith(roundChangeMetadata, TimeUnit.MILLISECONDS.toSeconds(clock.millis()));
      }
    }
  }

  private void startNewRound(final int roundNumber) {
    LOG.debug("Starting new round {}", roundNumber);
    // validate the current round
    if (futureRoundStateBuffer.containsKey(roundNumber)) {
      currentRound =
          Optional.of(
              roundFactory.createNewRoundWithState(
                  parentHeader, futureRoundStateBuffer.get(roundNumber)));
      futureRoundStateBuffer.keySet().removeIf(k -> k <= roundNumber);
    } else {
      currentRound = Optional.of(roundFactory.createNewRound(parentHeader, roundNumber));
    }
    // discard roundChange messages from the current and previous rounds
    roundChangeManager.discardRoundsPriorTo(currentRound.get().getRoundIdentifier());
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
    final int currentRoundNumber =
        currentRound.map(r -> r.getRoundIdentifier().getRoundNumber()).orElse(-1);
    if (messageRoundNumber > currentRoundNumber) {
      return MessageAge.FUTURE_ROUND;
    } else if (messageRoundNumber == currentRoundNumber) {
      return MessageAge.CURRENT_ROUND;
    }
    return MessageAge.PRIOR_ROUND;
  }

  public enum MessageAge {
    PRIOR_ROUND,
    CURRENT_ROUND,
    FUTURE_ROUND
  }
}
