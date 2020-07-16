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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionLogBloomCacher;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AdminLogsRepairCache implements JsonRpcMethod {
  private static final String STATUS = "Status";

  private final P2PNetwork peerNetwork;
  private final BlockchainQueries blockchainQueries;

  public AdminLogsRepairCache(
      final P2PNetwork peerNetwork, final BlockchainQueries blockchainQueries) {
    this.peerNetwork = peerNetwork;
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_LOGS_REPAIR_CACHE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (!peerNetwork.isP2pEnabled()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.P2P_DISABLED);
    }

    final Optional<EnodeURL> maybeEnode = peerNetwork.getLocalEnode();
    if (maybeEnode.isEmpty()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.P2P_NETWORK_NOT_RUNNING);
    }

    final Optional<Long> blockNumber = requestContext.getOptionalParameter(0, Long.class);

    blockNumber.ifPresent(
        bn ->
            blockchainQueries
                .getBlockchain()
                .getBlockByNumber(bn)
                .orElseThrow(() -> new IllegalStateException("Block not found")));

    final TransactionLogBloomCacher transactionLogBloomCacher =
        blockchainQueries.getTransactionLogBloomCacher().orElseThrow();

    transactionLogBloomCacher.ensurePreviousSegmentsArePresent(
        blockNumber.orElse(blockchainQueries.headBlockNumber()), true);

    final Map<String, Object> response = new HashMap<>();
    response.put(STATUS, "Started");

    if (transactionLogBloomCacher.getCachingStatus().isCaching()) {
      response.put(STATUS, "Already running");
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
