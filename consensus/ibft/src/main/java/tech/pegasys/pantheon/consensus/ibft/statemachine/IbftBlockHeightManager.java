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

import static tech.pegasys.pantheon.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.CURRENT_ROUND;
import static tech.pegasys.pantheon.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.FUTURE_ROUND;
import static tech.pegasys.pantheon.consensus.ibft.statemachine.IbftBlockHeightManager.MessageAge.PRIOR_ROUND;

import tech.pegasys.pantheon.consensus.ibft.BlockTimer;
import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.IbftMessage;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMessageTransmitter;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.Payload;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidatorFactory;
import tech.pegasys.pantheon.consensus.ibft.validation.NewRoundMessageValidator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for starting/clearing Consensus rounds at a given block height. One of these is
 * created when a new block is imported to the chain. It immediately then creates a Round-0 object,
 * and sends a Proposal message. If the round times out prior to importing a block, this class is
 * responsible for creating a RoundChange message and transmitting it.
 */
public class IbftBlockHeightManager implements BlockHeightManager {

  private static final Logger LOG = LogManager.getLogger();

  private final IbftRoundFactory roundFactory;
  private final RoundChangeManager roundChangeManager;
  private final BlockHeader parentHeader;
  private final RoundTimer roundTimer;
  private final BlockTimer blockTimer;
  private final IbftMessageTransmitter transmitter;
  private final MessageFactory messageFactory;
  private final Map<Integer, RoundState> futureRoundStateBuffer = Maps.newHashMap();
  private final NewRoundMessageValidator newRoundMessageValidator;
  private final Clock clock;
  private final Function<ConsensusRoundIdentifier, RoundState> roundStateCreator;
  private final IbftFinalState finalState;

  private Optional<PreparedRoundArtifacts> latestPreparedRoundArtifacts = Optional.empty();

  private IbftRound currentRound;

  public IbftBlockHeightManager(
      final BlockHeader parentHeader,
      final IbftFinalState finalState,
      final RoundChangeManager roundChangeManager,
      final IbftRoundFactory ibftRoundFactory,
      final Clock clock,
      final MessageValidatorFactory messageValidatorFactory) {
    this.parentHeader = parentHeader;
    this.roundFactory = ibftRoundFactory;
    this.roundTimer = finalState.getRoundTimer();
    this.blockTimer = finalState.getBlockTimer();
    this.transmitter = finalState.getTransmitter();
    this.messageFactory = finalState.getMessageFactory();
    this.clock = clock;
    this.roundChangeManager = roundChangeManager;
    this.finalState = finalState;

    newRoundMessageValidator = messageValidatorFactory.createNewRoundValidator(parentHeader);

    roundStateCreator =
        (roundIdentifier) ->
            new RoundState(
                roundIdentifier,
                finalState.getQuorum(),
                messageValidatorFactory.createMessageValidator(roundIdentifier));
  }

  @Override
  public void start() {
    startNewRound(0);
    if (finalState.isLocalNodeProposerForRound(currentRound.getRoundIdentifier())) {
      blockTimer.startTimer(currentRound.getRoundIdentifier(), parentHeader);
    }
  }

  @Override
  public void handleBlockTimerExpiry(final ConsensusRoundIdentifier roundIdentifier) {
    if (roundIdentifier.equals(currentRound.getRoundIdentifier())) {
      currentRound.createAndSendProposalMessage(clock.millis() / 1000);
    } else {
      LOG.info(
          "Block timer expired for a round ({}) other than current ({})",
          roundIdentifier,
          currentRound.getRoundIdentifier());
    }
  }

  @Override
  public void roundExpired(final RoundExpiry expire) {
    if (!expire.getView().equals(currentRound.getRoundIdentifier())) {
      LOG.info(
          "Ignoring Round timer expired which does not match current round. round={}, timerRound={}",
          currentRound.getRoundIdentifier(),
          expire.getView());
      return;
    }

    LOG.info(
        "Round has expired, creating PreparedCertificate and notifying peers. round={}",
        currentRound.getRoundIdentifier());
    final Optional<PreparedRoundArtifacts> preparedRoundArtifacts =
        currentRound.constructPreparedRoundArtifacts();

    if (preparedRoundArtifacts.isPresent()) {
      latestPreparedRoundArtifacts = preparedRoundArtifacts;
    }

    startNewRound(currentRound.getRoundIdentifier().getRoundNumber() + 1);

    final RoundChange localRoundChange =
        messageFactory.createRoundChange(
            currentRound.getRoundIdentifier(), latestPreparedRoundArtifacts);
    transmitter.multicastRoundChange(
        currentRound.getRoundIdentifier(), latestPreparedRoundArtifacts);

    // Its possible the locally created RoundChange triggers the transmission of a NewRound
    // message - so it must be handled accordingly.
    handleRoundChangePayload(localRoundChange);
  }

  @Override
  public void handleProposalPayload(final Proposal proposal) {
    LOG.debug("Received a Proposal Payload.");
    actionOrBufferMessage(
        proposal, currentRound::handleProposalMessage, RoundState::setProposedBlock);
  }

  @Override
  public void handlePreparePayload(final Prepare prepare) {
    LOG.debug("Received a Prepare Payload.");
    actionOrBufferMessage(
        prepare, currentRound::handlePrepareMessage, RoundState::addPrepareMessage);
  }

  @Override
  public void handleCommitPayload(final Commit commit) {
    LOG.debug("Received a Commit Payload.");
    actionOrBufferMessage(commit, currentRound::handleCommitMessage, RoundState::addCommitMessage);
  }

  private <P extends Payload, M extends IbftMessage<P>> void actionOrBufferMessage(
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
    LOG.info("Received a RoundChange Payload for {}", targetRound.toString());

    final MessageAge messageAge =
        determineAgeOfPayload(message.getRoundIdentifier().getRoundNumber());
    if (messageAge == PRIOR_ROUND) {
      LOG.debug("Received RoundChange Payload for a prior round. targetRound={}", targetRound);
      return;
    }

    final Optional<Collection<RoundChange>> result =
        roundChangeManager.appendRoundChangeMessage(message);
    if (result.isPresent()) {
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
    LOG.info("Starting new round {}", roundNumber);
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
    roundTimer.startTimer(currentRound.getRoundIdentifier());
  }

  @Override
  public void handleNewRoundPayload(final NewRound newRound) {
    // final NewRoundPayload payload = newRound.getSignedPayload().getPayload();
    final MessageAge messageAge =
        determineAgeOfPayload(newRound.getRoundIdentifier().getRoundNumber());

    if (messageAge == PRIOR_ROUND) {
      LOG.info("Received NewRound Payload for a prior round={}", newRound.getRoundIdentifier());
      return;
    }
    LOG.info("Received NewRound Payload for {}", newRound.getRoundIdentifier());

    if (newRoundMessageValidator.validateNewRoundMessage(newRound)) {
      if (messageAge == FUTURE_ROUND) {
        startNewRound(newRound.getRoundIdentifier().getRoundNumber());
      }
      currentRound.handleProposalFromNewRound(newRound);
    }
  }

  @Override
  public long getChainHeight() {
    return currentRound.getRoundIdentifier().getSequenceNumber();
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
