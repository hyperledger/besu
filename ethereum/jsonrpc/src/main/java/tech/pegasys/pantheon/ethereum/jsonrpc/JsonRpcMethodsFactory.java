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

import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminAddPeer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminChangeLogLevel;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminNodeInfo;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminPeers;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.AdminRemovePeer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugAccountRange;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugMetrics;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugStorageRangeAt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugTraceBlock;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugTraceBlockByHash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.DebugTraceBlockByNumber;
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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthGetProof;
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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthSendTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthSyncing;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.EthUninstallFilter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetEnode;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetListening;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetPeerCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetServices;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.NetVersion;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.RpcModules;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.TxPoolPantheonStatistics;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.TxPoolPantheonTransactions;
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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermReloadPermissionsFromFile;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromWhitelist;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea.EeaGetTransactionCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea.EeaGetTransactionReceipt;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea.EeaPrivateNonceProvider;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea.EeaSendRawTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivCreatePrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivDeletePrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivFindPrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivGetPrivacyPrecompileAddress;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivGetPrivateTransaction;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivGetTransactionCount;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockReplay;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockTracer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTracer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.permissioning.AccountLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import tech.pegasys.pantheon.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import tech.pegasys.pantheon.ethereum.privacy.markertransaction.RandomSigningPrivateMarkerTransactionFactory;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;

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
      final MetricsSystem metricsSystem,
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
      final MetricsSystem metricsSystem,
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
          new TxPoolPantheonTransactions(transactionPool.getPendingTransactions()),
          new TxPoolPantheonStatistics(transactionPool.getPendingTransactions()));
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
