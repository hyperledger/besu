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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import java.util.Optional;

public abstract class AbstractEstimateGas implements JsonRpcMethod {

  private static final double SUB_CALL_REMAINING_GAS_RATIO = 65D / 64D;

  protected final BlockchainQueries blockchainQueries;
  protected final TransactionSimulator transactionSimulator;

  public AbstractEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = blockchainQueries;
    this.transactionSimulator = transactionSimulator;
  }

  protected BlockHeader blockHeader() {
    final Blockchain theChain = blockchainQueries.getBlockchain();

    // Optimistically get the block header for the chain head without taking a lock,
    // but revert to the safe implementation if it returns an empty optional. (It's
    // possible the chain head has been updated but the block is still being persisted
    // to storage/cache under the lock).
    return theChain
        .getBlockHeader(theChain.getChainHeadHash())
        .or(() -> theChain.getBlockHeaderSafe(theChain.getChainHeadHash()))
        .orElse(null);
  }

  protected CallParameter overrideGasLimitAndPrice(
      final JsonCallParameter callParams, final long gasLimit) {
    return new CallParameter(
        callParams.getFrom(),
        callParams.getTo(),
        gasLimit,
        Optional.ofNullable(callParams.getGasPrice()).orElse(Wei.ZERO),
        callParams.getMaxPriorityFeePerGas(),
        callParams.getMaxFeePerGas(),
        callParams.getValue(),
        callParams.getPayload(),
        callParams.getAccessList());
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
    final long gasUsedByTransaction = result.getResult().getEstimateGasUsedByTransaction();
    return ((long) ((gasUsedByTransaction + gasStipend) * subCallMultiplier));
  }

  protected JsonCallParameter validateAndGetCallParams(final JsonRpcRequestContext request) {
    final JsonCallParameter callParams = request.getRequiredParameter(0, JsonCallParameter.class);
    if (callParams.getGasPrice() != null
        && (callParams.getMaxFeePerGas().isPresent()
            || callParams.getMaxPriorityFeePerGas().isPresent())) {
      throw new InvalidJsonRpcParameters("gasPrice cannot be used with baseFee or maxFeePerGas");
    }
    return callParams;
  }

  protected JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final TransactionSimulatorResult result) {

    final ValidationResult<TransactionInvalidReason> validationResult =
        result.getValidationResult();
    if (validationResult != null && !validationResult.isValid()) {
      if (validationResult.getErrorMessage().length() > 0) {
        final RpcErrorType rpcErrorType =
            JsonRpcErrorConverter.convertTransactionInvalidReason(
                validationResult.getInvalidReason());
        final JsonRpcError rpcError = new JsonRpcError(rpcErrorType);
        rpcError.setReason(validationResult.getErrorMessage());
        return errorResponse(request, rpcError);
      }
      return errorResponse(
          request,
          JsonRpcErrorConverter.convertTransactionInvalidReason(
              validationResult.getInvalidReason()));
    } else {
      final TransactionProcessingResult resultTrx = result.getResult();
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
}
