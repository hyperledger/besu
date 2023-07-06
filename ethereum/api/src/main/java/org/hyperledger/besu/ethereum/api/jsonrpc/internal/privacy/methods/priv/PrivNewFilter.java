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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

public class PrivNewFilter implements JsonRpcMethod {

  private final FilterManager filterManager;
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public PrivNewFilter(
      final FilterManager filterManager,
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider) {
    this.filterManager = filterManager;
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_NEW_FILTER.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final String privacyGroupId = request.getRequiredParameter(0, String.class);
    final FilterParameter filter = request.getRequiredParameter(1, FilterParameter.class);
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());

    if (privacyController instanceof MultiTenancyPrivacyController) {
      // no need to pass blockNumber. To create a filter, you need to be a current member of the
      // group
      checkIfPrivacyGroupMatchesAuthenticatedPrivacyUserId(privacyUserId, privacyGroupId);
    }

    if (!filter.isValid()) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final String logFilterId =
        filterManager.installPrivateLogFilter(
            privacyGroupId,
            privacyUserId,
            filter.getFromBlock(),
            filter.getToBlock(),
            filter.getLogsQuery());

    return new JsonRpcSuccessResponse(request.getRequest().getId(), logFilterId);
  }

  private void checkIfPrivacyGroupMatchesAuthenticatedPrivacyUserId(
      final String privacyUserId, final String privacyGroupId) {
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
  }
}
