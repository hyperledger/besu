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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.List;

@Deprecated(since = "24.12.0")
public class PrivGetFilterChanges implements JsonRpcMethod {

  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;
  private final FilterManager filterManager;

  public PrivGetFilterChanges(
      final FilterManager filterManager,
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider) {
    this.filterManager = filterManager;
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_FILTER_CHANGES.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String privacyGroupId;
    try {
      privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid privacy group ID parameter (index 0)",
          RpcErrorType.INVALID_PRIVACY_GROUP_PARAMS,
          e);
    }
    final String filterId;
    try {
      filterId = requestContext.getRequiredParameter(1, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid filter ID parameter (index 1)", RpcErrorType.INVALID_FILTER_PARAMS, e);
    }

    if (privacyController instanceof MultiTenancyPrivacyController) {
      checkIfPrivacyGroupMatchesAuthenticatedPrivacyUserId(requestContext, privacyGroupId);
    }
    final List<LogWithMetadata> logs = filterManager.logsChanges(filterId);
    if (logs != null) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), new LogsResult(logs));
    }

    return new JsonRpcErrorResponse(
        requestContext.getRequest().getId(), RpcErrorType.FILTER_NOT_FOUND);
  }

  private void checkIfPrivacyGroupMatchesAuthenticatedPrivacyUserId(
      final JsonRpcRequestContext request, final String privacyGroupId) {
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
  }
}
