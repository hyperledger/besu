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
package org.hyperledger.besu.ethereum.retesteth;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.LivenessCheck;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugAccountRange;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugStorageRangeAt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthBlockNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBalance;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetBlockByNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetCode;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthSendRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.Web3ClientVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.retesteth.methods.TestGetLogHash;
import org.hyperledger.besu.ethereum.retesteth.methods.TestImportRawBlock;
import org.hyperledger.besu.ethereum.retesteth.methods.TestMineBlocks;
import org.hyperledger.besu.ethereum.retesteth.methods.TestModifyTimestamp;
import org.hyperledger.besu.ethereum.retesteth.methods.TestRewindToBlock;
import org.hyperledger.besu.ethereum.retesteth.methods.TestSetChainParams;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;

public class RetestethService {

  private final JsonRpcHttpService jsonRpcHttpService;
  private final Vertx vertx;

  private final RetestethContext retestethContext;

  public RetestethService(
      final String clientVersion,
      final RetestethConfiguration retestethConfiguration,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    vertx = Vertx.vertx();
    retestethContext = new RetestethContext();

    final JsonRpcParameter parameters = new JsonRpcParameter();
    final BlockResultFactory blockResult = new BlockResultFactory();
    final Map<String, JsonRpcMethod> jsonRpcMethods = new HashMap<>();
    JsonRpcMethodsFactory.addMethods(
        jsonRpcMethods,
        new Web3ClientVersion(clientVersion),
        new TestSetChainParams(retestethContext),
        new TestImportRawBlock(retestethContext, parameters),
        new EthBlockNumber(retestethContext::getBlockchainQueries, true),
        new EthGetBlockByNumber(
            retestethContext::getBlockchainQueries, blockResult, parameters, true),
        new DebugAccountRange(parameters, retestethContext::getBlockchainQueries),
        new EthGetBalance(retestethContext::getBlockchainQueries, parameters),
        new EthGetCode(retestethContext::getBlockchainQueries, parameters),
        new EthGetTransactionCount(
            retestethContext::getBlockchainQueries,
            retestethContext::getPendingTransactions,
            parameters,
            true),
        new DebugStorageRangeAt(
            parameters,
            retestethContext::getBlockchainQueries,
            retestethContext::getBlockReplay,
            true),
        new TestModifyTimestamp(retestethContext, parameters),
        new EthSendRawTransaction(retestethContext::getTransactionPool, parameters, true),
        new TestMineBlocks(retestethContext, parameters),
        new TestGetLogHash(retestethContext, parameters),
        new TestRewindToBlock(retestethContext, parameters));

    jsonRpcHttpService =
        new JsonRpcHttpService(
            vertx,
            retestethConfiguration.getDataPath(),
            jsonRpcConfiguration,
            new NoOpMetricsSystem(),
            Optional.empty(),
            jsonRpcMethods,
            new HealthService(new LivenessCheck()),
            HealthService.ALWAYS_HEALTHY);
  }

  public void start() {
    jsonRpcHttpService.start();
  }

  public void close() {
    stop();
  }

  public void stop() {
    jsonRpcHttpService.stop();
  }
}
