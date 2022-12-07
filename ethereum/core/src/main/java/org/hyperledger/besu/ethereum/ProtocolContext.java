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

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
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
  private final ConsensusContext consensusContext;

  public ProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ConsensusContext consensusContext) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.consensusContext = consensusContext;
  }

  public static ProtocolContext init(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory) {
    return new ProtocolContext(
        blockchain,
        worldStateArchive,
        consensusContextFactory.create(blockchain, worldStateArchive, protocolSchedule));
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  public <C extends ConsensusContext> C getConsensusContext(final Class<C> klass) {
    return consensusContext.as(klass);
  }

  public <C extends ConsensusContext> Optional<C> safeConsensusContext(final Class<C> klass) {
    return Optional.ofNullable(consensusContext)
        .filter(c -> klass.isAssignableFrom(c.getClass()))
        .map(klass::cast);
  }
}
