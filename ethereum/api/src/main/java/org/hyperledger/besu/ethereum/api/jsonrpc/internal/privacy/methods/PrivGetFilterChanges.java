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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class PrivGetFilterChanges implements JsonRpcMethod {

  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  private final FilterManager filterManager;

  public PrivGetFilterChanges(
      final FilterManager filterManager,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    this.filterManager = filterManager;
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_FILTER_CHANGES.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    final String filterId = requestContext.getRequiredParameter(1, String.class);

    final BlockParameter blockParameter = filterManager.getToBlock(filterId);
    checkIfAuthenticatedUserWasMemberAtBlock(requestContext, privacyGroupId, blockParameter);

    final List<LogWithMetadata> logs = filterManager.logsChanges(filterId);
    if (logs != null) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), new LogsResult(logs));
    }

    return new JsonRpcErrorResponse(
        requestContext.getRequest().getId(), JsonRpcError.FILTER_NOT_FOUND);
  }

  private void checkIfAuthenticatedUserWasMemberAtBlock(
      final JsonRpcRequestContext request,
      final String privacyGroupId,
      final BlockParameter blockParameter) {
    final String enclavePublicKey = enclavePublicKeyProvider.getEnclaveKey(request.getUser());
    privacyController.verifyPrivacyGroupContainsEnclavePublicKey(
        privacyGroupId, enclavePublicKey, getBlockNumberToCheckForGroupMembership(blockParameter));
  }

  @VisibleForTesting
  public Optional<Long> getBlockNumberToCheckForGroupMembership(
      final BlockParameter blockParameter) {
    // TODO check group membership at previous block (they could have been removed as of blockNumber
    // but should still get previous logs)
    if (blockParameter.isEarliest()) {
      return Optional.of(0L);
    } else if (blockParameter.isLatest()) {
      // TODO if empty is passed, membership will be checked at chain head. If they were removed in
      // that block, they get nothing
      // TODO would be nice to return Optional.of(head - 1);
      return Optional.empty();
    } else if (blockParameter.isNumeric()) {
      return Optional.of(blockParameter.getNumber().get() - 1);
    } else {
      return Optional.empty();
    }
  }
}
