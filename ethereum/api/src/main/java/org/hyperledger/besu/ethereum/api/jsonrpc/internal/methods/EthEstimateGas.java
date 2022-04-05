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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import java.util.Optional;
import java.util.function.Function;

public class EthEstimateGas implements JsonRpcMethod {

  private static final double SUB_CALL_REMAINING_GAS_RATIO = 65D / 64D;

  private final BlockchainQueries blockchainQueries;
  private final TransactionSimulator transactionSimulator;

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = blockchainQueries;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final JsonCallParameter callParams = validateAndGetCallParams(requestContext);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      return errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR);
    }
    if (!blockchainQueries
        .getWorldStateArchive()
        .isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getHash())) {
      return errorResponse(requestContext, JsonRpcError.WORLD_STATE_UNAVAILABLE);
    }

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    final EstimateGasOperationTracer operationTracer = new EstimateGasOperationTracer();

    return transactionSimulator
        .process(
            modifiedCallParams,
            ImmutableTransactionValidationParams.builder()
                .from(TransactionValidationParams.transactionSimulator())
                .isAllowExceedingBalance(!callParams.isMaybeStrict().orElse(Boolean.FALSE))
                .build(),
            operationTracer,
            blockHeader.getNumber())
        .map(gasEstimateResponse(requestContext, operationTracer))
        .orElse(errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR));
  }

  private BlockHeader blockHeader() {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return blockchainQueries.getBlockchain().getBlockHeader(headBlockNumber).orElse(null);
  }

  private CallParameter overrideGasLimitAndPrice(
      final JsonCallParameter callParams, final long gasLimit) {
    return new CallParameter(
        callParams.getFrom(),
        callParams.getTo(),
        gasLimit,
        Optional.ofNullable(callParams.getGasPrice()).orElse(Wei.ZERO),
        callParams.getMaxPriorityFeePerGas(),
        callParams.getMaxFeePerGas(),
        callParams.getValue(),
        callParams.getPayload());
  }

  private Function<TransactionSimulatorResult, JsonRpcResponse> gasEstimateResponse(
      final JsonRpcRequestContext request, final EstimateGasOperationTracer operationTracer) {
    return result ->
        result.isSuccessful()
            ? new JsonRpcSuccessResponse(
                request.getRequest().getId(),
                Quantity.create(processEstimateGas(result, operationTracer)))
            : errorResponse(request, result);
  }

  /**
   * Estimate gas by adding minimum gas remaining for some operation and the necessary gas for sub
   * calls
   *
   * @param result transaction simulator result
   * @param operationTracer estimate gas operation tracer
   * @return estimate gas
   */
  private long processEstimateGas(
      final TransactionSimulatorResult result, final EstimateGasOperationTracer operationTracer) {
    // no more than 63/64s of the remaining gas can be passed to the sub calls
    final double subCallMultiplier =
        Math.pow(SUB_CALL_REMAINING_GAS_RATIO, operationTracer.getMaxDepth());
    // and minimum gas remaining is necessary for some operation (additionalStipend)
    final long gasStipend = operationTracer.getStipendNeeded();
    final long gasUsedByTransaction = result.getResult().getEstimateGasUsedByTransaction();
    return ((long) ((gasUsedByTransaction + gasStipend) * subCallMultiplier));
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final TransactionSimulatorResult result) {
    final JsonRpcError jsonRpcError;

    final ValidationResult<TransactionInvalidReason> validationResult =
        result.getValidationResult();
    if (validationResult != null && !validationResult.isValid()) {
      jsonRpcError =
          JsonRpcErrorConverter.convertTransactionInvalidReason(
              validationResult.getInvalidReason());
    } else {
      final TransactionProcessingResult resultTrx = result.getResult();
      if (resultTrx != null && resultTrx.getRevertReason().isPresent()) {
        jsonRpcError = JsonRpcError.REVERT_ERROR;
        jsonRpcError.setData(resultTrx.getRevertReason().get().toHexString());
      } else {
        jsonRpcError = JsonRpcError.INTERNAL_ERROR;
      }
    }
    return errorResponse(request, jsonRpcError);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
  }

  private JsonCallParameter validateAndGetCallParams(final JsonRpcRequestContext request) {
    final JsonCallParameter callParams = request.getRequiredParameter(0, JsonCallParameter.class);
    if (callParams.getGasPrice() != null
        && (callParams.getMaxFeePerGas().isPresent()
            || callParams.getMaxPriorityFeePerGas().isPresent())) {
      throw new InvalidJsonRpcParameters("gasPrice cannot be used with baseFee or maxFeePerGas");
    }
    return callParams;
  }
}
