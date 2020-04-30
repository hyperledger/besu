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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;

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
    final FilterParameter filter;
    final LogsQuery query;
    try {
      filter = requestContext.getRequiredParameter(0, FilterParameter.class);
      query = new LogsQuery(filter.getAddresses(), filter.getTopics());
    } catch (InvalidJsonRpcParameters __) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    if (filter.getMaybeBlockHash().isPresent()) {
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new LogsResult(blockchain.matchingLogs(filter.getMaybeBlockHash().get(), query)));
    }

    final long fromBlockNumber = filter.getFromBlock().getNumber().orElse(0);
    final long toBlockNumber = filter.getToBlock().getNumber().orElse(blockchain.headBlockNumber());

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new LogsResult(blockchain.matchingLogs(fromBlockNumber, toBlockNumber, query)));
  }
}
