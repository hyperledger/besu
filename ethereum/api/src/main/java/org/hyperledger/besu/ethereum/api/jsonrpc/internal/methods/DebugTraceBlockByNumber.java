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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;
import java.util.function.Supplier;

public class DebugTraceBlockByNumber extends AbstractBlockParameterMethod {

  private final Supplier<BlockTracer> blockTracerSupplier;

  public DebugTraceBlockByNumber(
      final Supplier<BlockTracer> blockTracerSupplier, final BlockchainQueries blockchain) {
    super(blockchain);
    this.blockTracerSupplier = blockTracerSupplier;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_BLOCK_BY_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final Optional<Hash> blockHash = getBlockchainQueries().getBlockHashByNumber(blockNumber);
    final TraceOptions traceOptions =
        request
            .getOptionalParameter(1, TransactionTraceParams.class)
            .map(TransactionTraceParams::traceOptions)
            .orElse(TraceOptions.DEFAULT);

    return blockHash
        .flatMap(
            hash ->
                blockTracerSupplier
                    .get()
                    .trace(hash, new DebugOperationTracer(traceOptions))
                    .map(BlockTrace::getTransactionTraces)
                    .map(DebugTraceTransactionResult::of))
        .orElse(null);
  }
}
