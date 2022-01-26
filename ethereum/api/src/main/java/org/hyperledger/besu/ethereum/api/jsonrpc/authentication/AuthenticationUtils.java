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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationUtils {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationUtils.class);

  @VisibleForTesting
  public static boolean isPermitted(
      final Optional<AuthenticationService> authenticationService,
      final Optional<User> optionalUser,
      final JsonRpcMethod jsonRpcMethod) {

    AtomicBoolean foundMatchingPermission = new AtomicBoolean();

    if (authenticationService.isEmpty()) {
      // no auth provider configured thus anything is permitted
      return true;
    }

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
        // exit if a matching permission was found, no need to keep checking
        if (foundMatchingPermission.get()) {
          return foundMatchingPermission.get();
        }
      }
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
      if (authenticationService.isEmpty()) {
        handler.handle(Optional.empty());
      } else {
        authenticationService
            .get()
            .getJwtAuthProvider()
            .authenticate(
                new JsonObject().put("token", token),
                (r) -> {
                  if (r.succeeded()) {
                    final Optional<User> user = Optional.ofNullable(r.result());
                    validateExpiryExists(user);
                    handler.handle(user);
                  } else {
                    LOG.debug("Invalid JWT token", r.cause());
                    handler.handle(Optional.empty());
                  }
                });
      }
    } catch (Exception e) {
      handler.handle(Optional.empty());
    }
  }

  private static void validateExpiryExists(final Optional<User> user) {
    if (!user.map(User::attributes).map(a -> a.containsKey("exp")).orElse(false)) {
      throw new IllegalStateException("Invalid JWT doesn't have expiry");
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
