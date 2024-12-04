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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.FIND_PRIVACY_GROUP_ERROR;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "24.12.0")
public class PrivDebugGetStateRoot extends AbstractBlockParameterMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PrivDebugGetStateRoot.class);

  private final PrivacyIdProvider privacyIdProvider;
  private final PrivacyController privacyController;

  public PrivDebugGetStateRoot(
      final BlockchainQueries blockchainQueries,
      final PrivacyIdProvider privacyIdProvider,
      final PrivacyController privacyController) {
    super(blockchainQueries);
    this.privacyIdProvider = privacyIdProvider;
    this.privacyController = privacyController;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_DEBUG_GET_STATE_ROOT.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(1, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 1)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext requestContext, final long blockNumber) {
    final String privacyGroupId;
    try {
      privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid privacy group ID parameter (index 0)",
          RpcErrorType.INVALID_PRIVACY_GROUP_PARAMS,
          e);
    }
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(requestContext.getUser());
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing {}", getName());
    }

    final Optional<PrivacyGroup> privacyGroup;
    try {
      privacyGroup = privacyController.findPrivacyGroupByGroupId(privacyGroupId, privacyUserId);
    } catch (final MultiTenancyValidationException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), FIND_PRIVACY_GROUP_ERROR);
    } catch (final EnclaveClientException e) {
      final Pattern pattern = Pattern.compile("^Privacy group.*not found$");
      if (e.getMessage().equals(RpcErrorType.ENCLAVE_PRIVACY_GROUP_MISSING.getMessage())
          || pattern.matcher(e.getMessage()).find()) {
        LOG.error("Failed to retrieve privacy group");
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), FIND_PRIVACY_GROUP_ERROR);
      } else {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), RpcErrorType.ENCLAVE_ERROR);
      }
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PRIVACY_GROUP_PARAMS);
    }

    if (privacyGroup.isEmpty()) {
      LOG.error("Failed to retrieve privacy group");
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), FIND_PRIVACY_GROUP_ERROR);
    }

    return privacyController
        .getStateRootByBlockNumber(privacyGroupId, privacyUserId, blockNumber)
        .<JsonRpcResponse>map(
            stateRootHash ->
                new JsonRpcSuccessResponse(
                    requestContext.getRequest().getId(), stateRootHash.toString()))
        .orElse(
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(), RpcErrorType.INTERNAL_ERROR));
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) findResultByParamType(requestContext);
  }
}
