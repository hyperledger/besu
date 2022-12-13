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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CreateAccessListResult;
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
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.evm.tracing.AccessListOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class EthCreateAccessList implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final TransactionSimulator transactionSimulator;

  public EthCreateAccessList(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = blockchainQueries;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_CREATE_ACCESS_LIST.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final JsonCallParameter callParams = validateAndGetCallParams(requestContext);

    final BlockHeader blockHeader = blockHeader();
    final Optional<JsonRpcError> jsonRpcError = validateBlockHeader(blockHeader);
    if (jsonRpcError.isPresent()) {
      return errorResponse(requestContext, jsonRpcError.get());
    }

    final TransactionValidationParams transactionValidationParams =
        transactionValidationParams(!callParams.isMaybeStrict().orElse(Boolean.FALSE));

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    final AccessListOperationTracer accessListOperationTracer = new AccessListOperationTracer();
    final Optional<TransactionSimulatorResult> maybeResult =
        transactionSimulator.process(
            modifiedCallParams,
            transactionValidationParams,
            accessListOperationTracer,
            blockHeader.getNumber());

    if (accessListOperationTracer.getAccessList().isEmpty()) {
      return maybeResult
          .map(createAccessListResponse(requestContext, accessListOperationTracer))
          .orElse(errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR));
    } else {
      final AccessListOperationTracer tracer = new AccessListOperationTracer();
      final CallParameter callParameter =
          overrideAccessList(modifiedCallParams, accessListOperationTracer.getAccessList());
      return transactionSimulator
          .process(callParameter, transactionValidationParams, tracer, blockHeader.getNumber())
          .map(createAccessListResponse(requestContext, tracer))
          .orElse(errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR));
    }
  }

  private Optional<JsonRpcError> validateBlockHeader(final BlockHeader blockHeader) {
    if (blockHeader == null) {
      return Optional.of(JsonRpcError.INTERNAL_ERROR);
    }
    if (!blockchainQueries
        .getWorldStateArchive()
        .isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getHash())) {
      return Optional.of(JsonRpcError.WORLD_STATE_UNAVAILABLE);
    }
    return Optional.empty();
  }

  private BlockHeader blockHeader() {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return blockchainQueries.getBlockchain().getBlockHeader(headBlockNumber).orElse(null);
  }

  private TransactionValidationParams transactionValidationParams(final boolean strict) {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .isAllowExceedingBalance(strict)
        .build();
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
        callParams.getPayload(),
        callParams.getAccessList());
  }

  private CallParameter overrideAccessList(
      final CallParameter callParams, final List<AccessListEntry> accessListEntries) {
    return new CallParameter(
        callParams.getFrom(),
        callParams.getTo(),
        callParams.getGasLimit(),
        callParams.getGasPrice(),
        callParams.getMaxPriorityFeePerGas(),
        callParams.getMaxFeePerGas(),
        callParams.getValue(),
        callParams.getPayload(),
        Optional.ofNullable(accessListEntries));
  }

  private Function<TransactionSimulatorResult, JsonRpcResponse> createAccessListResponse(
      final JsonRpcRequestContext request, final AccessListOperationTracer operationTracer) {
    return result ->
        result.isSuccessful()
            ? new JsonRpcSuccessResponse(
                request.getRequest().getId(),
                new CreateAccessListResult(
                    operationTracer.getAccessList(),
                    operationTracer.calculateEstimateGas(
                        result.getResult().getEstimateGasUsedByTransaction())))
            : errorResponse(request, result);
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

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
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
}
