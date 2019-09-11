/*
 * Copyright 2019 ConsenSys AG.
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

import tech.pegasys.pantheon.cli.config.EthNetworkConfig;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class PantheonController<C> implements java.io.Closeable {

  public static final String DATABASE_PATH = "database";
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthProtocolManager ethProtocolManager;
  private final GenesisConfigOptions genesisConfigOptions;
  private final SubProtocolConfiguration subProtocolConfiguration;
  private final KeyPair keyPair;
  private final Synchronizer synchronizer;
  private final JsonRpcMethodFactory additionalJsonRpcMethodsFactory;

  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final PrivacyParameters privacyParameters;
  private final Runnable close;
  private final SyncState syncState;

  PantheonController(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthProtocolManager ethProtocolManager,
      final GenesisConfigOptions genesisConfigOptions,
      final SubProtocolConfiguration subProtocolConfiguration,
      final Synchronizer synchronizer,
      final SyncState syncState,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final PrivacyParameters privacyParameters,
      final Runnable close,
      final JsonRpcMethodFactory additionalJsonRpcMethodsFactory,
      final KeyPair keyPair) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethProtocolManager = ethProtocolManager;
    this.genesisConfigOptions = genesisConfigOptions;
    this.subProtocolConfiguration = subProtocolConfiguration;
    this.synchronizer = synchronizer;
    this.syncState = syncState;
    this.additionalJsonRpcMethodsFactory = additionalJsonRpcMethodsFactory;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.privacyParameters = privacyParameters;
    this.close = close;
  }

  public ProtocolContext<C> getProtocolContext() {
    return protocolContext;
  }

  public ProtocolSchedule<C> getProtocolSchedule() {
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

  public KeyPair getLocalNodeKeyPair() {
    return keyPair;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  @Override
  public void close() {
    close.run();
  }

  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  public Map<String, JsonRpcMethod> getAdditionalJsonRpcMethods(
      final Collection<RpcApi> enabledRpcApis) {
    return additionalJsonRpcMethodsFactory.createJsonRpcMethods(enabledRpcApis);
  }

  public SyncState getSyncState() {
    return syncState;
  }

  public static class Builder {

    public PantheonControllerBuilder<?> fromEthNetworkConfig(
        final EthNetworkConfig ethNetworkConfig) {
      return fromEthNetworkConfig(ethNetworkConfig, Collections.emptyMap());
    }

    public PantheonControllerBuilder<?> fromEthNetworkConfig(
        final EthNetworkConfig ethNetworkConfig, final Map<String, String> genesisConfigOverrides) {
      return fromGenesisConfig(
              GenesisConfigFile.fromConfig(ethNetworkConfig.getGenesisConfig()),
              genesisConfigOverrides)
          .networkId(ethNetworkConfig.getNetworkId());
    }

    public PantheonControllerBuilder<?> fromGenesisConfig(final GenesisConfigFile genesisConfig) {
      return fromGenesisConfig(genesisConfig, Collections.emptyMap());
    }

    public PantheonControllerBuilder<?> fromGenesisConfig(
        final GenesisConfigFile genesisConfig, final Map<String, String> genesisConfigOverrides) {
      final GenesisConfigOptions configOptions =
          genesisConfig.getConfigOptions(genesisConfigOverrides);
      final PantheonControllerBuilder<?> builder;

      if (configOptions.isEthHash()) {
        builder = new MainnetPantheonControllerBuilder();
      } else if (configOptions.isIbft2()) {
        builder = new IbftPantheonControllerBuilder();
      } else if (configOptions.isIbftLegacy()) {
        builder = new IbftLegacyPantheonControllerBuilder();
      } else if (configOptions.isClique()) {
        builder = new CliquePantheonControllerBuilder();
      } else {
        throw new IllegalArgumentException("Unknown consensus mechanism defined");
      }
      return builder.genesisConfigFile(genesisConfig);
    }
  }
}
