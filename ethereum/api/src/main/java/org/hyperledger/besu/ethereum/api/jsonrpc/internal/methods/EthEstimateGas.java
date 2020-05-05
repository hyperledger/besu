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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.Optional;

import org.apache.logging.log4j.Logger;

public class EthEstimateGas implements JsonRpcMethod {

  private static final int GAS_ESTIMATE_CHANGE_DENOMINATOR = 10;
  private static final int MAX_ESTIMATE_NUMBER_OF_RETRY = 20;

  private static final Logger LOG = getLogger();

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

    final JsonCallParameter callParams =
        requestContext.getRequiredParameter(0, JsonCallParameter.class);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
    }
    return doEstimateGas(requestContext.getRequest(), callParams, blockHeader);
  }

  /**
   * Allows to estimate gas for a transaction
   *
   * @param request json rpc request
   * @param callParams call params
   * @param blockHeader block header
   * @return json rpc response with the estimate gas of the transaction, otherwise the error
   */
  private JsonRpcResponse doEstimateGas(
      final JsonRpcRequest request,
      final JsonCallParameter callParams,
      final BlockHeader blockHeader) {

    long lowGasLimit, midGasLimit, highGasLimit;
    int numberOfRetry = 0;
    boolean foundEstimateGas;

    JsonCallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    Optional<TransactionSimulatorResult> simulatorResult = Optional.empty();

    try {

      // make a first estimate of the necessary gasLimit
      simulatorResult = transactionSimulator.process(modifiedCallParams, blockHeader.getNumber());
      lowGasLimit =
          highGasLimit =
              simulatorResult
                  .filter(TransactionSimulatorResult::isSuccessful)
                  .map(TransactionSimulatorResult::getGasEstimate)
                  .orElseThrow();

      // check that the estimate is valid and if not we increase the estimate by
      // GAS_ESTIMATE_CHANGE_DENOMINATOR. if after MAX_ESTIMATE_NUMBER_OF_RETRY retries you cannot
      // find a valid estimate. we return the error returned by the transaction
      do {

        modifiedCallParams.setGasLimit(highGasLimit);
        simulatorResult = transactionSimulator.process(modifiedCallParams, blockHeader.getNumber());
        foundEstimateGas =
            simulatorResult.filter(TransactionSimulatorResult::isSuccessful).isPresent();

        if (!foundEstimateGas) {
          lowGasLimit = modifiedCallParams.getGasLimit();
          highGasLimit = lowGasLimit + lowGasLimit / GAS_ESTIMATE_CHANGE_DENOMINATOR;
        }

        // unable to find a good estimate we send the error returned by the transaction
        if (numberOfRetry++ > MAX_ESTIMATE_NUMBER_OF_RETRY) {
          throw new RuntimeException("Unable to find a good estimate");
        }

      } while (!foundEstimateGas);

      // performs binary search to find the most accurate estimate
      while (lowGasLimit + 1 < highGasLimit) {

        midGasLimit = (highGasLimit + lowGasLimit) / 2;

        modifiedCallParams.setGasLimit(midGasLimit);
        simulatorResult = transactionSimulator.process(modifiedCallParams, blockHeader.getNumber());
        foundEstimateGas =
            simulatorResult.filter(TransactionSimulatorResult::isSuccessful).isPresent();

        if (!foundEstimateGas) {
          lowGasLimit = midGasLimit;
        } else {
          highGasLimit = midGasLimit;
        }
      }
      return new JsonRpcSuccessResponse(request.getId(), Quantity.create(highGasLimit));
    } catch (Exception e) {
      LOG.error("Error while executing the transaction");
    }
    return errorResponse(request, simulatorResult);
  }

  private BlockHeader blockHeader() {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return blockchainQueries.getBlockchain().getBlockHeader(headBlockNumber).orElse(null);
  }

  private JsonCallParameter overrideGasLimitAndPrice(
      final CallParameter callParams, final long gasLimit) {
    return new JsonCallParameter(
        callParams.getFrom() != null ? callParams.getFrom().toString() : null,
        callParams.getTo() != null ? callParams.getTo().toString() : null,
        Quantity.create(gasLimit),
        Quantity.create(0L),
        callParams.getValue() != null ? Quantity.create(callParams.getValue()) : null,
        callParams.getPayload() != null ? callParams.getPayload().toString() : null);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequest request, final Optional<TransactionSimulatorResult> simulatorResult) {

    JsonRpcError jsonRpcError;
    try {
      jsonRpcError =
          JsonRpcErrorConverter.convertTransactionInvalidReason(
              simulatorResult
                  .map(TransactionSimulatorResult::getValidationResult)
                  .map(ValidationResult::getInvalidReason)
                  .orElseThrow());
    } catch (Exception e) {
      jsonRpcError = JsonRpcError.INTERNAL_ERROR;
    }
    return new JsonRpcErrorResponse(request.getId(), jsonRpcError);
  }
}
