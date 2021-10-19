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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BesuController implements java.io.Closeable {
  private static final Logger LOG = LogManager.getLogger();

  public static final String DATABASE_PATH = "database";
  public static final String CACHE_PATH = "caches";
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthProtocolManager ethProtocolManager;
  private final GenesisConfigOptions genesisConfigOptions;
  private final SubProtocolConfiguration subProtocolConfiguration;
  private final NodeKey nodeKey;
  private final Synchronizer synchronizer;
  private final JsonRpcMethods additionalJsonRpcMethodsFactory;

  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final PrivacyParameters privacyParameters;
  private final List<Closeable> closeables;
  private final MiningParameters miningParameters;
  private final PluginServiceFactory additionalPluginServices;
  private final SyncState syncState;

  BesuController(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthProtocolManager ethProtocolManager,
      final GenesisConfigOptions genesisConfigOptions,
      final SubProtocolConfiguration subProtocolConfiguration,
      final Synchronizer synchronizer,
      final SyncState syncState,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final PrivacyParameters privacyParameters,
      final MiningParameters miningParameters,
      final JsonRpcMethods additionalJsonRpcMethodsFactory,
      final NodeKey nodeKey,
      final List<Closeable> closeables,
      final PluginServiceFactory additionalPluginServices) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethProtocolManager = ethProtocolManager;
    this.genesisConfigOptions = genesisConfigOptions;
    this.subProtocolConfiguration = subProtocolConfiguration;
    this.synchronizer = synchronizer;
    this.syncState = syncState;
    this.additionalJsonRpcMethodsFactory = additionalJsonRpcMethodsFactory;
    this.nodeKey = nodeKey;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.privacyParameters = privacyParameters;
    this.closeables = closeables;
    this.miningParameters = miningParameters;
    this.additionalPluginServices = additionalPluginServices;
  }

  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  public ProtocolSchedule getProtocolSchedule() {
    return protocolSchedule;
  }

  public EthProtocolManager getProtocolManager() {
    return ethProtocolManager;
  }

  public GenesisConfigOptions getGenesisConfigOptions() {
    return genesisConfigOptions;
  }

  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  public SubProtocolConfiguration getSubProtocolConfiguration() {
    return subProtocolConfiguration;
  }

  public NodeKey getNodeKey() {
    return nodeKey;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  @Override
  public void close() {
    closeables.forEach(this::tryClose);
  }

  private void tryClose(final Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      LOG.error("Unable to close resource.", e);
    }
  }

  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  public MiningParameters getMiningParameters() {
    return miningParameters;
  }

  public Map<String, JsonRpcMethod> getAdditionalJsonRpcMethods(
      final Collection<String> enabledRpcApis) {
    return additionalJsonRpcMethodsFactory.create(enabledRpcApis);
  }

  public SyncState getSyncState() {
    return syncState;
  }

  public PluginServiceFactory getAdditionalPluginServices() {
    return additionalPluginServices;
  }

  public static class Builder {

    public BesuControllerBuilder fromEthNetworkConfig(final EthNetworkConfig ethNetworkConfig) {
      return fromEthNetworkConfig(ethNetworkConfig, Collections.emptyMap());
    }

    public BesuControllerBuilder fromEthNetworkConfig(
        final EthNetworkConfig ethNetworkConfig, final Map<String, String> genesisConfigOverrides) {
      return fromGenesisConfig(
              GenesisConfigFile.fromConfig(ethNetworkConfig.getGenesisConfig()),
              genesisConfigOverrides)
          .networkId(ethNetworkConfig.getNetworkId());
    }

    public BesuControllerBuilder fromGenesisConfig(final GenesisConfigFile genesisConfig) {
      return fromGenesisConfig(genesisConfig, Collections.emptyMap());
    }

    BesuControllerBuilder fromGenesisConfig(
        final GenesisConfigFile genesisConfig, final Map<String, String> genesisConfigOverrides) {
      final GenesisConfigOptions configOptions =
          genesisConfig.getConfigOptions(genesisConfigOverrides);
      final List<BesuControllerBuilder> builders = new ArrayList<>();

      if (configOptions.getPowAlgorithm() != PowAlgorithm.UNSUPPORTED) {
        builders.add(new MainnetBesuControllerBuilder());
      }
      if (configOptions.isIbft2()) {
        builders.add(new IbftBesuControllerBuilder());
      }
      if (configOptions.isIbftLegacy()) {
        builders.add(new IbftLegacyBesuControllerBuilder());
      }
      if (configOptions.isQbft()) {
        builders.add(new QbftBesuControllerBuilder());
      }
      if (configOptions.isClique()) {
        builders.add(new CliqueBesuControllerBuilder());
      }

      builders.forEach(builder -> builder.genesisConfigFile(genesisConfig));

      if (configOptions.isBftMigration()) {
        // TODO throw error if not all bft besu controllers
        final List<BftBesuControllerBuilder> bftBesuControllerBuilders =
            builders.stream()
                .map(BftBesuControllerBuilder.class::cast)
                .collect(Collectors.toList());
        return new ForkingBftControllerBuilder(bftBesuControllerBuilders)
            .genesisConfigFile(genesisConfig);
      } else if (builders.size() == 1) {
        return builders.get(0).genesisConfigFile(genesisConfig);
      } else {
        throw new IllegalStateException("Unknown consensus mechanism");
      }
    }
  }
}
