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
 *
 */
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.json.JsonObject;

@Module
public class GenesisFileModule {

  private final String genesisConfig;

  protected GenesisFileModule(final File genesisFile) throws IOException {
    this.genesisConfig = Files.readString(genesisFile.toPath(), Charset.defaultCharset());
  }

  protected GenesisFileModule(final String genesisConfig) {
    this.genesisConfig = genesisConfig;
  }

  @Singleton
  @Provides
  GenesisConfigFile providesGenesisConfigFile() {
    return GenesisConfigFile.fromConfig(genesisConfig);
  }

  @Singleton
  @Provides
  GenesisConfigOptions provideGenesisConfigOptions(final GenesisConfigFile genesisConfigFile) {
    return genesisConfigFile.getConfigOptions();
  }

  @Singleton
  @Provides
  ProtocolSchedule provideProtocolSchedule(
      final GenesisConfigOptions configOptions,
      @Named("RevertReasonEnabled") final boolean revertReasonEnabled) {
    throw new RuntimeException("Abstract");
  }

  @Singleton
  @Provides
  GenesisState provideGenesisState(
      final GenesisConfigFile genesisConfigFile, final ProtocolSchedule protocolSchedule) {
    return GenesisState.fromConfig(genesisConfigFile, protocolSchedule);
  }

  @Singleton
  @Provides
  BlockHeaderFunctions blockHashFunction() {
    throw new RuntimeException("Abstract");
  }

  @Singleton
  @Provides
  @Named("GenesisBlock")
  Block provideGenesisBlock(final GenesisState genesisState) {
    return genesisState.getBlock();
  }

  static GenesisFileModule createGenesisModule(final NetworkName networkName) {
    return createGenesisModule(EthNetworkConfig.jsonConfig(networkName));
  }

  static GenesisFileModule createGenesisModule(final File genesisFile) throws IOException {
    return createGenesisModule(Files.readString(genesisFile.toPath(), Charset.defaultCharset()));
  }

  private static GenesisFileModule createGenesisModule(final String genesisConfig) {
    // duplicating work from JsonGenesisConfigOptions, but in a refactoring this goes away.
    final JsonObject genesis = new JsonObject(genesisConfig);
    final JsonObject config = genesis.getJsonObject("config");
    if (config.containsKey("ethash")) {
      return new MainnetGenesisFileModule(genesisConfig);
    } else if (config.containsKey("ibft")) {
      return new IBFTGenesisFileModule(genesisConfig);
    } else if (config.containsKey("clique")) {
      return new CliqueGenesisFileModule(genesisConfig);
    } else if (config.containsKey("qbft")) {
      return new QBFTGenesisFileModule(genesisConfig);
    } else {
      // default is mainnet
      return new MainnetGenesisFileModule(genesisConfig);
    }
  }
}
