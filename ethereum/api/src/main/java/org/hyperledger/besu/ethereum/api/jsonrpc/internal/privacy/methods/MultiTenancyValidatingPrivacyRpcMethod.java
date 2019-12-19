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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVACY_MULTI_TENANCY_NO_ENCLAVE_PUBLIC_KEY;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVACY_MULTI_TENANCY_NO_TOKEN;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import java.util.Optional;

import io.vertx.ext.auth.User;

public class MultiTenancyValidatingPrivacyRpcMethod implements JsonRpcMethod {

  private JsonRpcMethod rpcMethod;

  public MultiTenancyValidatingPrivacyRpcMethod(final JsonRpcMethod rpcMethod) {
    this.rpcMethod = rpcMethod;
  }

  @Override
  public String getName() {
    return rpcMethod.getName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Optional<User> user = requestContext.getUser();
    final Object id = requestContext.getRequest().getId();
    if (user.isEmpty()) {
      return new JsonRpcErrorResponse(id, PRIVACY_MULTI_TENANCY_NO_TOKEN);
    } else if (MultiTenancyUserUtil.enclavePublicKey(user).isEmpty()) {
      return new JsonRpcErrorResponse(id, PRIVACY_MULTI_TENANCY_NO_ENCLAVE_PUBLIC_KEY);
    } else {
      return rpcMethod.response(requestContext);
    }
  }
}
