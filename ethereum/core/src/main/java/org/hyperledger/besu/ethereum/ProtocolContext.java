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
package org.hyperledger.besu.ethereum;

import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

/**
 * Holds the mutable state used to track the current context of the protocol. This is primarily the
 * blockchain and world state archive, but can also hold arbitrary context required by a particular
 * consensus algorithm.
 */
public class ProtocolContext {
  private final MutableBlockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final BadBlockManager badBlockManager;
  private final ConsensusContext consensusContext;

  private Optional<Synchronizer> synchronizer;

  /**
   * Constructs a new ProtocolContext with the given blockchain, world state archive, consensus
   * context, and bad block manager.
   *
   * @param blockchain the blockchain of the protocol context
   * @param worldStateArchive the world state archive of the protocol context
   * @param consensusContext the consensus context of the protocol context
   * @param badBlockManager the bad block manager of the protocol context
   */
  public ProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ConsensusContext consensusContext,
      final BadBlockManager badBlockManager) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.consensusContext = consensusContext;
    this.synchronizer = Optional.empty();
    this.badBlockManager = badBlockManager;
  }

  /**
   * Initializes a new ProtocolContext with the given blockchain, world state archive, protocol
   * schedule, consensus context factory, and bad block manager.
   *
   * @param blockchain the blockchain of the protocol context
   * @param worldStateArchive the world state archive of the protocol context
   * @param protocolSchedule the protocol schedule of the protocol context
   * @param consensusContextFactory the consensus context factory of the protocol context
   * @param badBlockManager the bad block manager of the protocol context
   * @return the initialized ProtocolContext
   */
  public static ProtocolContext init(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory,
      final BadBlockManager badBlockManager) {
    return new ProtocolContext(
        blockchain,
        worldStateArchive,
        consensusContextFactory.create(blockchain, worldStateArchive, protocolSchedule),
        badBlockManager);
  }

  /**
   * Gets the synchronizer of the protocol context.
   *
   * @return the synchronizer of the protocol context
   */
  public Optional<Synchronizer> getSynchronizer() {
    return synchronizer;
  }

  /**
   * Sets the synchronizer of the protocol context.
   *
   * @param synchronizer the synchronizer to set
   */
  public void setSynchronizer(final Optional<Synchronizer> synchronizer) {
    this.synchronizer = synchronizer;
  }

  /**
   * Gets the blockchain of the protocol context.
   *
   * @return the blockchain of the protocol context
   */
  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  /**
   * Gets the world state archive of the protocol context.
   *
   * @return the world state archive of the protocol context
   */
  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  /**
   * Gets the bad block manager of the protocol context.
   *
   * @return the bad block manager of the protocol context
   */
  public BadBlockManager getBadBlockManager() {
    return badBlockManager;
  }

  /**
   * Gets the consensus context of the protocol context.
   *
   * @param <C> the type of the consensus context
   * @param klass the klass
   * @return the consensus context of the protocol context
   */
  public <C extends ConsensusContext> C getConsensusContext(final Class<C> klass) {
    return consensusContext.as(klass);
  }

  /**
   * Gets the safe consensus context of the protocol context.
   *
   * @param <C> the type of the consensus context
   * @param klass the klass
   * @return the consensus context of the protocol context
   */
  public <C extends ConsensusContext> Optional<C> safeConsensusContext(final Class<C> klass) {
    return Optional.ofNullable(consensusContext)
        .filter(c -> klass.isAssignableFrom(c.getClass()))
        .map(klass::cast);
  }
}
