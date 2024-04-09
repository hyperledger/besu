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
package org.hyperledger.besu.consensus.common.bft.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;

import java.time.Clock;
import java.util.Collection;

/** This is the full data set, or context, required for many of the aspects of BFT workflows. */
public class BftFinalState {

  private final ValidatorProvider validatorProvider;
  private final NodeKey nodeKey;
  private final Address localAddress;
  private final ProposerSelector proposerSelector;
  private final RoundTimer roundTimer;
  private final BlockTimer blockTimer;
  private final BftBlockCreatorFactory<?> blockCreatorFactory;
  private final Clock clock;
  private final ValidatorMulticaster validatorMulticaster;

  /**
   * Instantiates a new Bft final state.
   *
   * @param validatorProvider the validator provider
   * @param nodeKey the node key
   * @param localAddress the local address
   * @param proposerSelector the proposer selector
   * @param validatorMulticaster the validator multicaster
   * @param roundTimer the round timer
   * @param blockTimer the block timer
   * @param blockCreatorFactory the block creator factory
   * @param clock the clock
   */
  public BftFinalState(
      final ValidatorProvider validatorProvider,
      final NodeKey nodeKey,
      final Address localAddress,
      final ProposerSelector proposerSelector,
      final ValidatorMulticaster validatorMulticaster,
      final RoundTimer roundTimer,
      final BlockTimer blockTimer,
      final BftBlockCreatorFactory<?> blockCreatorFactory,
      final Clock clock) {
    this.validatorProvider = validatorProvider;
    this.nodeKey = nodeKey;
    this.localAddress = localAddress;
    this.proposerSelector = proposerSelector;
    this.roundTimer = roundTimer;
    this.blockTimer = blockTimer;
    this.blockCreatorFactory = blockCreatorFactory;
    this.clock = clock;
    this.validatorMulticaster = validatorMulticaster;
  }

  /**
   * Gets quorum.
   *
   * @return the quorum
   */
  public int getQuorum() {
    return BftHelpers.calculateRequiredValidatorQuorum(getValidators().size());
  }

  /**
   * Gets validators.
   *
   * @return the validators
   */
  public Collection<Address> getValidators() {
    return validatorProvider.getValidatorsAtHead();
  }

  /**
   * Gets node key.
   *
   * @return the node key
   */
  public NodeKey getNodeKey() {
    return nodeKey;
  }

  /**
   * Gets local address.
   *
   * @return the local address
   */
  public Address getLocalAddress() {
    return localAddress;
  }

  /**
   * Is local node proposer for round.
   *
   * @param roundIdentifier the round identifier
   * @return the boolean
   */
  public boolean isLocalNodeProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    return getProposerForRound(roundIdentifier).equals(localAddress);
  }

  /**
   * Is local node validator.
   *
   * @return the boolean
   */
  public boolean isLocalNodeValidator() {
    final boolean isValidator = getValidators().contains(localAddress);
    return isValidator;
  }

  /**
   * Gets round timer.
   *
   * @return the round timer
   */
  public RoundTimer getRoundTimer() {
    return roundTimer;
  }

  /**
   * Gets block timer.
   *
   * @return the block timer
   */
  public BlockTimer getBlockTimer() {
    return blockTimer;
  }

  /**
   * Gets block creator factory.
   *
   * @return the block creator factory
   */
  public BftBlockCreatorFactory<?> getBlockCreatorFactory() {
    return blockCreatorFactory;
  }

  /**
   * Gets proposer for round.
   *
   * @param roundIdentifier the round identifier
   * @return the proposer for round
   */
  public Address getProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    return proposerSelector.selectProposerForRound(roundIdentifier);
  }

  /**
   * Gets validator multicaster.
   *
   * @return the validator multicaster
   */
  public ValidatorMulticaster getValidatorMulticaster() {
    return validatorMulticaster;
  }

  /**
   * Gets clock.
   *
   * @return the clock
   */
  public Clock getClock() {
    return clock;
  }
}
