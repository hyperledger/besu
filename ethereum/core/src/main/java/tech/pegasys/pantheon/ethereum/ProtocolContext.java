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
package tech.pegasys.pantheon.ethereum;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.chain.DefaultBlockchain;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStatePreimageStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.function.BiFunction;

/**
 * Holds the mutable state used to track the current context of the protocol. This is primarily the
 * blockchain and world state archive, but can also hold arbitrary context required by a particular
 * consensus algorithm.
 *
 * @param <C> the type of the consensus algorithm context
 */
public class ProtocolContext<C> {
  private final MutableBlockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final C consensusState;

  public ProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final C consensusState) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.consensusState = consensusState;
  }

  public static <T> ProtocolContext<T> init(
      final StorageProvider storageProvider,
      final GenesisState genesisState,
      final ProtocolSchedule<T> protocolSchedule,
      final MetricsSystem metricsSystem,
      final BiFunction<Blockchain, WorldStateArchive, T> consensusContextFactory) {
    final BlockchainStorage blockchainStorage =
        storageProvider.createBlockchainStorage(protocolSchedule);
    final WorldStateStorage worldStateStorage = storageProvider.createWorldStateStorage();
    final WorldStatePreimageStorage preimageStorage =
        storageProvider.createWorldStatePreimageStorage();

    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisState.getBlock(), blockchainStorage, metricsSystem);

    final WorldStateArchive worldStateArchive =
        new WorldStateArchive(worldStateStorage, preimageStorage);
    genesisState.writeStateTo(worldStateArchive.getMutable());

    return new ProtocolContext<>(
        blockchain,
        worldStateArchive,
        consensusContextFactory.apply(blockchain, worldStateArchive));
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  public C getConsensusState() {
    return consensusState;
  }
}
