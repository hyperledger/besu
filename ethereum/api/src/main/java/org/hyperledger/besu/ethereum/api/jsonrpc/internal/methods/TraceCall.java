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

import com.google.common.base.Suppliers;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class TraceCall implements JsonRpcMethod {
  protected final Supplier<BlockchainQueries> blockchainQueries;
  private final TransactionSimulator transactionSimulator;

  public TraceCall(final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_CALL.getMethodName() : null;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final TraceTypeParameter traceTypeParameter = requestContext.getRequiredParameter(1, TraceTypeParameter.class);
    final Optional<BlockParameter> maybeBlockParameter = requestContext.getOptionalParameter(2, BlockParameter.class);

    final Optional<BlockHeader> maybeBlockHeader = resolveBlockHeader(maybeBlockParameter);

    if (maybeBlockHeader.isEmpty()) {
      throw new IllegalStateException("Invalid block parameter.");
    }

    ByteArrayOutputStream traceOutput = new ByteArrayOutputStream();
    transactionSimulator.process(
        validateAndGetCallParams(requestContext),
        buildTransactionValidationParams(),
        new StandardJsonTracer(traceOutput, true),
        maybeBlockHeader.get()
    );

    //FlatTraceGenerator

    return null;
  }

  private TransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .build();

  }

  private Optional<BlockHeader> resolveBlockHeader(final Optional<BlockParameter> maybeBlockParameter) {
    AtomicLong blockNumber = new AtomicLong();

    maybeBlockParameter.ifPresentOrElse(
        blockParameter -> {
          if (blockParameter.isNumeric()) {
            blockNumber.set(blockParameter.getNumber().get());
          }
          else if (blockParameter.isEarliest()) {
            blockNumber.set(0);
          } else if (blockParameter.isPending() || blockParameter.isLatest()) {
            blockNumber.set(blockchainQueries.get().headBlockNumber());
          } else {
            throw new IllegalStateException("Unknown block parameter type.");
          }
        },
        () -> blockNumber.set(blockchainQueries.get().headBlockNumber())
    );

    return blockchainQueries.get().getBlockHeaderByNumber(blockNumber.get());
  }

  private JsonCallParameter validateAndGetCallParams(final JsonRpcRequestContext request) {
    final JsonCallParameter callParams = request.getRequiredParameter(0, JsonCallParameter.class);
    if (callParams.getTo() == null) {
      throw new InvalidJsonRpcParameters("Missing \"to\" field in call arguments");
    }
    if (callParams.getGasPrice() != null
        && (callParams.getMaxFeePerGas().isPresent()
        || callParams.getMaxPriorityFeePerGas().isPresent())) {
      throw new InvalidJsonRpcParameters(
          "gasPrice cannot be used with maxFeePerGas or maxPriorityFeePerGas");
    }
    return callParams;
  }
}
