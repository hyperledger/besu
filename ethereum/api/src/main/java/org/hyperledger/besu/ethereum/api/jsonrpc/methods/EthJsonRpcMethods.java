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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSubmitWork;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSyncing;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthUninstallFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

import java.util.Map;
import java.util.Set;

public class EthJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockResultFactory blockResult = new BlockResultFactory();
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  private final BlockchainQueries blockchainQueries;
  private final Synchronizer synchronizer;
  private final ProtocolSchedule<?> protocolSchedule;
  private final FilterManager filterManager;
  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final Set<Capability> supportedCapabilities;

  public EthJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule<?> protocolSchedule,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Set<Capability> supportedCapabilities) {
    this.blockchainQueries = blockchainQueries;
    this.synchronizer = synchronizer;
    this.protocolSchedule = protocolSchedule;
    this.filterManager = filterManager;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.supportedCapabilities = supportedCapabilities;
  }

  @Override
  protected RpcApi getApiGroup() {
    return RpcApis.ETH;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    return mapOf(
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
        new EthSubmitWork(miningCoordinator, parameter),
        new EthHashrate(miningCoordinator),
        new EthChainId(protocolSchedule.getChainId()));
  }
}
