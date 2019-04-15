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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.debug.TraceOptions;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.TransactionTraceParams;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockTrace;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.BlockTracer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.DebugTraceTransactionResult;
import tech.pegasys.pantheon.ethereum.vm.DebugOperationTracer;

import java.util.Collection;
import java.util.Optional;

public class DebugTraceBlockByNumber implements JsonRpcMethod {

  private final JsonRpcParameter parameters;
  private final BlockTracer blockTracer;
  private final BlockchainQueries blockchain;

  public DebugTraceBlockByNumber(
      final JsonRpcParameter parameters,
      final BlockTracer blockTracer,
      final BlockchainQueries blockchain) {
    this.parameters = parameters;
    this.blockTracer = blockTracer;
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return "debug_traceBlockByNumber";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Long blockNumber = parameters.required(request.getParams(), 0, Long.class);
    final Optional<Hash> blockHash = this.blockchain.getBlockHashByNumber(blockNumber);
    final TraceOptions traceOptions =
        parameters
            .optional(request.getParams(), 1, TransactionTraceParams.class)
            .map(TransactionTraceParams::traceOptions)
            .orElse(TraceOptions.DEFAULT);

    final Collection<DebugTraceTransactionResult> results =
        blockHash
            .map(
                hash ->
                    blockTracer
                        .trace(hash, new DebugOperationTracer(traceOptions))
                        .map(BlockTrace::getTransactionTraces)
                        .map(DebugTraceTransactionResult::of))
            .orElse(null)
            .get();
    return new JsonRpcSuccessResponse(request.getId(), results);
  }
}
