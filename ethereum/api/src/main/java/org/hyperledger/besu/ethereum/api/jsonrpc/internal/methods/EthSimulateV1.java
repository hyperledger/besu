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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockStateCallsParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonBlockStateCall;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionHashResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.BlockSimulationResult;
import org.hyperledger.besu.ethereum.transaction.BlockSimulator;

import java.util.List;
import java.util.stream.Collectors;

public class EthSimulateV1 extends AbstractBlockParameterOrBlockHashMethod {
  private final BlockSimulator blockSimulator;

  public EthSimulateV1(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final long rpcGasCap,
      final MiningConfiguration miningConfiguration) {
    super(blockchainQueries);
    this.blockSimulator =
        new BlockSimulator(
            blockchainQueries.getBlockchain(),
            blockchainQueries.getWorldStateArchive(),
            protocolSchedule,
            rpcGasCap,
            miningConfiguration);
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

      var blockStateCalls = getBlockStateCalls(request);
      var simulationResults = blockSimulator.process(header, blockStateCalls);

      var jsonResponse = createResponse(simulationResults, request);
      return new JsonRpcSuccessResponse(request.getRequest().getId(), jsonResponse);
    } catch (JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
  }

  private List<JsonBlockStateCall> getBlockStateCalls(final JsonRpcRequestContext request)
      throws JsonRpcParameterException {
    BlockStateCallsParameter parameter =
        request.getRequiredParameter(0, BlockStateCallsParameter.class);
    return parameter.getBlockStateCalls();
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), new JsonRpcError(rpcErrorType));
  }

  private JsonRpcSuccessResponse createResponse(
      final List<BlockSimulationResult> simulationResult, final JsonRpcRequestContext request) {
    var response =
        simulationResult.stream()
            .map(
                result -> {
                  Block block = result.getBlock();
                  var txs =
                      block.getBody().getTransactions().stream().map(Transaction::getHash).toList();
                  var transactionResults =
                      result.getTransactionSimulations().stream()
                          .map(this::createTransactionProcessingResult)
                          .toList();
                  List<TransactionResult> transactionHashes =
                      txs.stream()
                          .map(Hash::toString)
                          .map(TransactionHashResult::new)
                          .collect(Collectors.toList());
                  return new BlockResult(
                      block.getHeader(),
                      transactionHashes,
                      List.of(),
                      transactionResults,
                      Difficulty.ZERO,
                      block.calculateSize(),
                      false,
                      block.getBody().getWithdrawals());
                })
            .toList();
    return new JsonRpcSuccessResponse(request.getRequest().getId(), response);
  }

  private TransactionProcessingResult createTransactionProcessingResult(
      final org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult simulatorResult) {
    return new TransactionProcessingResult(
        simulatorResult.result().isSuccessful() ? 1 : 0,
        simulatorResult.result().getOutput(),
        simulatorResult.result().getGasRemaining(),
        null, // TODO ADD ERROR
        null);// TODO ADD LOG
  }
}
