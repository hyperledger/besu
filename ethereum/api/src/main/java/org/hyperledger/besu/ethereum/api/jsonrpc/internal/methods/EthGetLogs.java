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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.List;

public class EthGetLogs implements JsonRpcMethod {

  private final BlockchainQueries blockchain;

  public EthGetLogs(final BlockchainQueries blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_LOGS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final FilterParameter filter = requestContext.getRequiredParameter(0, FilterParameter.class);

    if (!filter.isValid()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final List<LogWithMetadata> matchingLogs =
        filter
            .getBlockHash()
            .map(
                blockHash ->
                    blockchain.matchingLogs(
                        blockHash, filter.getLogsQuery(), requestContext::isAlive))
            .orElseGet(
                () -> {
                  final long fromBlockNumber = filter.getFromBlock().getNumber().orElse(0L);
                  final long toBlockNumber =
                      filter.getToBlock().getNumber().orElse(blockchain.headBlockNumber());
                  return blockchain.matchingLogs(
                      fromBlockNumber,
                      toBlockNumber,
                      filter.getLogsQuery(),
                      requestContext::isAlive);
                });

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), new LogsResult(matchingLogs));
  }
}
