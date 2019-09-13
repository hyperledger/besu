/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.priv;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcEnclaveErrorConverter;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.apache.logging.log4j.Logger;

public class PrivCreatePrivacyGroup implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final Enclave enclave;
  private PrivacyParameters privacyParameters;
  private final JsonRpcParameter parameters;

  public PrivCreatePrivacyGroup(
      final Enclave enclave,
      final PrivacyParameters privacyParameters,
      final JsonRpcParameter parameters) {
    this.enclave = enclave;
    this.privacyParameters = privacyParameters;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    LOG.trace("Executing {}", RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName());

    final CreatePrivacyGroupParameter parameter =
        parameters.required(request.getParams(), 0, CreatePrivacyGroupParameter.class);

    LOG.trace(
        "Creating a privacy group with name {} and description {}",
        parameter.getName(),
        parameter.getDescription());

    final CreatePrivacyGroupRequest createPrivacyGroupRequest =
        new CreatePrivacyGroupRequest(
            parameter.getAddresses(),
            privacyParameters.getEnclavePublicKey(),
            parameter.getName(),
            parameter.getDescription());
    final PrivacyGroup response;
    try {
      response = enclave.createPrivacyGroup(createPrivacyGroupRequest);
    } catch (Exception e) {
      LOG.error("Failed to create privacy group", e);
      return new JsonRpcErrorResponse(
          request.getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }
    return new JsonRpcSuccessResponse(request.getId(), response.getPrivacyGroupId());
  }
}
