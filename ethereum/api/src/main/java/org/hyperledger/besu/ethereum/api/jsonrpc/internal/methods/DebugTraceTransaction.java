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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;

public class DebugTraceTransaction implements JsonRpcMethod {

  private final TransactionTracer transactionTracer;
  private final BlockchainQueries blockchain;

  public DebugTraceTransaction(
      final BlockchainQueries blockchain, final TransactionTracer transactionTracer) {
    this.blockchain = blockchain;
    this.transactionTracer = transactionTracer;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final Optional<TransactionWithMetadata> transactionWithMetadata =
        blockchain.transactionByHash(hash);
    if (transactionWithMetadata.isPresent()) {
      final TraceOptions traceOptions =
          requestContext
              .getOptionalParameter(1, TransactionTraceParams.class)
              .map(TransactionTraceParams::traceOptions)
              .orElse(TraceOptions.DEFAULT);
      final DebugTraceTransactionResult debugTraceTransactionResult =
          debugTraceTransactionResult(hash, transactionWithMetadata.get(), traceOptions);

      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), debugTraceTransactionResult);
    } else {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
  }

  private DebugTraceTransactionResult debugTraceTransactionResult(
      final Hash hash,
      final TransactionWithMetadata transactionWithMetadata,
      final TraceOptions traceOptions) {
    final Hash blockHash = transactionWithMetadata.getBlockHash().get();

    final DebugOperationTracer execTracer = new DebugOperationTracer(traceOptions);

    return transactionTracer
        .traceTransaction(blockHash, hash, execTracer)
        .map(DebugTraceTransactionResult::new)
        .orElse(null);
  }
}
