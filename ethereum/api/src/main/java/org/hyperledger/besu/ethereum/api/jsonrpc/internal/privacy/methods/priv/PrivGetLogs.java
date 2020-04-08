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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

public class PrivGetLogs implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final PrivacyQueries privacyQueries;
  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public PrivGetLogs(
      final BlockchainQueries blockchainQueries,
      final PrivacyQueries privacyQueries,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.blockchainQueries = blockchainQueries;
    this.privacyQueries = privacyQueries;
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_LOGS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final String privacyGroupId = request.getRequiredParameter(0, String.class);
    final FilterParameter filter = request.getRequiredParameter(1, FilterParameter.class);

    checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(request, privacyGroupId);

    final LogsQuery query =
        new LogsQuery.Builder().addresses(filter.getAddresses()).topics(filter.getTopics()).build();

    if (!filter.isValid()) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    if (filter.getBlockhash() != null) {
      return new JsonRpcSuccessResponse(
          request.getRequest().getId(),
          new LogsResult(
              privacyQueries.matchingLogs(privacyGroupId, filter.getBlockhash(), query)));
    }

    final long fromBlockNumber = filter.getFromBlock().getNumber().orElse(0);
    final long toBlockNumber =
        filter.getToBlock().getNumber().orElse(blockchainQueries.headBlockNumber());

    return new JsonRpcSuccessResponse(
        request.getRequest().getId(),
        new LogsResult(
            privacyQueries.matchingLogs(privacyGroupId, fromBlockNumber, toBlockNumber, query)));
  }

  private void checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
      final JsonRpcRequestContext request, final String privacyGroupId) {
    final String enclavePublicKey = enclavePublicKeyProvider.getEnclaveKey(request.getUser());
    privacyController.verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
  }
}
