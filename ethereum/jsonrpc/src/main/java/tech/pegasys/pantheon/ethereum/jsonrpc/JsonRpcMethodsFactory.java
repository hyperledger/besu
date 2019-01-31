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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminAddPeer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminPeers;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugMetrics;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugStorageRangeAt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugTraceTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthAccounts;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthBlockNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthCall;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthChainId;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthCoinbase;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthEstimateGas;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGasPrice;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBalance;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockByNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetCode;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterChanges;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterLogs;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetLogs;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetStorageAt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockHashAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockNumberAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionReceipt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleByBlockHashAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleByBlockNumberAndIndex;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleCountByBlockHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleCountByBlockNumber;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetWork;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthHashrate;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthMining;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewBlockFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthNewPendingTransactionFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthProtocolVersion;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthSendRawTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthSyncing;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthUninstallFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetListening;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetPeerCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetVersion;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.Web3ClientVersion;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.Web3Sha3;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerSetCoinbase;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerSetEtherbase;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerStart;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerStop;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermAddAccountsToWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermAddNodesToWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermGetAccountsWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermGetNodesWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.EeaSendRawTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockReplay;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTracer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.permissioning.AccountWhitelistController;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JsonRpcMethodsFactory {

  private final BlockResultFactory blockResult = new BlockResultFactory();
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final P2PNetwork peerNetworkingService,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final ProtocolSchedule<?> protocolSchedule,
      final MiningCoordinator miningCoordinator,
      final MetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApi> rpcApis,
      final FilterManager filterManager,
      final AccountWhitelistController accountsWhitelistController,
      final PrivateTransactionHandler privateTransactionHandler) {
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    return methods(
        clientVersion,
        peerNetworkingService,
        blockchainQueries,
        synchronizer,
        protocolSchedule,
        filterManager,
        transactionPool,
        miningCoordinator,
        metricsSystem,
        supportedCapabilities,
        accountsWhitelistController,
        rpcApis,
        privateTransactionHandler);
  }

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final P2PNetwork p2pNetwork,
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule<?> protocolSchedule,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final MetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final AccountWhitelistController accountsWhitelistController,
      final Collection<RpcApi> rpcApis,
      final PrivateTransactionHandler privateTransactionHandler) {
    final Map<String, JsonRpcMethod> enabledMethods = new HashMap<>();
    // @formatter:off
    if (rpcApis.contains(RpcApis.ETH)) {
      addMethods(
          enabledMethods,
          new EthAccounts(),
          new EthBlockNumber(blockchainQueries),
          new EthGetBalance(blockchainQueries, parameter),
          new EthGetBlockByHash(blockchainQueries, blockResult, parameter),
          new EthGetBlockByNumber(blockchainQueries, blockResult, parameter),
          new EthGetBlockTransactionCountByNumber(blockchainQueries, parameter),
          new EthGetBlockTransactionCountByHash(blockchainQueries, parameter),
          new EthCall(
              blockchainQueries,
              new TransientTransactionProcessor(
                  blockchainQueries.getBlockchain(),
                  blockchainQueries.getWorldStateArchive(),
                  protocolSchedule),
              parameter),
          new EthGetCode(blockchainQueries, parameter),
          new EthGetLogs(blockchainQueries, parameter),
          new EthGetUncleCountByBlockHash(blockchainQueries, parameter),
          new EthGetUncleCountByBlockNumber(blockchainQueries, parameter),
          new EthGetUncleByBlockNumberAndIndex(blockchainQueries, parameter),
          new EthGetUncleByBlockHashAndIndex(blockchainQueries, parameter),
          new EthNewBlockFilter(filterManager),
          new EthNewPendingTransactionFilter(filterManager),
          new EthNewFilter(filterManager, parameter),
          new EthGetTransactionByHash(
              blockchainQueries, transactionPool.getPendingTransactions(), parameter),
          new EthGetTransactionByBlockHashAndIndex(blockchainQueries, parameter),
          new EthGetTransactionByBlockNumberAndIndex(blockchainQueries, parameter),
          new EthGetTransactionCount(
              blockchainQueries, transactionPool.getPendingTransactions(), parameter),
          new EthGetTransactionReceipt(blockchainQueries, parameter),
          new EthUninstallFilter(filterManager, parameter),
          new EthGetFilterChanges(filterManager, parameter),
          new EthGetFilterLogs(filterManager, parameter),
          new EthSyncing(synchronizer),
          new EthGetStorageAt(blockchainQueries, parameter),
          new EthSendRawTransaction(transactionPool, parameter),
          new EthEstimateGas(
              blockchainQueries,
              new TransientTransactionProcessor(
                  blockchainQueries.getBlockchain(),
                  blockchainQueries.getWorldStateArchive(),
                  protocolSchedule),
              parameter),
          new EthMining(miningCoordinator),
          new EthCoinbase(miningCoordinator),
          new EthProtocolVersion(supportedCapabilities),
          new EthGasPrice(miningCoordinator),
          new EthGetWork(miningCoordinator),
          new EthHashrate(miningCoordinator),
          new EthChainId(protocolSchedule.getChainId()));
    }
    if (rpcApis.contains(RpcApis.DEBUG)) {
      final BlockReplay blockReplay =
          new BlockReplay(
              protocolSchedule,
              blockchainQueries.getBlockchain(),
              blockchainQueries.getWorldStateArchive());
      addMethods(
          enabledMethods,
          new DebugTraceTransaction(
              blockchainQueries, new TransactionTracer(blockReplay), parameter),
          new DebugStorageRangeAt(parameter, blockchainQueries, blockReplay),
          new DebugMetrics(metricsSystem));
    }
    if (rpcApis.contains(RpcApis.NET)) {
      addMethods(
          enabledMethods,
          new NetVersion(protocolSchedule.getChainId()),
          new NetListening(p2pNetwork),
          new NetPeerCount(p2pNetwork));
    }
    if (rpcApis.contains(RpcApis.WEB3)) {
      addMethods(enabledMethods, new Web3ClientVersion(clientVersion), new Web3Sha3());
    }
    if (rpcApis.contains(RpcApis.MINER)) {
      final MinerSetCoinbase minerSetCoinbase = new MinerSetCoinbase(miningCoordinator, parameter);
      addMethods(
          enabledMethods,
          new MinerStart(miningCoordinator),
          new MinerStop(miningCoordinator),
          minerSetCoinbase,
          new MinerSetEtherbase(minerSetCoinbase));
    }
    if (rpcApis.contains(RpcApis.PERM)) {
      addMethods(
          enabledMethods,
          new PermAddNodesToWhitelist(p2pNetwork, parameter),
          new PermRemoveNodesFromWhitelist(p2pNetwork, parameter),
          new PermGetNodesWhitelist(p2pNetwork));
    }
    if (rpcApis.contains(RpcApis.ADMIN)) {
      addMethods(enabledMethods, new AdminPeers(p2pNetwork));
      addMethods(enabledMethods, new AdminAddPeer(p2pNetwork, parameter));
    }
    if (rpcApis.contains(RpcApis.PERM)) {
      addMethods(
          enabledMethods,
          new PermGetAccountsWhitelist(accountsWhitelistController),
          new PermAddAccountsToWhitelist(accountsWhitelistController, parameter),
          new PermRemoveAccountsFromWhitelist(accountsWhitelistController, parameter));
    }
    if (rpcApis.contains(RpcApis.EEA)) {
      addMethods(
          enabledMethods,
          new EeaSendRawTransaction(privateTransactionHandler, transactionPool, parameter));
    }
    // @formatter:off
    return enabledMethods;
  }

  private void addMethods(
      final Map<String, JsonRpcMethod> methods, final JsonRpcMethod... rpcMethods) {
    for (final JsonRpcMethod rpcMethod : rpcMethods) {
      methods.put(rpcMethod.getName(), rpcMethod);
    }
  }
}
