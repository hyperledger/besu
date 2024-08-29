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
package org.hyperledger.besu.consensus.common.jsonrpc;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.SignerMetricResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

/** The Abstract get signer metrics method. */
public abstract class AbstractGetSignerMetricsMethod {

  private static final long DEFAULT_RANGE_BLOCK = 100;

  private final ValidatorProvider validatorProvider;
  private final BlockInterface blockInterface;
  private final BlockchainQueries blockchainQueries;

  /**
   * Instantiates a new Abstract get signer metrics method.
   *
   * @param validatorProvider the validator provider
   * @param blockInterface the block interface
   * @param blockchainQueries the blockchain queries
   */
  protected AbstractGetSignerMetricsMethod(
      final ValidatorProvider validatorProvider,
      final BlockInterface blockInterface,
      final BlockchainQueries blockchainQueries) {
    this.validatorProvider = validatorProvider;
    this.blockInterface = blockInterface;
    this.blockchainQueries = blockchainQueries;
  }

  /**
   * Response.
   *
   * @param requestContext the request context
   * @return the json rpc response
   */
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    final Optional<BlockParameter> startBlockParameter;
    try {
      startBlockParameter = requestContext.getOptionalParameter(0, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid start block parameter (index 0)", RpcErrorType.INVALID_BLOCK_NUMBER_PARAMS, e);
    }
    final Optional<BlockParameter> endBlockParameter;
    try {
      endBlockParameter = requestContext.getOptionalParameter(1, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid end block parameter (index 1)", RpcErrorType.INVALID_BLOCK_NUMBER_PARAMS, e);
    }

    final long fromBlockNumber = getFromBlockNumber(startBlockParameter);
    final long toBlockNumber = getEndBlockNumber(endBlockParameter);

    if (!isValidParameters(fromBlockNumber, toBlockNumber)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_BLOCK_NUMBER_PARAMS);
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
                      validatorProvider
                          .getValidatorsAfterBlock(header)
                          .forEach(
                              address ->
                                  proposersMap.computeIfAbsent(address, SignerMetricResult::new));
                    }
                  });
            });

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), new ArrayList<>(proposersMap.values()));
  }

  private long getFromBlockNumber(final Optional<BlockParameter> startBlockParameter) {
    return startBlockParameter
        .map(this::resolveBlockNumber)
        .orElseGet(() -> Math.max(0, blockchainQueries.headBlockNumber() - DEFAULT_RANGE_BLOCK));
  }

  private long getEndBlockNumber(final Optional<BlockParameter> endBlockParameter) {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return endBlockParameter
        .map(this::resolveBlockNumber)
        .filter(blockNumber -> blockNumber <= headBlockNumber)
        .orElse(headBlockNumber);
  }

  private boolean isValidParameters(final long startBlock, final long endBlock) {
    return startBlock < endBlock;
  }

  private long resolveBlockNumber(final BlockParameter param) {
    if (param.getNumber().isPresent()) {
      return param.getNumber().get();
    } else if (param.isEarliest()) {
      return BlockHeader.GENESIS_BLOCK_NUMBER;
    } else if (param.isLatest() || param.isPending()) {
      return blockchainQueries.headBlockNumber();
    } else {
      throw new IllegalStateException("Unknown block parameter type.");
    }
  }
}
