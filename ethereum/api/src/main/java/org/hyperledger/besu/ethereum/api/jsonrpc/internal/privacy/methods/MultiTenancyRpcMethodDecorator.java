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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;

import java.util.Optional;

import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiTenancyRpcMethodDecorator implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(MultiTenancyRpcMethodDecorator.class);
  private final JsonRpcMethod rpcMethod;

  public MultiTenancyRpcMethodDecorator(final JsonRpcMethod rpcMethod) {
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
      LOG.error("Request does not contain an authorization token");
      return new JsonRpcUnauthorizedResponse(id, JsonRpcError.UNAUTHORIZED);
    } else if (MultiTenancyUserUtil.privacyUserId(user).isEmpty()) {
      LOG.error("Request token does not contain an enclave public key");
      return new JsonRpcErrorResponse(id, JsonRpcError.INVALID_REQUEST);
    } else {
      return rpcMethod.response(requestContext);
    }
  }
}
