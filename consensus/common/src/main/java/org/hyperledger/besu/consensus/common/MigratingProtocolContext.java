/*
 * Copyright contributors to Hyperledger Besu.
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
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

/** The Migrating protocol context. */
public class MigratingProtocolContext extends ProtocolContext {

  private final ForksSchedule<ConsensusContext> consensusContextSchedule;

  /**
   * Instantiates a new Migrating protocol context.
   *
   * @param blockchain the blockchain
   * @param worldStateArchive the world state archive
   * @param migratingConsensusContext the consensus context
   * @param badBlockManager the cache to use to keep invalid blocks
   */
  public MigratingProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final MigratingConsensusContext migratingConsensusContext,
      final BadBlockManager badBlockManager) {
    super(blockchain, worldStateArchive, migratingConsensusContext, badBlockManager);
    this.consensusContextSchedule = migratingConsensusContext.getConsensusContextSchedule();
  }

  @Override
  public <C extends ConsensusContext> C getConsensusContext(final Class<C> klass) {
    final long chainHeadBlockNumber = getBlockchain().getChainHeadBlockNumber();
    return consensusContextSchedule.getFork(chainHeadBlockNumber).getValue().as(klass);
  }
}
