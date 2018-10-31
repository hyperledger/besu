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
import tech.pegasys.pantheon.ethereum.db.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;

public class ExecutionContextTestFixture {

  private final Block genesis;
  private final KeyValueStorage keyValueStorage;
  private final MutableBlockchain blockchain;
  private final WorldStateArchive stateArchive;

  private final ProtocolSchedule<Void> protocolSchedule;
  private final ProtocolContext<Void> protocolContext;

  private ExecutionContextTestFixture(
      final ProtocolSchedule<Void> protocolSchedule, final KeyValueStorage keyValueStorage) {
    final GenesisConfig<Void> genesisConfig = GenesisConfig.mainnet();
    this.genesis = genesisConfig.getBlock();
    this.keyValueStorage = keyValueStorage;
    this.blockchain =
        new DefaultMutableBlockchain(
            genesis,
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                keyValueStorage, MainnetBlockHashFunction::createHash));
    this.stateArchive =
        new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = new ProtocolContext<>(blockchain, stateArchive, null);

    genesisConfig.writeStateTo(
        new DefaultMutableWorldState(new KeyValueStorageWorldStateStorage(keyValueStorage)));
  }

  public static ExecutionContextTestFixture create() {
    return new Builder().build();
  }

  public static Builder builder() {
    return new Builder();
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

  public static class Builder {

    private KeyValueStorage keyValueStorage;
    private ProtocolSchedule<Void> protocolSchedule;

    public Builder keyValueStorage(final KeyValueStorage keyValueStorage) {
      this.keyValueStorage = keyValueStorage;
      return this;
    }

    public Builder protocolSchedule(final ProtocolSchedule<Void> protocolSchedule) {
      this.protocolSchedule = protocolSchedule;
      return this;
    }

    public ExecutionContextTestFixture build() {
      if (protocolSchedule == null) {
        protocolSchedule = MainnetProtocolSchedule.create(0, 0, 0, 0, 0, 0, 42);
      }
      if (keyValueStorage == null) {
        keyValueStorage = new InMemoryKeyValueStorage();
      }
      return new ExecutionContextTestFixture(protocolSchedule, keyValueStorage);
    }
  }
}
