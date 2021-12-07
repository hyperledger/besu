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
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class MigratingProtocolContext extends ProtocolContext {

  private final ForksSchedule<ConsensusContext> consensusContextSchedule;

  public MigratingProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ForksSchedule<ConsensusContext> consensusContextSchedule) {
    super(blockchain, worldStateArchive, null);
    this.consensusContextSchedule = consensusContextSchedule;
  }

  public static ProtocolContext init(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory) {
    final ConsensusContext consensusContext =
        consensusContextFactory.create(blockchain, worldStateArchive, protocolSchedule);
    final MigratingContext migratingContext = consensusContext.as(MigratingContext.class);
    return new MigratingProtocolContext(
        blockchain, worldStateArchive, migratingContext.getConsensusContextSchedule());
  }

  @Override
  public <C extends ConsensusContext> C getConsensusContext(final Class<C> klass) {
    final long chainHeadBlockNumber = getBlockchain().getChainHeadBlockNumber();
    return consensusContextSchedule.getFork(chainHeadBlockNumber).getValue().as(klass);
  }
}
