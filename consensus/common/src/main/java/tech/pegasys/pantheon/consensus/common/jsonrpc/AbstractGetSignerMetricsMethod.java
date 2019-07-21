/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.common.jsonrpc;

import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.SignerMetricResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

public abstract class AbstractGetSignerMetricsMethod {

  private static final long DEFAULT_RANGE_BLOCK = 100;

  private final BlockInterface blockInterface;
  private final BlockchainQueries blockchainQueries;
  private final JsonRpcParameter parameters;

  public AbstractGetSignerMetricsMethod(
      final BlockInterface blockInterface,
      final BlockchainQueries blockchainQueries,
      final JsonRpcParameter parameter) {
    this.blockInterface = blockInterface;
    this.blockchainQueries = blockchainQueries;
    this.parameters = parameter;
  }

  public JsonRpcResponse response(final JsonRpcRequest request) {

    final Optional<BlockParameter> startBlockParameter =
        parameters.optional(request.getParams(), 0, BlockParameter.class);
    final Optional<BlockParameter> endBlockParameter =
        parameters.optional(request.getParams(), 1, BlockParameter.class);

    final long fromBlockNumber = getFromBlockNumber(startBlockParameter);
    final long toBlockNumber = getEndBlockNumber(endBlockParameter);

    if (!isValidParameters(fromBlockNumber, toBlockNumber)) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Map<Address, SignerMetricResult> proposersMap = new HashMap<>();
    final long lastBlockIndex = toBlockNumber - 1;

    // go through each block (startBlock is inclusive and endBlock is exclusive)
    LongStream.range(fromBlockNumber, toBlockNumber)
        .forEach(
            currentIndex -> {
              final Optional<BlockHeader> blockHeaderByNumber =
                  blockchainQueries.getBlockHeaderByNumber(currentIndex);

              // Get the number of blocks from each proposer in a given block range.
              blockHeaderByNumber.ifPresent(
                  header -> {
                    final Address proposerAddress = blockInterface.getProposerOfBlock(header);
                    final SignerMetricResult signerMetric =
                        proposersMap.computeIfAbsent(proposerAddress, SignerMetricResult::new);
                    signerMetric.incrementeNbBlock();
                    // Add the block number of the last block proposed by each validator
                    signerMetric.setLastProposedBlockNumber(currentIndex);

                    // Get All validators present in the last block of the range even
                    // if they didn't propose a block
                    if (currentIndex == lastBlockIndex) {
                      blockInterface
                          .validatorsInBlock(header)
                          .forEach(
                              address ->
                                  proposersMap.computeIfAbsent(
                                      proposerAddress, SignerMetricResult::new));
                    }
                  });
            });

    return new JsonRpcSuccessResponse(request.getId(), new ArrayList<>(proposersMap.values()));
  }

  private long getFromBlockNumber(final Optional<BlockParameter> startBlockParameter) {
    return startBlockParameter
        .map(this::resolveBlockNumber)
        .orElseGet(() -> Math.max(0, blockchainQueries.headBlockNumber() - DEFAULT_RANGE_BLOCK));
  }

  private long getEndBlockNumber(final Optional<BlockParameter> endBlockParameter) {
    return endBlockParameter
        .map(this::resolveBlockNumber)
        .orElseGet(blockchainQueries::headBlockNumber);
  }

  private boolean isValidParameters(final long startBlock, final long endBlock) {
    return startBlock < endBlock;
  }

  private long resolveBlockNumber(final BlockParameter param) {
    if (param.getNumber().isPresent()) {
      return param.getNumber().getAsLong();
    } else if (param.isEarliest()) {
      return BlockHeader.GENESIS_BLOCK_NUMBER;
    } else if (param.isLatest() || param.isPending()) {
      return blockchainQueries.headBlockNumber();
    } else {
      throw new IllegalStateException("Unknown block parameter type.");
    }
  }
}
