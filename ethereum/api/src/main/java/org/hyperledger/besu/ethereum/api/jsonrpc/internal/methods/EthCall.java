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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.BLOCK_NOT_FOUND;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

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
    return request.getRequiredParameter(1, BlockParameterOrBlockHash.class);
  }

  @Override
  protected Object resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    JsonCallParameter callParams = JsonCallParameterUtil.validateAndGetCallParams(request);
    final BlockHeader header = blockchainQueries.get().getBlockHeaderByHash(blockHash).orElse(null);

    if (header == null) {
      return errorResponse(request, BLOCK_NOT_FOUND);
    }

    return transactionSimulator
        .process(
            callParams,
            buildTransactionValidationParams(header, callParams),
            OperationTracer.NO_TRACING,
            header)
        .map(
            result ->
                result
                    .getValidationResult()
                    .either(
                        (() ->
                            result.isSuccessful()
                                ? new JsonRpcSuccessResponse(
                                    request.getRequest().getId(), result.getOutput().toString())
                                : errorResponse(request, result)),
                        reason ->
                            new JsonRpcErrorResponse(
                                request.getRequest().getId(),
                                JsonRpcErrorConverter.convertTransactionInvalidReason(reason))))
        .orElse(errorResponse(request, INTERNAL_ERROR));
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) handleParamTypes(requestContext);
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

  private TransactionValidationParams buildTransactionValidationParams(
      final BlockHeader header, final JsonCallParameter callParams) {

    ImmutableTransactionValidationParams.Builder transactionValidationParams =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator());

    // if it is not set explicitly whether we want a strict check of the balance or not. this will
    // be decided according to the provided parameters
    if (callParams.isMaybeStrict().isEmpty()) {
      transactionValidationParams.isAllowExceedingBalance(
          isAllowExeedingBalanceAutoSelection(header, callParams));
    } else {
      transactionValidationParams.isAllowExceedingBalance(
          !callParams.isMaybeStrict().orElse(Boolean.FALSE));
    }
    return transactionValidationParams.build();
  }

  private boolean isAllowExeedingBalanceAutoSelection(
      final BlockHeader header, final JsonCallParameter callParams) {

    boolean isZeroGasPrice =
        callParams.getGasPrice() == null || Wei.ZERO.equals(callParams.getGasPrice());

    if (header.getBaseFee().isPresent()) {
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
