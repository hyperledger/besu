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

import static java.util.Collections.emptyMap;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

public interface PantheonController<C> extends Closeable {

  String DATABASE_PATH = "database";

  static PantheonController<?> fromConfig(
      final GenesisConfigFile genesisConfigFile,
      final SynchronizerConfiguration syncConfig,
      final StorageProvider storageProvider,
      final boolean ottomanTestnetOperation,
      final int networkId,
      final MiningParameters miningParameters,
      final KeyPair nodeKeys,
      final MetricsSystem metricsSystem,
      final PrivacyParameters privacyParameters) {

    final GenesisConfigOptions configOptions = genesisConfigFile.getConfigOptions();

    if (configOptions.isEthHash()) {
      return MainnetPantheonController.init(
          storageProvider,
          genesisConfigFile,
          MainnetProtocolSchedule.fromConfig(configOptions, privacyParameters),
          syncConfig,
          miningParameters,
          nodeKeys,
          metricsSystem,
          privacyParameters);
    } else if (configOptions.isIbft2()) {
      return IbftPantheonController.init(
          storageProvider,
          genesisConfigFile,
          syncConfig,
          miningParameters,
          networkId,
          nodeKeys,
          metricsSystem);
    } else if (configOptions.isIbftLegacy()) {
      return IbftLegacyPantheonController.init(
          storageProvider,
          genesisConfigFile,
          syncConfig,
          ottomanTestnetOperation,
          networkId,
          nodeKeys,
          metricsSystem);
    } else if (configOptions.isClique()) {
      return CliquePantheonController.init(
          storageProvider,
          genesisConfigFile,
          syncConfig,
          miningParameters,
          networkId,
          nodeKeys,
          metricsSystem);
    } else {
      throw new IllegalArgumentException("Unknown consensus mechanism defined");
    }
  }

  ProtocolContext<C> getProtocolContext();

  ProtocolSchedule<C> getProtocolSchedule();

  Synchronizer getSynchronizer();

  SubProtocolConfiguration subProtocolConfiguration();

  KeyPair getLocalNodeKeyPair();

  TransactionPool getTransactionPool();

  MiningCoordinator getMiningCoordinator();

  PrivacyParameters getPrivacyParameters();

  default Map<String, JsonRpcMethod> getAdditionalJsonRpcMethods(
      final Collection<RpcApi> enabledRpcApis) {
    return emptyMap();
  }
}
