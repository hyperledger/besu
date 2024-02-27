/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.common;

import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ConsensusContextFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;

import java.util.Optional;

/** The Migrating protocol context. */
public class MigratingProtocolContext extends ProtocolContext {

  private final ForksSchedule<ConsensusContext> consensusContextSchedule;

  /**
   * Instantiates a new Migrating protocol context.
   *
   * @param blockchain the blockchain
   * @param worldStateArchive the world state archive
   * @param consensusContextSchedule the consensus context schedule
   * @param transactionSelectorFactory the optional transaction selector factory
   * @param badBlockManager the cache to use to keep invalid blocks
   */
  public MigratingProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ForksSchedule<ConsensusContext> consensusContextSchedule,
      final Optional<PluginTransactionSelectorFactory> transactionSelectorFactory,
      final BadBlockManager badBlockManager) {
    super(blockchain, worldStateArchive, null, transactionSelectorFactory, badBlockManager);
    this.consensusContextSchedule = consensusContextSchedule;
  }

  /**
   * Init protocol context.
   *
   * @param blockchain the blockchain
   * @param worldStateArchive the world state archive
   * @param protocolSchedule the protocol schedule
   * @param consensusContextFactory the consensus context factory
   * @param transactionSelectorFactory the optional transaction selector factory
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol context
   */
  public static ProtocolContext init(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory,
      final Optional<PluginTransactionSelectorFactory> transactionSelectorFactory,
      final BadBlockManager badBlockManager) {
    final ConsensusContext consensusContext =
        consensusContextFactory.create(blockchain, worldStateArchive, protocolSchedule);
    final MigratingContext migratingContext = consensusContext.as(MigratingContext.class);
    return new MigratingProtocolContext(
        blockchain,
        worldStateArchive,
        migratingContext.getConsensusContextSchedule(),
        transactionSelectorFactory,
        badBlockManager);
  }

  @Override
  public <C extends ConsensusContext> C getConsensusContext(final Class<C> klass) {
    final long chainHeadBlockNumber = getBlockchain().getChainHeadBlockNumber();
    return consensusContextSchedule.getFork(chainHeadBlockNumber).getValue().as(klass);
  }
}
