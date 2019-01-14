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

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.calculateRequiredValidatorQuorum;

import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.consensus.ibft.BlockTimer;
import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMulticaster;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;

import java.time.Clock;
import java.util.Collection;

/** This is the full data set, or context, required for many of the aspects of the IBFT workflow. */
public class IbftFinalState {
  private final ValidatorProvider validatorProvider;
  private final KeyPair nodeKeys;
  private final Address localAddress;
  private final ProposerSelector proposerSelector;
  private final IbftMulticaster peers;
  private final RoundTimer roundTimer;
  private final BlockTimer blockTimer;
  private final IbftBlockCreatorFactory blockCreatorFactory;
  private final MessageFactory messageFactory;
  private final BlockHeaderValidator<IbftContext> ibftContextBlockHeaderValidator;
  private final IbftMessageTransmitter messageTransmitter;
  private final Clock clock;

  public IbftFinalState(
      final ValidatorProvider validatorProvider,
      final KeyPair nodeKeys,
      final Address localAddress,
      final ProposerSelector proposerSelector,
      final IbftMulticaster peers,
      final RoundTimer roundTimer,
      final BlockTimer blockTimer,
      final IbftBlockCreatorFactory blockCreatorFactory,
      final MessageFactory messageFactory,
      final BlockHeaderValidator<IbftContext> ibftContextBlockHeaderValidator,
      final Clock clock) {
    this.validatorProvider = validatorProvider;
    this.nodeKeys = nodeKeys;
    this.localAddress = localAddress;
    this.proposerSelector = proposerSelector;
    this.peers = peers;
    this.roundTimer = roundTimer;
    this.blockTimer = blockTimer;
    this.blockCreatorFactory = blockCreatorFactory;
    this.messageFactory = messageFactory;
    this.ibftContextBlockHeaderValidator = ibftContextBlockHeaderValidator;
    this.clock = clock;
    this.messageTransmitter = new IbftMessageTransmitter(messageFactory, peers);
  }

  public int getQuorumSize() {
    return calculateRequiredValidatorQuorum(validatorProvider.getValidators().size());
  }

  public Collection<Address> getValidators() {
    return validatorProvider.getValidators();
  }

  public KeyPair getNodeKeys() {
    return nodeKeys;
  }

  public Address getLocalAddress() {
    return localAddress;
  }

  public ProposerSelector getProposerSelector() {
    return proposerSelector;
  }

  public boolean isLocalNodeProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    return getProposerForRound(roundIdentifier).equals(localAddress);
  }

  public IbftMulticaster getPeers() {
    return peers;
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

  public BlockHeaderValidator<IbftContext> getBlockHeaderValidator() {
    return ibftContextBlockHeaderValidator;
  }

  public IbftMessageTransmitter getTransmitter() {
    return messageTransmitter;
  }

  public Clock getClock() {
    return clock;
  }
}
