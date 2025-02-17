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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCreator;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftExtraDataProvider;
import org.hyperledger.besu.consensus.qbft.core.types.QbftFinalState;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMinedBlockObserver;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidatorFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.util.Subscribers;

/** The Qbft round factory. */
public class QbftRoundFactory {

  private final QbftFinalState finalState;
  private final QbftBlockCreatorFactory blockCreatorFactory;
  private final ProtocolContext protocolContext;
  private final QbftProtocolSchedule protocolSchedule;
  private final Subscribers<QbftMinedBlockObserver> minedBlockObservers;
  private final MessageValidatorFactory messageValidatorFactory;
  private final MessageFactory messageFactory;
  private final BftExtraDataCodec bftExtraDataCodec;
  private final QbftExtraDataProvider qbftExtraDataProvider;

  /**
   * Instantiates a new Qbft round factory.
   *
   * @param finalState the final state
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param minedBlockObservers the mined block observers
   * @param messageValidatorFactory the message validator factory
   * @param messageFactory the message factory
   * @param bftExtraDataCodec the bft extra data codec
   * @param qbftExtraDataProvider the bft extra data codec
   */
  public QbftRoundFactory(
      final QbftFinalState finalState,
      final ProtocolContext protocolContext,
      final QbftProtocolSchedule protocolSchedule,
      final Subscribers<QbftMinedBlockObserver> minedBlockObservers,
      final MessageValidatorFactory messageValidatorFactory,
      final MessageFactory messageFactory,
      final BftExtraDataCodec bftExtraDataCodec,
      final QbftExtraDataProvider qbftExtraDataProvider) {
    this.finalState = finalState;
    this.blockCreatorFactory = finalState.getBlockCreatorFactory();
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.minedBlockObservers = minedBlockObservers;
    this.messageValidatorFactory = messageValidatorFactory;
    this.messageFactory = messageFactory;
    this.bftExtraDataCodec = bftExtraDataCodec;
    this.qbftExtraDataProvider = qbftExtraDataProvider;
  }

  /**
   * Create new round qbft round.
   *
   * @param parentHeader the parent header
   * @param round the round
   * @return the qbft round
   */
  public QbftRound createNewRound(final QbftBlockHeader parentHeader, final int round) {
    long nextBlockHeight = parentHeader.getNumber() + 1;
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(nextBlockHeight, round);

    final RoundState roundState =
        new RoundState(
            roundIdentifier,
            finalState.getQuorum(),
            messageValidatorFactory.createMessageValidator(roundIdentifier, parentHeader));

    return createNewRoundWithState(parentHeader, roundState);
  }

  /**
   * Create new Qbft round with state.
   *
   * @param parentHeader the parent header
   * @param roundState the round state
   * @return the qbft round
   */
  public QbftRound createNewRoundWithState(
      final QbftBlockHeader parentHeader, final RoundState roundState) {
    final QbftBlockCreator blockCreator =
        blockCreatorFactory.create(roundState.getRoundIdentifier().getRoundNumber());

    // TODO(tmm): Why is this created everytime?!
    final QbftMessageTransmitter messageTransmitter =
        new QbftMessageTransmitter(messageFactory, finalState.getValidatorMulticaster());

    return new QbftRound(
        roundState,
        blockCreator,
        protocolContext,
        protocolSchedule,
        minedBlockObservers,
        finalState.getNodeKey(),
        messageFactory,
        messageTransmitter,
        finalState.getRoundTimer(),
        bftExtraDataCodec,
        qbftExtraDataProvider,
        parentHeader);
  }
}
