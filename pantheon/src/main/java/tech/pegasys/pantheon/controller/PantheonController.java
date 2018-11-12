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
package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public interface PantheonController<C> extends Closeable {

  String DATABASE_PATH = "database";

  static PantheonController<?> fromConfig(
      final SynchronizerConfiguration syncConfig,
      final String configContents,
      final Path pantheonHome,
      final boolean ottomanTestnetOperation,
      final int networkId,
      final MiningParameters miningParameters,
      final KeyPair nodeKeys)
      throws IOException {

    final GenesisConfigFile config = GenesisConfigFile.fromConfig(configContents);
    final GenesisConfigOptions configOptions = config.getConfigOptions();

    if (configOptions.isEthHash()) {
      return MainnetPantheonController.init(
          pantheonHome,
          config,
          MainnetProtocolSchedule.fromConfig(configOptions),
          syncConfig,
          miningParameters,
          nodeKeys);
    } else if (configOptions.isIbft()) {
      return IbftPantheonController.init(
          pantheonHome, config, syncConfig, ottomanTestnetOperation, networkId, nodeKeys);
    } else if (configOptions.isClique()) {
      return CliquePantheonController.init(
          pantheonHome, config, syncConfig, miningParameters, networkId, nodeKeys);
    } else {
      throw new IllegalArgumentException("Unknown consensus mechanism defined");
    }
  }

  ProtocolContext<C> getProtocolContext();

  ProtocolSchedule<C> getProtocolSchedule();

  GenesisConfigOptions getGenesisConfigOptions();

  Synchronizer getSynchronizer();

  SubProtocolConfiguration subProtocolConfiguration();

  KeyPair getLocalNodeKeyPair();

  TransactionPool getTransactionPool();

  MiningCoordinator getMiningCoordinator();
}
