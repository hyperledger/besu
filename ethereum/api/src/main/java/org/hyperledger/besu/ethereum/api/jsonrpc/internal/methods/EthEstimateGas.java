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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.util.AccountOverrideMap;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthEstimateGas extends AbstractEstimateGas {
  private static final Logger LOG = LoggerFactory.getLogger(EthEstimateGas.class);

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, transactionSimulator);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  protected Object resultByBlockHeader(
      final JsonRpcRequestContext requestContext,
      final JsonCallParameter callParams,
      final BlockHeader blockHeader) {

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());
    Optional<AccountOverrideMap> maybeStateOverrides = getAddressAccountOverrideMap(requestContext);
    // TODO implement for block overrides

    final boolean isAllowExceedingBalance = !callParams.isMaybeStrict().orElse(Boolean.FALSE);

    final EstimateGasOperationTracer operationTracer = new EstimateGasOperationTracer();
    final var transactionValidationParams =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .isAllowExceedingBalance(isAllowExceedingBalance)
            .build();

    LOG.debug("Processing transaction with params: {}", modifiedCallParams);
    final var maybeResult =
        transactionSimulator.process(
            modifiedCallParams,
            maybeStateOverrides,
            transactionValidationParams,
            operationTracer,
            blockHeader);

    final Optional<JsonRpcErrorResponse> maybeErrorResponse =
        validateSimulationResult(requestContext, maybeResult);
    if (maybeErrorResponse.isPresent()) {
      return maybeErrorResponse.get();
    }

    final var result = maybeResult.get();
    long low = result.result().getEstimateGasUsedByTransaction();
    final var lowResult =
        transactionSimulator.process(
            overrideGasLimitAndPrice(callParams, low),
            maybeStateOverrides,
            transactionValidationParams,
            operationTracer,
            blockHeader);

    if (lowResult.isPresent() && lowResult.get().isSuccessful()) {
      return Quantity.create(low);
    }

    long high = processEstimateGas(result, operationTracer);
    long mid;

    while (low + 1 < high) {
      mid = (low + high) / 2;
      var binarySearchResult =
          transactionSimulator.process(
              overrideGasLimitAndPrice(callParams, mid),
              maybeStateOverrides,
              transactionValidationParams,
              operationTracer,
              blockHeader);

      if (binarySearchResult.isEmpty() || !binarySearchResult.get().isSuccessful()) {
        low = mid;
      } else {
        high = mid;
      }
    }

    return Quantity.create(high);
  }

  private Optional<JsonRpcErrorResponse> validateSimulationResult(
      final JsonRpcRequestContext requestContext,
      final Optional<TransactionSimulatorResult> maybeResult) {
    if (maybeResult.isEmpty()) {
      LOG.error("No result after simulating transaction.");
      return Optional.of(
          new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), RpcErrorType.INTERNAL_ERROR));
    }

    // if the transaction is invalid or doesn't have enough gas with the max it never will!
    if (maybeResult.get().isInvalid() || !maybeResult.get().isSuccessful()) {
      return Optional.of(errorResponse(requestContext, maybeResult.get()));
    }
    return Optional.empty();
  }

  @VisibleForTesting
  protected Optional<AccountOverrideMap> getAddressAccountOverrideMap(
      final JsonRpcRequestContext request) {
    try {
      return request.getOptionalParameter(2, AccountOverrideMap.class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcRequestException(
          "Invalid account overrides parameter (index 2)", RpcErrorType.INVALID_CALL_PARAMS, e);
    }
  }
}
