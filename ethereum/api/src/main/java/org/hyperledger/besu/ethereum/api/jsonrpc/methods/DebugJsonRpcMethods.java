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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugAccountRange;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugMetrics;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugStorageRangeAt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlockByHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceBlockByNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.util.Map;

public class DebugJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final JsonRpcParameter parameter = new JsonRpcParameter();
  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule<?> protocolSchedule;
  private final ObservableMetricsSystem metricsSystem;

  public DebugJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final ObservableMetricsSystem metricsSystem) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.metricsSystem = metricsSystem;
  }

  @Override
  protected RpcApi getApiGroup() {
    return RpcApis.DEBUG;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final BlockReplay blockReplay =
        new BlockReplay(
            protocolSchedule,
            blockchainQueries.getBlockchain(),
            blockchainQueries.getWorldStateArchive());

    return mapOf(
        new DebugTraceTransaction(blockchainQueries, new TransactionTracer(blockReplay), parameter),
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
}
