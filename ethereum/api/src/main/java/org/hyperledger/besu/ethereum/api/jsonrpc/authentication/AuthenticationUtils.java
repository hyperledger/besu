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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationUtils {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting
  public static boolean isPermitted(
      final Optional<AuthenticationService> authenticationService,
      final Optional<User> optionalUser,
      final JsonRpcMethod jsonRpcMethod) {

    AtomicBoolean foundMatchingPermission = new AtomicBoolean();

    if (authenticationService.isPresent()) {
      if (optionalUser.isPresent()) {
        User user = optionalUser.get();
        for (String perm : jsonRpcMethod.getPermissions()) {
          user.isAuthorized(
              perm,
              (authed) -> {
                if (authed.result()) {
                  LOG.trace(
                      "user {} authorized : {} via permission {}",
                      user,
                      jsonRpcMethod.getName(),
                      perm);
                  foundMatchingPermission.set(true);
                }
              });
        }
      }
    } else {
      // no auth provider configured thus anything is permitted
      foundMatchingPermission.set(true);
    }

    if (!foundMatchingPermission.get()) {
      LOG.trace("user NOT authorized : {}", jsonRpcMethod.getName());
    }
    return foundMatchingPermission.get();
  }

  public static void getUser(
      final Optional<AuthenticationService> authenticationService,
      final String token,
      final Handler<Optional<User>> handler) {
    try {
      if (!authenticationService.isPresent()) {
        handler.handle(Optional.empty());
      } else {
        authenticationService
            .get()
            .getJwtAuthProvider()
            .authenticate(
                new JsonObject().put("jwt", token),
                (r) -> {
                  final User user = r.result();
                  handler.handle(Optional.of(user));
                });
      }
    } catch (Exception e) {
      handler.handle(Optional.empty());
    }
  }

  public static String getJwtTokenFromAuthorizationHeaderValue(final String value) {
    if (value != null) {
      final String bearerSchemaName = "Bearer ";
      if (value.startsWith(bearerSchemaName)) {
        return value.substring(bearerSchemaName.length());
      }
    }
    return null;
  }
}
