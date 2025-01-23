/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonCallParameterUtil.validateAndGetCallParams;

import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public abstract class AbstractEstimateGas extends AbstractBlockParameterMethod {

  private static final double SUB_CALL_REMAINING_GAS_RATIO = 65D / 64D;

  protected final TransactionSimulator transactionSimulator;

  public AbstractEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    super(blockchainQueries);
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    try {
      return request.getOptionalParameter(1, BlockParameter.class).orElse(BlockParameter.LATEST);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 1)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  protected abstract Object simulate(
      final JsonRpcRequestContext requestContext,
      final CallParameter callParams,
      final long gasLimit,
      final TransactionSimulationFunction simulationFunction);

  @Override
  protected Object pendingResult(final JsonRpcRequestContext requestContext) {
    final JsonCallParameter jsonCallParameter = validateAndGetCallParams(requestContext);
    final var validationParams = getTransactionValidationParams(jsonCallParameter);
    final var maybeStateOverrides = getAddressStateOverrideMap(requestContext);
    final var pendingBlockHeader = transactionSimulator.simulatePendingBlockHeader();
    final TransactionSimulationFunction simulationFunction =
        (cp, op) ->
            transactionSimulator.processOnPending(
                cp, maybeStateOverrides, validationParams, op, pendingBlockHeader);
    return simulate(
        requestContext, jsonCallParameter, pendingBlockHeader.getGasLimit(), simulationFunction);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext requestContext, final long blockNumber) {
    final JsonCallParameter jsonCallParameter = validateAndGetCallParams(requestContext);
    final Optional<BlockHeader> maybeBlockHeader = blockHeader(blockNumber);
    final Optional<RpcErrorType> jsonRpcError = validateBlockHeader(maybeBlockHeader);
    if (jsonRpcError.isPresent()) {
      return errorResponse(requestContext, jsonRpcError.get());
    }
    return resultByBlockHeader(requestContext, jsonCallParameter, maybeBlockHeader.get());
  }

  private Object resultByBlockHeader(
      final JsonRpcRequestContext requestContext,
      final JsonCallParameter jsonCallParameter,
      final BlockHeader blockHeader) {
    final var validationParams = getTransactionValidationParams(jsonCallParameter);
    final var maybeStateOverrides = getAddressStateOverrideMap(requestContext);
    final TransactionSimulationFunction simulationFunction =
        (cp, op) ->
            transactionSimulator.process(
                cp, maybeStateOverrides, validationParams, op, blockHeader);
    return simulate(
        requestContext, jsonCallParameter, blockHeader.getGasLimit(), simulationFunction);
  }

  private Optional<BlockHeader> blockHeader(final long blockNumber) {
    if (getBlockchainQueries().headBlockNumber() == blockNumber) {
      // chain head header if cached, and we can return it form memory
      return Optional.of(getBlockchainQueries().getBlockchain().getChainHeadHeader());
    }
    return getBlockchainQueries().getBlockHeaderByNumber(blockNumber);
  }

  private Optional<RpcErrorType> validateBlockHeader(final Optional<BlockHeader> maybeBlockHeader) {
    if (maybeBlockHeader.isEmpty()) {
      return Optional.of(RpcErrorType.BLOCK_NOT_FOUND);
    }

    final var blockHeader = maybeBlockHeader.get();
    if (!getBlockchainQueries()
        .getWorldStateArchive()
        .isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getHash())) {
      return Optional.of(RpcErrorType.WORLD_STATE_UNAVAILABLE);
    }
    return Optional.empty();
  }

  protected CallParameter overrideGasLimit(final CallParameter callParams, final long gasLimit) {
    return new CallParameter(
        callParams.getChainId(),
        callParams.getFrom(),
        callParams.getTo(),
        gasLimit,
        Optional.ofNullable(callParams.getGasPrice()).orElse(Wei.ZERO),
        callParams.getMaxPriorityFeePerGas(),
        callParams.getMaxFeePerGas(),
        callParams.getValue(),
        callParams.getPayload(),
        callParams.getAccessList(),
        callParams.getMaxFeePerBlobGas(),
        callParams.getBlobVersionedHashes(),
        callParams.getNonce());
  }

  /**
   * Estimate gas by adding minimum gas remaining for some operation and the necessary gas for sub
   * calls
   *
   * @param result transaction simulator result
   * @param operationTracer estimate gas operation tracer
   * @return estimate gas
   */
  protected long processEstimateGas(
      final TransactionSimulatorResult result, final EstimateGasOperationTracer operationTracer) {
    // no more than 63/64s of the remaining gas can be passed to the sub calls
    final double subCallMultiplier =
        Math.pow(SUB_CALL_REMAINING_GAS_RATIO, operationTracer.getMaxDepth());
    // and minimum gas remaining is necessary for some operation (additionalStipend)
    final long gasStipend = operationTracer.getStipendNeeded();
    final long gasUsedByTransaction = result.result().getEstimateGasUsedByTransaction();
    return ((long) ((gasUsedByTransaction + gasStipend) * subCallMultiplier));
  }

  protected JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final TransactionSimulatorResult result) {

    final ValidationResult<TransactionInvalidReason> validationResult =
        result.getValidationResult();
    if (validationResult != null && !validationResult.isValid()) {
      if (!validationResult.getErrorMessage().isEmpty()) {
        return errorResponse(request, JsonRpcError.from(validationResult));
      }
      return errorResponse(
          request,
          JsonRpcErrorConverter.convertTransactionInvalidReason(
              validationResult.getInvalidReason()));
    } else {
      final TransactionProcessingResult resultTrx = result.result();
      if (resultTrx != null && resultTrx.getRevertReason().isPresent()) {
        return errorResponse(
            request,
            new JsonRpcError(
                RpcErrorType.REVERT_ERROR, resultTrx.getRevertReason().get().toHexString()));
      }
      return errorResponse(request, RpcErrorType.INTERNAL_ERROR);
    }
  }

  protected JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return errorResponse(request, new JsonRpcError(rpcErrorType));
  }

  protected JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
  }

  protected static TransactionValidationParams getTransactionValidationParams(
      final JsonCallParameter callParams) {
    final boolean isAllowExceedingBalance = !callParams.isMaybeStrict().orElse(Boolean.FALSE);

    return isAllowExceedingBalance
        ? TransactionValidationParams.transactionSimulatorAllowExceedingBalance()
        : TransactionValidationParams.transactionSimulator();
  }

  @VisibleForTesting
  protected Optional<StateOverrideMap> getAddressStateOverrideMap(
      final JsonRpcRequestContext request) {
    try {
      return request.getOptionalParameter(2, StateOverrideMap.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcRequestException(
          "Invalid account overrides parameter (index 2)", RpcErrorType.INVALID_CALL_PARAMS, e);
    }
  }

  protected interface TransactionSimulationFunction {
    Optional<TransactionSimulatorResult> simulate(
        CallParameter callParams, OperationTracer operationTracer);
  }
}
