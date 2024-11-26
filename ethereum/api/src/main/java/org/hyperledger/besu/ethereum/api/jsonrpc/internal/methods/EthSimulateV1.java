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
            miningConfiguration::getCoinbase,
            miningConfiguration::getTargetGasLimit);
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
    final BlockHeader header = blockchainQueries.get().getBlockHeaderByHash(blockHash).orElse(null);

    if (header == null) {
      return errorResponse(request, BLOCK_NOT_FOUND);
    }
    return resultByBlockHeader(request, header);
  }

  @Override
  protected Object resultByBlockHeader(
      final JsonRpcRequestContext request, final BlockHeader header) {
    try {
      BlockStateCallsParameter parameter =
          request.getRequiredParameter(0, BlockStateCallsParameter.class);

      var response =
          blockSimulator.process(header, parameter.getBlockStateCalls()).stream()
              .map(this::mapResponse);

      return new JsonRpcSuccessResponse(request.getRequest().getId(), response);
    } catch (JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), new JsonRpcError(rpcErrorType));
  }

  public BlockResult mapResponse(final BlockSimulationResult result) {
    Block block = result.getBlock();
    final int size = block.calculateSize();

    final List<Hash> txs =
        block.getBody().getTransactions().stream().map(Transaction::getHash).toList();

    var transactionResults =
        result.getTransactionSimulations().stream()
            .map(
                r ->
                    new TransactionProcessingResult(
                        r.result().isSuccessful() ? 1 : 0,
                        r.result().getOutput(),
                        r.result().getGasRemaining(),
                        null,
                        null))
            .toList();

    final List<TransactionResult> transactionHashes =
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
        size,
        false,
        block.getBody().getWithdrawals());
  }
}
