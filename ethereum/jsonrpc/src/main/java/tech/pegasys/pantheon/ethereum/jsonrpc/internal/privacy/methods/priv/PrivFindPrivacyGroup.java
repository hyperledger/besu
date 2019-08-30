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
import tech.pegasys.pantheon.enclave.types.FindPrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Arrays;

import org.apache.logging.log4j.Logger;

public class PrivFindPrivacyGroup implements JsonRpcMethod {

  private static final Logger LOG = getLogger();
  private final Enclave enclave;
  private final JsonRpcParameter parameters;

  public PrivFindPrivacyGroup(final Enclave enclave, final JsonRpcParameter parameters) {
    this.enclave = enclave;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_FIND_PRIVACY_GROUP.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    LOG.trace("Executing {}", RpcMethod.PRIV_FIND_PRIVACY_GROUP.getMethodName());

    final String[] addresses = parameters.required(request.getParams(), 0, String[].class);

    LOG.trace("Finding a privacy group with members {}", Arrays.toString(addresses));

    FindPrivacyGroupRequest findPrivacyGroupRequest = new FindPrivacyGroupRequest(addresses);
    PrivacyGroup[] response;
    try {
      response = enclave.findPrivacyGroup(findPrivacyGroupRequest);
    } catch (Exception e) {
      LOG.error("Failed to fetch group from Enclave with error " + e.getMessage());
      LOG.error(e);
      return new JsonRpcSuccessResponse(request.getId(), JsonRpcError.FIND_PRIVACY_GROUP_ERROR);
    }
    return new JsonRpcSuccessResponse(request.getId(), response);
  }
}
