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
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.consensus.ibft.validation.SignedDataValidator;
import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

public class IbftRoundFactory {
  private final IbftFinalState finalState;
  private final IbftBlockCreatorFactory blockCreatorFactory;
  private final ProtocolContext<IbftContext> protocolContext;
  private final ProtocolSchedule<IbftContext> protocolSchedule;
  private final Subscribers<MinedBlockObserver> minedBlockObservers;

  public IbftRoundFactory(
      final IbftFinalState finalState,
      final ProtocolContext<IbftContext> protocolContext,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final Subscribers<MinedBlockObserver> minedBlockObservers) {
    this.finalState = finalState;
    this.blockCreatorFactory = finalState.getBlockCreatorFactory();
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.minedBlockObservers = minedBlockObservers;
  }

  public IbftRound createNewRound(final BlockHeader parentHeader, final int round) {
    long nextBlockHeight = parentHeader.getNumber() + 1;
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(nextBlockHeight, round);

    BlockValidator<IbftContext> blockValidator =
        protocolSchedule.getByBlockNumber(nextBlockHeight).getBlockValidator();

    final RoundState roundState =
        new RoundState(
            roundIdentifier,
            finalState.getQuorum(),
            new MessageValidator(
                new SignedDataValidator(
                    finalState.getValidators(),
                    finalState.getProposerForRound(roundIdentifier),
                    roundIdentifier,
                    blockValidator,
                    protocolContext)));

    return createNewRoundWithState(parentHeader, roundState);
  }

  public IbftRound createNewRoundWithState(
      final BlockHeader parentHeader, final RoundState roundState) {
    final ConsensusRoundIdentifier roundIdentifier = roundState.getRoundIdentifier();
    final IbftBlockCreator blockCreator =
        blockCreatorFactory.create(parentHeader, roundIdentifier.getRoundNumber());

    return new IbftRound(
        roundState,
        blockCreator,
        protocolContext,
        protocolSchedule.getByBlockNumber(roundIdentifier.getSequenceNumber()).getBlockImporter(),
        minedBlockObservers,
        finalState.getNodeKeys(),
        finalState.getMessageFactory(),
        finalState.getTransmitter());
  }
}
