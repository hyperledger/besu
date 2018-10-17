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
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;

public class ExecutionContextTestFixture {

  private final Block genesis = GenesisConfig.mainnet().getBlock();
  private final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
  private final MutableBlockchain blockchain =
      new DefaultMutableBlockchain(genesis, keyValueStorage, MainnetBlockHashFunction::createHash);
  private final WorldStateArchive stateArchive =
      new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));

  ProtocolSchedule<Void> protocolSchedule;
  ProtocolContext<Void> protocolContext = new ProtocolContext<>(blockchain, stateArchive, null);

  public ExecutionContextTestFixture() {
    this(MainnetProtocolSchedule.create(2, 3, 10, 11, 12, -1, 42));
  }

  public ExecutionContextTestFixture(final ProtocolSchedule<Void> protocolSchedule) {
    GenesisConfig.mainnet()
        .writeStateTo(
            new DefaultMutableWorldState(new KeyValueStorageWorldStateStorage(keyValueStorage)));
    this.protocolSchedule = protocolSchedule;
  }

  public Block getGenesis() {
    return genesis;
  }

  public KeyValueStorage getKeyValueStorage() {
    return keyValueStorage;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getStateArchive() {
    return stateArchive;
  }

  public ProtocolSchedule<Void> getProtocolSchedule() {
    return protocolSchedule;
  }

  public ProtocolContext<Void> getProtocolContext() {
    return protocolContext;
  }
}
