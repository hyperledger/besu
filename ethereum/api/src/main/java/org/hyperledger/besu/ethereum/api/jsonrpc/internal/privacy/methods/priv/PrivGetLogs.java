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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Deprecated(since = "24.12.0")
public class PrivGetLogs implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final PrivacyQueries privacyQueries;
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public PrivGetLogs(
      final BlockchainQueries blockchainQueries,
      final PrivacyQueries privacyQueries,
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider) {
    this.blockchainQueries = blockchainQueries;
    this.privacyQueries = privacyQueries;
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_LOGS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String privacyGroupId;
    try {
      privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    } catch (Exception e) {
      throw new InvalidJsonRpcParameters(
          "Invalid privacy group ID parameter (index 0)",
          RpcErrorType.INVALID_PRIVACY_GROUP_PARAMS,
          e);
    }
    final FilterParameter filter;
    try {
      filter = requestContext.getRequiredParameter(1, FilterParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid filter parameter (index 1)", RpcErrorType.INVALID_FILTER_PARAMS, e);
    }

    if (!filter.isValid()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_FILTER_PARAMS);
    }

    final List<LogWithMetadata> matchingLogs =
        filter
            .getBlockHash()
            .map(
                blockHash ->
                    findLogsForBlockHash(requestContext, privacyGroupId, filter, blockHash))
            .orElseGet(() -> findLogsForBlockRange(requestContext, privacyGroupId, filter));

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), new LogsResult(matchingLogs));
  }

  private List<LogWithMetadata> findLogsForBlockRange(
      final JsonRpcRequestContext requestContext,
      final String privacyGroupId,
      final FilterParameter filter) {

    if (privacyController instanceof MultiTenancyPrivacyController) {
      checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
          requestContext, privacyGroupId, Optional.empty());
    }

    final long fromBlockNumber = filter.getFromBlock().getNumber().orElse(0L);
    final long toBlockNumber =
        filter.getToBlock().getNumber().orElse(blockchainQueries.headBlockNumber());

    return privacyQueries.matchingLogs(
        privacyGroupId, fromBlockNumber, toBlockNumber, filter.getLogsQuery());
  }

  private List<LogWithMetadata> findLogsForBlockHash(
      final JsonRpcRequestContext requestContext,
      final String privacyGroupId,
      final FilterParameter filter,
      final Hash blockHash) {
    final Optional<BlockHeader> blockHeader = blockchainQueries.getBlockHeaderByHash(blockHash);
    if (blockHeader.isEmpty()) {
      return Collections.emptyList();
    }
    final long blockNumber = blockHeader.get().getNumber();

    if (privacyController instanceof MultiTenancyPrivacyController) {
      checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
          requestContext, privacyGroupId, Optional.of(Long.valueOf(blockNumber)));
    }

    return privacyQueries.matchingLogs(privacyGroupId, blockHash, filter.getLogsQuery());
  }

  private void checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
      final JsonRpcRequestContext request,
      final String privacyGroupId,
      final Optional<Long> toBlock) {
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, toBlock);
  }
}
