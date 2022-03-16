/*
 * Copyright Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Collection;
import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;

public interface AuthenticationService {
  void handleLogin(RoutingContext routingContext);

  JWTAuth getJwtAuthProvider();

  void authenticate(String token, Handler<Optional<User>> handler);

  boolean isPermitted(
      final Optional<User> optionalUser,
      final JsonRpcMethod jsonRpcMethod,
      final Collection<String> noAuthMethods);
}
