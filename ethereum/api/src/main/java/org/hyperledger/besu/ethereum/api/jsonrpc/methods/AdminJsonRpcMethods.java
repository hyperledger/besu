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
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminAddPeer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminChangeLogLevel;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminGenerateLogBloomCache;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminLogsRemoveCache;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminLogsRepairCache;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminNodeInfo;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminPeers;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminRemovePeer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.PluginsReloadConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.BesuPlugin;

import java.math.BigInteger;
import java.util.Map;

public class AdminJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final String clientVersion;
  private final BigInteger networkId;
  private final GenesisConfigOptions genesisConfigOptions;
  private final P2PNetwork p2pNetwork;
  private final BlockchainQueries blockchainQueries;
  private final NatService natService;
  private final Map<String, BesuPlugin> namedPlugins;
  private final EthPeers ethPeers;

  public AdminJsonRpcMethods(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork p2pNetwork,
      final BlockchainQueries blockchainQueries,
      final Map<String, BesuPlugin> namedPlugins,
      final NatService natService,
      final EthPeers ethPeers) {
    this.clientVersion = clientVersion;
    this.networkId = networkId;
    this.genesisConfigOptions = genesisConfigOptions;
    this.p2pNetwork = p2pNetwork;
    this.blockchainQueries = blockchainQueries;
    this.namedPlugins = namedPlugins;
    this.natService = natService;
    this.ethPeers = ethPeers;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.ADMIN.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    return mapOf(
        new AdminAddPeer(p2pNetwork),
        new AdminRemovePeer(p2pNetwork),
        new AdminNodeInfo(
            clientVersion,
            networkId,
            genesisConfigOptions,
            p2pNetwork,
            blockchainQueries,
            natService),
        new AdminPeers(ethPeers),
        new AdminChangeLogLevel(),
        new AdminGenerateLogBloomCache(blockchainQueries),
        new AdminLogsRepairCache(blockchainQueries),
        new AdminLogsRemoveCache(blockchainQueries),
        new PluginsReloadConfiguration(namedPlugins));
  }
}
