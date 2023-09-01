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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthGetLogs implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(EthGetLogs.class);

  private final BlockchainQueries blockchain;
  private final Optional<Long> maxLogRange;

  public EthGetLogs(final BlockchainQueries blockchain, final Optional<Long> maxLogRange) {
    this.blockchain = blockchain;
    this.maxLogRange = maxLogRange;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_LOGS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final FilterParameter filter = requestContext.getRequiredParameter(0, FilterParameter.class);
    LOG.atTrace().setMessage("eth_getLogs FilterParameter: {}").addArgument(filter).log();

    if (!filter.isValid()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PARAMS);
    }

    final AtomicReference<Exception> ex = new AtomicReference<>();
    final List<LogWithMetadata> matchingLogs =
        filter
            .getBlockHash()
            .map(
                blockHash ->
                    blockchain.matchingLogs(
                        blockHash, filter.getLogsQuery(), requestContext::isAlive))
            .orElseGet(
                () -> {
                  final long fromBlockNumber;
                  final long toBlockNumber;
                  try {
                    fromBlockNumber =
                        filter
                            .getFromBlock()
                            .getBlockNumber(blockchain)
                            .orElseThrow(
                                () ->
                                    new Exception("fromBlock not found: " + filter.getFromBlock()));
                    toBlockNumber =
                        filter
                            .getToBlock()
                            .getBlockNumber(blockchain)
                            .orElseThrow(
                                () -> new Exception("toBlock not found: " + filter.getToBlock()));
                    if (maxLogRange.isPresent()
                        && (toBlockNumber - fromBlockNumber) > maxLogRange.get()) {
                      throw new IllegalArgumentException(
                          "Requested range exceeds maximum range limit");
                    }
                  } catch (final Exception e) {
                    ex.set(e);
                    return Collections.emptyList();
                  }

                  return blockchain.matchingLogs(
                      fromBlockNumber,
                      toBlockNumber,
                      filter.getLogsQuery(),
                      requestContext::isAlive);
                });

    if (ex.get() != null) {
      LOG.atDebug()
          .setMessage("eth_getLogs request {} failed:")
          .addArgument(requestContext.getRequest())
          .setCause(ex.get())
          .log();
      if (ex.get() instanceof IllegalArgumentException) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), RpcErrorType.EXCEEDS_RPC_MAX_BLOCK_RANGE);
      }
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PARAMS);
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), new LogsResult(matchingLogs));
  }
}
