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
package org.hyperledger.besu.ethereum.core;

import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.util.function.Function;

public class ExecutionContextTestFixture {

  private final Block genesis;
  private final KeyValueStorage keyValueStorage;
  private final MutableBlockchain blockchain;
  private final WorldStateArchive stateArchive;

  private final ProtocolSchedule<Void> protocolSchedule;
  private final ProtocolContext<Void> protocolContext;

  private ExecutionContextTestFixture(
      final ProtocolSchedule<Void> protocolSchedule, final KeyValueStorage keyValueStorage) {
    final GenesisState genesisState =
        GenesisState.fromConfig(GenesisConfigFile.mainnet(), protocolSchedule);
    this.genesis = genesisState.getBlock();
    this.keyValueStorage = keyValueStorage;
    this.blockchain =
        DefaultBlockchain.createMutable(
            genesis,
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                keyValueStorage, new MainnetBlockHeaderFunctions()),
            new NoOpMetricsSystem());
    this.stateArchive = createInMemoryWorldStateArchive();
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = new ProtocolContext<>(blockchain, stateArchive, null);
    genesisState.writeStateTo(stateArchive.getMutable());
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
        protocolSchedule =
            new ProtocolScheduleBuilder<>(
                    new StubGenesisConfigOptions().constantinopleFixBlock(0),
                    BigInteger.valueOf(42),
                    Function.identity(),
                    new PrivacyParameters(),
                    false)
                .createProtocolSchedule();
      }
      if (keyValueStorage == null) {
        keyValueStorage = new InMemoryKeyValueStorage();
      }
      return new ExecutionContextTestFixture(protocolSchedule, keyValueStorage);
    }
  }
}
