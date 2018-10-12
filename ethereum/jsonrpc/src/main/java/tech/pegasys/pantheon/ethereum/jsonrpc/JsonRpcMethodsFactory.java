package net.consensys.pantheon.ethereum.jsonrpc;

import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.AdminPeers;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.DebugStorageRangeAt;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.DebugTraceTransaction;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthAccounts;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthBlockNumber;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthCall;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthCoinbase;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthEstimateGas;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGasPrice;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBalance;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockByHash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockByNumber;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByHash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetBlockTransactionCountByNumber;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetCode;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterChanges;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetFilterLogs;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetLogs;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetStorageAt;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockHashAndIndex;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByBlockNumberAndIndex;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionByHash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionCount;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetTransactionReceipt;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleByBlockHashAndIndex;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleByBlockNumberAndIndex;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleCountByBlockHash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthGetUncleCountByBlockNumber;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthMining;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthNewBlockFilter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthNewFilter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthNewPendingTransactionFilter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthProtocolVersion;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthSendRawTransaction;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthSyncing;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.EthUninstallFilter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.NetListening;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.NetPeerCount;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.NetVersion;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.Web3ClientVersion;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.Web3Sha3;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerSetCoinbase;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerSetEtherbase;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerStart;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner.MinerStop;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.BlockReplay;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTracer;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JsonRpcMethodsFactory {

  private final BlockResultFactory blockResult = new BlockResultFactory();
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final String chainId,
      final P2PNetwork peerNetworkingService,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final ProtocolSchedule<?> protocolSchedule,
      final AbstractMiningCoordinator<?, ?> miningCoordinator,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApis> rpcApis) {
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    final FilterManager filterManager =
        new FilterManager(blockchainQueries, transactionPool, new FilterIdGenerator());
    return methods(
        clientVersion,
        chainId,
        peerNetworkingService,
        blockchainQueries,
        synchronizer,
        protocolSchedule,
        filterManager,
        transactionPool,
        miningCoordinator,
        supportedCapabilities,
        rpcApis);
  }

  public Map<String, JsonRpcMethod> methods(
      final String clientVersion,
      final String chainId,
      final P2PNetwork p2pNetwork,
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule<?> protocolSchedule,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final AbstractMiningCoordinator<?, ?> miningCoordinator,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApis> rpcApis) {
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
          new EthMining<>(miningCoordinator),
          new EthCoinbase(miningCoordinator),
          new EthProtocolVersion(supportedCapabilities),
          new EthGasPrice<>(miningCoordinator));
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
          new DebugStorageRangeAt(parameter, blockchainQueries, blockReplay));
    }
    if (rpcApis.contains(RpcApis.NET)) {
      addMethods(
          enabledMethods,
          new NetVersion(chainId),
          new NetListening(p2pNetwork),
          new NetPeerCount(p2pNetwork),
          new AdminPeers(p2pNetwork));
    }
    if (rpcApis.contains(RpcApis.WEB3)) {
      addMethods(enabledMethods, new Web3ClientVersion(clientVersion), new Web3Sha3());
    }
    if (rpcApis.contains(RpcApis.MINER)) {
      final MinerSetCoinbase minerSetCoinbase = new MinerSetCoinbase(miningCoordinator, parameter);
      addMethods(
          enabledMethods,
          new MinerStart<>(miningCoordinator),
          new MinerStop<>(miningCoordinator),
          minerSetCoinbase,
          new MinerSetEtherbase(minerSetCoinbase));
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
