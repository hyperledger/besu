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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer.TRACE_PATH;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;

public class DebugStandardTraceBlockToFile implements JsonRpcMethod {

  protected final Supplier<BlockchainQueries> blockchainQueries;
  private final Supplier<TransactionTracer> transactionTracerSupplier;
  private final Path dataDir;

  public DebugStandardTraceBlockToFile(
      final Supplier<TransactionTracer> transactionTracerSupplier,
      final BlockchainQueries blockchainQueries,
      final Path dataDir) {
    this.transactionTracerSupplier = transactionTracerSupplier;
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    this.dataDir = dataDir;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_STANDARD_TRACE_BLOCK_TO_FILE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash blockHash = requestContext.getRequiredParameter(0, Hash.class);
    final Optional<TransactionTraceParams> transactionTraceParams =
        requestContext.getOptionalParameter(1, TransactionTraceParams.class);

    return blockchainQueries
        .get()
        .getBlockchain()
        .getBlockByHash(blockHash)
        .map(
            block ->
                (JsonRpcResponse)
                    new JsonRpcSuccessResponse(
                        requestContext.getRequest().getId(),
                        traceBlock(block, transactionTraceParams)))
        .orElse(
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(), JsonRpcError.BLOCK_NOT_FOUND));
  }

  protected List<String> traceBlock(
      final Block block, final Optional<TransactionTraceParams> transactionTraceParams) {
    return transactionTracerSupplier
        .get()
        .traceTransactionToFile(
            block.getHash(), transactionTraceParams, dataDir.resolve(TRACE_PATH));
  }

  protected Object emptyResult() {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.createArrayNode();
  }
}
