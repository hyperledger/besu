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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminAddPeer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminChangeLogLevel;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminNodeInfo;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminPeers;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AdminRemovePeer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugAccountRange;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugMetrics;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugStorageRangeAt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlockByHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlockByNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthAccounts;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthBlockNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthCall;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthChainId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthCoinbase;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthEstimateGas;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGasPrice;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBalance;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBlockByHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBlockByNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBlockTransactionCountByHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBlockTransactionCountByNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetCode;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetFilterChanges;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetFilterLogs;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetLogs;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetProof;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetStorageAt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionByBlockHashAndIndex;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionByBlockNumberAndIndex;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionByHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionReceipt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetUncleByBlockHashAndIndex;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetUncleByBlockNumberAndIndex;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetUncleCountByBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetUncleCountByBlockNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetWork;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthHashrate;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthMining;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthNewBlockFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthNewFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthNewPendingTransactionFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthProtocolVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSendRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSendTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSyncing;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthUninstallFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetEnode;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetListening;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetPeerCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetServices;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.RpcModules;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceReplayBlockTransactions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TxPoolBesuStatistics;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TxPoolBesuTransactions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.Web3ClientVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.Web3Sha3;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerSetCoinbase;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerSetEtherbase;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerStart;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner.MinerStop;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddAccountsToWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddNodesToWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetAccountsWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetNodesWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermReloadPermissionsFromFile;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea.EeaGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea.EeaGetTransactionReceipt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea.EeaPrivateNonceProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea.EeaSendRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivCreatePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivDeletePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivFindPrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivacyPrecompileAddress;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.RandomSigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonRpcMethodsFactory {

  private final BlockResultFactory blockResult = new BlockResultFactory();
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork peerNetworkingService,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final ProtocolSchedule<?> protocolSchedule,
      final MiningCoordinator miningCoordinator,
      final ObservableMetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApi> rpcApis,
      final FilterManager filterManager,
      final Optional<AccountLocalConfigPermissioningController> accountsWhitelistController,
      final Optional<NodeLocalConfigPermissioningController> nodeWhitelistController,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration) {
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    return methods(
        clientVersion,
        networkId,
        genesisConfigOptions,
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
        nodeWhitelistController,
        rpcApis,
        privacyParameters,
        jsonRpcConfiguration,
        webSocketConfiguration,
        metricsConfiguration);
  }

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final BigInteger networkId,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork p2pNetwork,
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule<?> protocolSchedule,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final ObservableMetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Optional<AccountLocalConfigPermissioningController> accountsWhitelistController,
      final Optional<NodeLocalConfigPermissioningController> nodeWhitelistController,
      final Collection<RpcApi> rpcApis,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration) {
    final Map<String, JsonRpcMethod> enabledMethods = new HashMap<>();
    if (!rpcApis.isEmpty()) {
      addMethods(enabledMethods, new RpcModules(rpcApis));
    }
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
              new TransactionSimulator(
                  blockchainQueries.getBlockchain(),
                  blockchainQueries.getWorldStateArchive(),
                  protocolSchedule),
              parameter),
          new EthGetCode(blockchainQueries, parameter),
          new EthGetLogs(blockchainQueries, parameter),
          new EthGetProof(blockchainQueries, parameter),
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
          new EthSendTransaction(),
          new EthEstimateGas(
              blockchainQueries,
              new TransactionSimulator(
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
          new DebugAccountRange(parameter, blockchainQueries),
          new DebugStorageRangeAt(parameter, blockchainQueries, blockReplay),
          new DebugMetrics(metricsSystem),
          new DebugTraceBlock(
              parameter,
              new BlockTracer(blockReplay),
              ScheduleBasedBlockHeaderFunctions.create(protocolSchedule),
              blockchainQueries),
          new DebugTraceBlockByNumber(parameter, new BlockTracer(blockReplay), blockchainQueries),
          new DebugTraceBlockByHash(parameter, new BlockTracer(blockReplay)));
    }
    if (rpcApis.contains(RpcApis.NET)) {
      addMethods(
          enabledMethods,
          new NetVersion(protocolSchedule.getChainId()),
          new NetListening(p2pNetwork),
          new NetPeerCount(p2pNetwork),
          new NetEnode(p2pNetwork),
          new NetServices(
              jsonRpcConfiguration, webSocketConfiguration, p2pNetwork, metricsConfiguration));
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
    if (rpcApis.contains(RpcApis.TX_POOL)) {
      addMethods(
          enabledMethods,
          new TxPoolBesuTransactions(transactionPool.getPendingTransactions()),
          new TxPoolBesuStatistics(transactionPool.getPendingTransactions()));
    }
    if (rpcApis.contains(RpcApis.PERM)) {
      addMethods(
          enabledMethods,
          new PermAddNodesToWhitelist(nodeWhitelistController, parameter),
          new PermRemoveNodesFromWhitelist(nodeWhitelistController, parameter),
          new PermGetNodesWhitelist(nodeWhitelistController),
          new PermGetAccountsWhitelist(accountsWhitelistController),
          new PermAddAccountsToWhitelist(accountsWhitelistController, parameter),
          new PermRemoveAccountsFromWhitelist(accountsWhitelistController, parameter),
          new PermReloadPermissionsFromFile(accountsWhitelistController, nodeWhitelistController));
    }
    if (rpcApis.contains(RpcApis.ADMIN)) {
      addMethods(
          enabledMethods,
          new AdminAddPeer(p2pNetwork, parameter),
          new AdminRemovePeer(p2pNetwork, parameter),
          new AdminNodeInfo(
              clientVersion, networkId, genesisConfigOptions, p2pNetwork, blockchainQueries),
          new AdminPeers(p2pNetwork),
          new AdminChangeLogLevel(parameter));
    }

    if (rpcApis.contains(RpcApis.TRACE)) {
      addMethods(
          enabledMethods,
          new TraceReplayBlockTransactions(
              parameter,
              new BlockTracer(
                  new BlockReplay(
                      protocolSchedule,
                      blockchainQueries.getBlockchain(),
                      blockchainQueries.getWorldStateArchive())),
              blockchainQueries,
              protocolSchedule));
    }

    final boolean eea = rpcApis.contains(RpcApis.EEA);
    final boolean priv = rpcApis.contains(RpcApis.PRIV);
    if (eea || priv) {
      final PrivateMarkerTransactionFactory markerTransactionFactory =
          createPrivateMarkerTransactionFactory(
              privacyParameters, blockchainQueries, transactionPool.getPendingTransactions());

      final PrivateTransactionHandler privateTransactionHandler =
          new PrivateTransactionHandler(
              privacyParameters, protocolSchedule.getChainId(), markerTransactionFactory);
      final Enclave enclave = new Enclave(privacyParameters.getEnclaveUri());
      if (eea) {
        addMethods(
            enabledMethods,
            new EeaGetTransactionReceipt(blockchainQueries, enclave, parameter, privacyParameters),
            new EeaSendRawTransaction(privateTransactionHandler, transactionPool, parameter),
            new EeaGetTransactionCount(
                parameter, new EeaPrivateNonceProvider(enclave, privateTransactionHandler)));
      }
      if (priv) {
        addMethods(
            enabledMethods,
            new PrivCreatePrivacyGroup(
                new Enclave(privacyParameters.getEnclaveUri()), privacyParameters, parameter),
            new PrivDeletePrivacyGroup(
                new Enclave(privacyParameters.getEnclaveUri()), privacyParameters, parameter),
            new PrivFindPrivacyGroup(new Enclave(privacyParameters.getEnclaveUri()), parameter),
            new PrivGetPrivacyPrecompileAddress(privacyParameters),
            new PrivGetTransactionCount(parameter, privateTransactionHandler),
            new PrivGetPrivateTransaction(
                blockchainQueries, enclave, parameter, privacyParameters));
      }
    }

    return enabledMethods;
  }

  public static void addMethods(
      final Map<String, JsonRpcMethod> methods, final JsonRpcMethod... rpcMethods) {
    for (final JsonRpcMethod rpcMethod : rpcMethods) {
      methods.put(rpcMethod.getName(), rpcMethod);
    }
  }

  private PrivateMarkerTransactionFactory createPrivateMarkerTransactionFactory(
      final PrivacyParameters privacyParameters,
      final BlockchainQueries blockchainQueries,
      final PendingTransactions pendingTransactions) {

    final Address privateContractAddress =
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress());

    if (privacyParameters.getSigningKeyPair().isPresent()) {
      return new FixedKeySigningPrivateMarkerTransactionFactory(
          privateContractAddress,
          new LatestNonceProvider(blockchainQueries, pendingTransactions),
          privacyParameters.getSigningKeyPair().get());
    }
    return new RandomSigningPrivateMarkerTransactionFactory(privateContractAddress);
  }
}
