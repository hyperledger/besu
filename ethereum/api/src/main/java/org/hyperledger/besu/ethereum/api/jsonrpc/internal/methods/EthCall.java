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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.BLOCK_NOT_FOUND;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INTERNAL_ERROR;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class EthCall extends AbstractBlockParameterOrBlockHashMethod {
  private final TransactionSimulator transactionSimulator;

  public EthCall(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    super(blockchainQueries);
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_CALL.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(1, BlockParameterOrBlockHash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block or block hash parameters (index 1)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  protected Object resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    final BlockHeader header = blockchainQueries.get().getBlockHeaderByHash(blockHash).orElse(null);

    if (header == null) {
      return errorResponse(request, BLOCK_NOT_FOUND);
    }
    return resultByBlockHeader(request, header);
  }

  @Override
  protected Object resultByBlockHeader(
      final JsonRpcRequestContext request, final BlockHeader header) {
    JsonCallParameter callParams = JsonCallParameterUtil.validateAndGetCallParams(request);
    Optional<StateOverrideMap> maybeStateOverrides = getAddressStateOverrideMap(request);
    // TODO implement for block overrides

    return transactionSimulator
        .process(
            callParams,
            maybeStateOverrides,
            buildTransactionValidationParams(header, callParams),
            OperationTracer.NO_TRACING,
            (mutableWorldState, transactionSimulatorResult) ->
                transactionSimulatorResult.map(
                    result ->
                        result
                            .getValidationResult()
                            .either(
                                (() ->
                                    result.isSuccessful()
                                        ? new JsonRpcSuccessResponse(
                                            request.getRequest().getId(),
                                            result.getOutput().toString())
                                        : errorResponse(request, result)),
                                reason -> errorResponse(request, result))),
            header)
        .orElse(errorResponse(request, INTERNAL_ERROR));
  }

  @VisibleForTesting
  protected Optional<StateOverrideMap> getAddressStateOverrideMap(
      final JsonRpcRequestContext request) {
    try {
      return request.getOptionalParameter(2, StateOverrideMap.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcRequestException(
          "Invalid account overrides parameter (index 2)", RpcErrorType.INVALID_CALL_PARAMS, e);
    }
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) handleParamTypes(requestContext);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final TransactionSimulatorResult result) {
    final ValidationResult<TransactionInvalidReason> validationResult =
        result.getValidationResult();
    if (validationResult != null && !validationResult.isValid()) {
      return errorResponse(request, JsonRpcError.from(validationResult));
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

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return errorResponse(request, new JsonRpcError(rpcErrorType));
  }

  private TransactionValidationParams buildTransactionValidationParams(
      final BlockHeader header, final JsonCallParameter callParams) {

    final boolean isAllowExceedingBalance;
    // if it is not set explicitly whether we want a strict check of the balance or not. this will
    // be decided according to the provided parameters
    if (callParams.isMaybeStrict().isEmpty()) {
      isAllowExceedingBalance = isAllowExceedingBalanceAutoSelection(header, callParams);

    } else {
      isAllowExceedingBalance = !callParams.isMaybeStrict().orElse(Boolean.FALSE);
    }
    return isAllowExceedingBalance
        ? TransactionValidationParams.transactionSimulatorAllowExceedingBalance()
        : TransactionValidationParams.transactionSimulator();
  }

  private boolean isAllowExceedingBalanceAutoSelection(
      final BlockHeader header, final JsonCallParameter callParams) {

    boolean isZeroGasPrice =
        callParams.getGasPrice() == null || Wei.ZERO.equals(callParams.getGasPrice());

    if (header.getBaseFee().isPresent()) {
      if (callParams.getBlobVersionedHashes().isPresent()
          && (callParams.getMaxFeePerBlobGas().isEmpty()
              || callParams.getMaxFeePerBlobGas().get().equals(Wei.ZERO))) {
        return true;
      }
      boolean isZeroMaxFeePerGas = callParams.getMaxFeePerGas().orElse(Wei.ZERO).equals(Wei.ZERO);
      boolean isZeroMaxPriorityFeePerGas =
          callParams.getMaxPriorityFeePerGas().orElse(Wei.ZERO).equals(Wei.ZERO);
      if (isZeroGasPrice && isZeroMaxFeePerGas && isZeroMaxPriorityFeePerGas) {
        // After 1559, when gas pricing is not provided, 0 is used and the balance is not
        // checked
        return true;
      } else {
        // After 1559, when gas price is provided, it is interpreted as both the max and
        // priority fee and the balance is checked
        return false;
      }
    }
    // Prior 1559, when gas price == 0 or is not provided the balance is not checked
    return isZeroGasPrice;
  }
}
