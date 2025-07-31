/*
 * Copyright contributors to Besu.
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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.SimulateV1Parameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockStateCallResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.BlockSimulationResult;
import org.hyperledger.besu.ethereum.transaction.BlockSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.exceptions.BlockStateCallError;
import org.hyperledger.besu.ethereum.transaction.exceptions.BlockStateCallException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EthSimulateV1 extends AbstractBlockParameterOrBlockHashMethod {

  private final BlockSimulator blockSimulator;
  private final ProtocolSchedule protocolSchedule;

  public EthSimulateV1(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator,
      final MiningConfiguration miningConfiguration,
      final ApiConfiguration apiConfiguration) {
    super(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
    this.blockSimulator =
        new BlockSimulator(
            blockchainQueries.getWorldStateArchive(),
            protocolSchedule,
            transactionSimulator,
            miningConfiguration,
            blockchainQueries.getBlockchain(),
            apiConfiguration.getGasCap());
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SIMULATE_V1.getMethodName();
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
    return blockchainQueries
        .get()
        .getBlockHeaderByHash(blockHash)
        .map(header -> resultByBlockHeader(request, header))
        .orElseGet(() -> errorResponse(request, BLOCK_NOT_FOUND));
  }

  @Override
  protected Object resultByBlockHeader(
      final JsonRpcRequestContext request, final BlockHeader header) {
    try {
      SimulateV1Parameter simulateV1Parameter = getBlockStateCalls(request);
      Optional<BlockStateCallError> maybeValidationError =
          simulateV1Parameter.validate(getValidPrecompileAddresses(header));
      if (maybeValidationError.isPresent()) {
        JsonRpcError error =
            new JsonRpcError(
                maybeValidationError.get().getCode(),
                maybeValidationError.get().getMessage(),
                null);
        return new JsonRpcErrorResponse(request.getRequest().getId(), error);
      }
      return process(header, simulateV1Parameter);
    } catch (final BlockStateCallException e) {
      return handleBlockSimulationException(request, e);
    } catch (final JsonRpcParameterException e) {
      return errorResponse(request, INVALID_PARAMS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object process(final BlockHeader header, final SimulateV1Parameter simulateV1Parameter) {
    final List<BlockSimulationResult> simulationResults =
        blockSimulator.process(header, simulateV1Parameter);
    return simulationResults.stream()
        .map(
            result ->
                BlockStateCallResult.create(result, simulateV1Parameter.isReturnFullTransactions()))
        .collect(Collectors.toList());
  }

  private JsonRpcErrorResponse handleBlockSimulationException(
      final JsonRpcRequestContext request, final BlockStateCallException e) {
    JsonRpcError error =
        e.getResult()
            .map(
                r -> {
                  BlockStateCallError blockStateCallError =
                      BlockStateCallError.of(r.getValidationResult().getInvalidReason());
                  String message = r.getInvalidReason().orElse(blockStateCallError.getMessage());
                  return new JsonRpcError(blockStateCallError.getCode(), message, null);
                })
            .orElseGet(() -> new JsonRpcError(RpcErrorType.INTERNAL_ERROR));

    return new JsonRpcErrorResponse(request.getRequest().getId(), error);
  }

  private Set<Address> getValidPrecompileAddresses(final BlockHeader header) {
    return protocolSchedule
        .getByBlockHeader(header)
        .getPrecompileContractRegistry()
        .getPrecompileAddresses();
  }

  private SimulateV1Parameter getBlockStateCalls(final JsonRpcRequestContext request)
      throws JsonRpcParameterException {
    return request.getRequiredParameter(0, SimulateV1Parameter.class);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), new JsonRpcError(rpcErrorType));
  }
}
