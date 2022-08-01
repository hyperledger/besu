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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.RpcModules;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.BesuPlugin;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonRpcMethodsFactory {

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork p2pNetwork,
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final ObservableMetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Optional<AccountLocalConfigPermissioningController> accountsAllowlistController,
      final Optional<NodeLocalConfigPermissioningController> nodeAllowlistController,
      final Collection<String> rpcApis,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final NatService natService,
      final Map<String, BesuPlugin> namedPlugins,
      final Path dataDir,
      final EthPeers ethPeers) {
    final Map<String, JsonRpcMethod> enabled = new HashMap<>();

    if (!rpcApis.isEmpty()) {
      final JsonRpcMethod modules = new RpcModules(rpcApis);
      enabled.put(modules.getName(), modules);

      final List<JsonRpcMethods> availableApiGroups =
          List.of(
              new AdminJsonRpcMethods(
                  clientVersion,
                  networkId,
                  genesisConfigOptions,
                  p2pNetwork,
                  blockchainQueries,
                  namedPlugins,
                  natService,
                  ethPeers),
              new DebugJsonRpcMethods(
                  blockchainQueries, protocolSchedule, metricsSystem, transactionPool, dataDir),
              new EeaJsonRpcMethods(
                  blockchainQueries, protocolSchedule, transactionPool, privacyParameters),
              new ExecutionEngineJsonRpcMethods(miningCoordinator, protocolContext),
              new GoQuorumJsonRpcPrivacyMethods(
                  blockchainQueries, protocolSchedule, transactionPool, privacyParameters),
              new EthJsonRpcMethods(
                  blockchainQueries,
                  synchronizer,
                  protocolSchedule,
                  filterManager,
                  transactionPool,
                  miningCoordinator,
                  supportedCapabilities,
                  privacyParameters),
              new NetJsonRpcMethods(
                  p2pNetwork,
                  networkId,
                  jsonRpcConfiguration,
                  webSocketConfiguration,
                  metricsConfiguration),
              new MinerJsonRpcMethods(miningCoordinator),
              new PermJsonRpcMethods(accountsAllowlistController, nodeAllowlistController),
              new PrivJsonRpcMethods(
                  blockchainQueries,
                  protocolSchedule,
                  transactionPool,
                  privacyParameters,
                  filterManager),
              new PrivxJsonRpcMethods(
                  blockchainQueries, protocolSchedule, transactionPool, privacyParameters),
              new Web3JsonRpcMethods(clientVersion),
              new TraceJsonRpcMethods(blockchainQueries, protocolSchedule, privacyParameters),
              new TxPoolJsonRpcMethods(transactionPool),
              new PluginsJsonRpcMethods(namedPlugins));

      for (final JsonRpcMethods apiGroup : availableApiGroups) {
        enabled.putAll(apiGroup.create(rpcApis));
      }
    }

    return enabled;
  }
}
