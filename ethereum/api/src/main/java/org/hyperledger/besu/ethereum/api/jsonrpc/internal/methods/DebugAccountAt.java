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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableDebugAccountAtResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.account.Account;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class DebugAccountAt extends AbstractBlockParameterOrBlockHashMethod {
  private final Supplier<BlockTracer> blockTracerSupplier;

  public DebugAccountAt(
      final BlockchainQueries blockchainQueries, final Supplier<BlockTracer> blockTracerSupplier) {
    super(blockchainQueries);
    this.blockTracerSupplier = blockTracerSupplier;
  }

  public DebugAccountAt(
      final Supplier<BlockchainQueries> blockchainQueries,
      final Supplier<BlockTracer> blockTracerSupplier) {
    super(blockchainQueries);
    this.blockTracerSupplier = blockTracerSupplier;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_ACCOUNT_AT.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext requestContext) {
    return requestContext.getRequiredParameter(0, BlockParameterOrBlockHash.class);
  }

  @Override
  protected Object resultByBlockHash(
      final JsonRpcRequestContext requestContext, final Hash blockHash) {
    final Integer txIndex = requestContext.getRequiredParameter(1, Integer.class);
    final Address address = requestContext.getRequiredParameter(2, Address.class);

    Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> block =
        blockchainQueries.get().blockByHash(blockHash);
    if (block.isEmpty()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.BLOCK_NOT_FOUND);
    }

    List<TransactionWithMetadata> transactions = block.get().getTransactions();
    if (transactions.isEmpty() || txIndex < 0 || txIndex > block.get().getTransactions().size()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Optional<TransactionTrace> transactionTrace =
        blockTracerSupplier
            .get()
            .trace(blockHash, new DebugOperationTracer(new TraceOptions(false, true, true)))
            .map(BlockTrace::getTransactionTraces)
            .orElse(Collections.emptyList())
            .stream()
            .filter(
                trxTrace ->
                    trxTrace
                        .getTransaction()
                        .getHash()
                        .equals(transactions.get(txIndex).getTransaction().getHash()))
            .findFirst();

    if (transactionTrace.isEmpty()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.TRANSACTION_NOT_FOUND);
    }

    Optional<Account> account =
        transactionTrace.get().getTraceFrames().stream()
            .map(traceFrame -> traceFrame.getWorldUpdater().get(address))
            .filter(Objects::nonNull)
            .filter(a -> a.getAddress().equals(address))
            .findFirst();
    if (account.isEmpty()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.NO_ACCOUNT_FOUND);
    }

    return debugAccountAtResult(
        account.get().getCode(),
        Quantity.create(account.get().getNonce()),
        Quantity.create(account.get().getBalance()),
        Quantity.create(account.get().getCodeHash()));
  }

  protected ImmutableDebugAccountAtResult debugAccountAtResult(
      final Bytes code, final String nonce, final String balance, final String codeHash) {
    return ImmutableDebugAccountAtResult.builder()
        .code(code.isEmpty() ? "0x0" : code.toHexString())
        .nonce(nonce)
        .balance(balance)
        .codehash(codeHash)
        .build();
  }
}
