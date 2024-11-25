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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.BlockSimulator;

import java.util.ArrayList;
import java.util.List;

public class EthSimulateV1 extends AbstractBlockParameterOrBlockHashMethod {
  private final BlockSimulator blockSimulator;
  private final BlockResultFactory blockResultFactory;

  public EthSimulateV1(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final long rpcGasCap,
      final MiningConfiguration miningConfiguration,
      final BlockResultFactory blockResultFactory) {
    super(blockchainQueries);
    this.blockResultFactory = blockResultFactory;

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

      List<BlockResult> results = new ArrayList<>();
      for (var blockStateCall : parameter.getBlockStateCalls()) {
        var result = blockSimulator.simulate(header, blockStateCall);

        results.add(
            result
                .map(
                    blockSimulationResult ->
                        blockResultFactory.transactionHash(
                            blockByHashWithTxHashes(blockSimulationResult.getBlock())))
                .orElse(null));
      }
      return new JsonRpcSuccessResponse(request.getRequest().getId(), results);
    } catch (JsonRpcParameterException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return errorResponse(request, new JsonRpcError(rpcErrorType));
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), jsonRpcError);
  }

  public BlockWithMetadata<Hash, Hash> blockByHashWithTxHashes(final Block block) {
    final int size = block.calculateSize();

    final List<Hash> txs =
        block.getBody().getTransactions().stream().map(Transaction::getHash).toList();

    final List<Hash> ommers =
        block.getBody().getOmmers().stream().map(BlockHeader::getHash).toList();

    return new BlockWithMetadata<>(
        block.getHeader(), txs, ommers, Difficulty.ZERO, size, block.getBody().getWithdrawals());
  }
}
