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

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "24.12.0")
public class PrivCreatePrivacyGroup implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PrivCreatePrivacyGroup.class);
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public PrivCreatePrivacyGroup(
      final PrivacyController privacyController, final PrivacyIdProvider privacyIdProvider) {
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName());

    final CreatePrivacyGroupParameter parameter;
    try {
      parameter = requestContext.getRequiredParameter(0, CreatePrivacyGroupParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid create privacy group parameter (index 0)",
          RpcErrorType.INVALID_CREATE_PRIVACY_GROUP_PARAMS,
          e);
    }

    LOG.trace(
        "Creating a privacy group with name {} and description {}",
        parameter.getName(),
        parameter.getDescription());

    final PrivacyGroup response;
    try {
      response =
          privacyController.createPrivacyGroup(
              parameter.getAddresses(),
              parameter.getName(),
              parameter.getDescription(),
              privacyIdProvider.getPrivacyUserId(requestContext.getUser()));
    } catch (Exception e) {
      LOG.error("Failed to create privacy group", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), response.getPrivacyGroupId());
  }
}
