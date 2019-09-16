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
package org.hyperledger.besu.consensus.ibft.statemachine;

import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.ibft.BlockTimer;
import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.IbftHelpers;
import org.hyperledger.besu.consensus.ibft.RoundTimer;
import org.hyperledger.besu.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.ibft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.ibft.network.IbftMessageTransmitter;
import org.hyperledger.besu.consensus.ibft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;

import java.time.Clock;
import java.util.Collection;

/** This is the full data set, or context, required for many of the aspects of the IBFT workflow. */
public class IbftFinalState {
  private final VoteTallyCache voteTallyCache;
  private final KeyPair nodeKeys;
  private final Address localAddress;
  private final ProposerSelector proposerSelector;
  private final RoundTimer roundTimer;
  private final BlockTimer blockTimer;
  private final IbftBlockCreatorFactory blockCreatorFactory;
  private final MessageFactory messageFactory;
  private final IbftMessageTransmitter messageTransmitter;
  private final Clock clock;

  public IbftFinalState(
      final VoteTallyCache voteTallyCache,
      final KeyPair nodeKeys,
      final Address localAddress,
      final ProposerSelector proposerSelector,
      final ValidatorMulticaster validatorMulticaster,
      final RoundTimer roundTimer,
      final BlockTimer blockTimer,
      final IbftBlockCreatorFactory blockCreatorFactory,
      final MessageFactory messageFactory,
      final Clock clock) {
    this.voteTallyCache = voteTallyCache;
    this.nodeKeys = nodeKeys;
    this.localAddress = localAddress;
    this.proposerSelector = proposerSelector;
    this.roundTimer = roundTimer;
    this.blockTimer = blockTimer;
    this.blockCreatorFactory = blockCreatorFactory;
    this.messageFactory = messageFactory;
    this.clock = clock;
    this.messageTransmitter = new IbftMessageTransmitter(messageFactory, validatorMulticaster);
  }

  public int getQuorum() {
    return IbftHelpers.calculateRequiredValidatorQuorum(getValidators().size());
  }

  public Collection<Address> getValidators() {
    return voteTallyCache.getVoteTallyAtHead().getValidators();
  }

  public KeyPair getNodeKeys() {
    return nodeKeys;
  }

  public Address getLocalAddress() {
    return localAddress;
  }

  public boolean isLocalNodeProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    return getProposerForRound(roundIdentifier).equals(localAddress);
  }

  public boolean isLocalNodeValidator() {
    return getValidators().contains(localAddress);
  }

  public RoundTimer getRoundTimer() {
    return roundTimer;
  }

  public BlockTimer getBlockTimer() {
    return blockTimer;
  }

  public IbftBlockCreatorFactory getBlockCreatorFactory() {
    return blockCreatorFactory;
  }

  public MessageFactory getMessageFactory() {
    return messageFactory;
  }

  public Address getProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    return proposerSelector.selectProposerForRound(roundIdentifier);
  }

  public IbftMessageTransmitter getTransmitter() {
    return messageTransmitter;
  }

  public Clock getClock() {
    return clock;
  }
}
