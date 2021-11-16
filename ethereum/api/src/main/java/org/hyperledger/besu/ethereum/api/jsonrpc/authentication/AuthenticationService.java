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

import io.vertx.ext.auth.impl.jose.JWK;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;

import java.io.File;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;

/** Provides authentication handlers for use in the http and websocket services */
public class AuthenticationService {

  private final JWTAuth jwtAuthProvider;
  @VisibleForTesting public final JWTAuthOptions jwtAuthOptions;
  private final Optional<AuthProvider> credentialAuthProvider;
  private static final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();

  private AuthenticationService(
      final JWTAuth jwtAuthProvider,
      final JWTAuthOptions jwtAuthOptions,
      final Optional<AuthProvider> credentialAuthProvider) {
    this.jwtAuthProvider = jwtAuthProvider;
    this.jwtAuthOptions = jwtAuthOptions;
    this.credentialAuthProvider = credentialAuthProvider;
  }

  /**
   * Creates a ready for use set of authentication providers if authentication is configured to be
   * on
   *
   * @param vertx The vertx instance that will be providing requests that this set of authentication
   *     providers will be handling
   * @param config The {{@link JsonRpcConfiguration}} that describes this rpc setup
   * @return Optionally an authentication service. If empty then authentication isn't to be enabled
   *     on this service
   */
  public static Optional<AuthenticationService> create(
      final Vertx vertx, final JsonRpcConfiguration config) {
    return create(
        vertx,
        config.isAuthenticationEnabled(),
        config.getAuthenticationCredentialsFile(),
        config.getAuthenticationPublicKeyFile(),
        config.getAuthenticationAlgorithm());
  }

  /**
   * Creates a ready for use set of authentication providers if authentication is configured to be
   * on
   *
   * @param vertx The vertx instance that will be providing requests that this set of authentication
   *     providers will be handling
   * @param config The {{@link JsonRpcConfiguration}} that describes this rpc setup
   * @return Optionally an authentication service. If empty then authentication isn't to be enabled
   *     on this service
   */
  public static Optional<AuthenticationService> create(
      final Vertx vertx, final WebSocketConfiguration config) {
    return create(
        vertx,
        config.isAuthenticationEnabled(),
        config.getAuthenticationCredentialsFile(),
        config.getAuthenticationPublicKeyFile(),
        config.getAuthenticationAlgorithm());
  }

  private static Optional<AuthenticationService> create(
      final Vertx vertx,
      final boolean authenticationEnabled,
      final String authenticationCredentialsFile,
      final File authenticationPublicKeyFile,
      final JwtAlgorithm authenticationAlgorithm) {
    if (!authenticationEnabled) {
      return Optional.empty();
    }

    final JWTAuthOptions jwtAuthOptions;
    if (authenticationPublicKeyFile == null) {
      jwtAuthOptions = jwtAuthOptionsFactory.createWithGeneratedKeyPair();
    } else {
      jwtAuthOptions =
          authenticationAlgorithm == null
              ? jwtAuthOptionsFactory.createForExternalPublicKey(authenticationPublicKeyFile)
              : jwtAuthOptionsFactory.createForExternalPublicKeyWithAlgorithm(
                  authenticationPublicKeyFile, authenticationAlgorithm.toString());
    }
    final Optional<AuthProvider> credentialAuthProvider =
        makeCredentialAuthProvider(vertx, authenticationEnabled, authenticationCredentialsFile);

    return Optional.of(
        new AuthenticationService(
            JWTAuth.create(vertx, jwtAuthOptions), jwtAuthOptions, credentialAuthProvider));
  }

  private static Optional<AuthProvider> makeCredentialAuthProvider(
      final Vertx vertx,
      final boolean authenticationEnabled,
      @Nullable final String authenticationCredentialsFile) {
    if (authenticationEnabled && authenticationCredentialsFile != null) {
      return Optional.of(
          new TomlAuthOptions().setTomlPath(authenticationCredentialsFile).createProvider(vertx));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Static route for terminating login requests when Authentication is disabled
   *
   * @param routingContext The vertx routing context for this request
   */
  public static void handleDisabledLogin(final RoutingContext routingContext) {
    routingContext
        .response()
        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
        .setStatusMessage("Authentication not enabled")
        .end();
  }

  /**
   * Handles a login request and checks the provided credentials against our credential auth
   * provider
   *
   * @param routingContext Routing context associated with this request
   */
  public void handleLogin(final RoutingContext routingContext) {
    if (credentialAuthProvider.isPresent()) {
      login(routingContext, credentialAuthProvider.get());
    } else {
      handleDisabledLogin(routingContext);
    }
  }

  private void login(
      final RoutingContext routingContext, final AuthProvider credentialAuthProvider) {
    final JsonObject requestBody = routingContext.getBodyAsJson();

    if (requestBody == null) {
      routingContext
          .response()
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .setStatusMessage(HttpResponseStatus.BAD_REQUEST.reasonPhrase())
          .end();
      return;
    }

    // Check user
    final JsonObject authParams = new JsonObject();
    authParams.put("username", requestBody.getValue("username"));
    authParams.put("password", requestBody.getValue("password"));
    credentialAuthProvider.authenticate(
        authParams,
        (r) -> {
          if (r.failed()) {
            routingContext
                .response()
                .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
                .setStatusMessage(HttpResponseStatus.UNAUTHORIZED.reasonPhrase())
                .end();
          } else {
            final User user = r.result();

            final JWTOptions options =
                new JWTOptions().setExpiresInMinutes(5).setAlgorithm("RS256");
            final JsonObject jwtContents =
                new JsonObject()
                    .put("permissions", user.principal().getValue("permissions"))
                    .put("username", user.principal().getValue("username"));
            final String privacyPublicKey = user.principal().getString("privacyPublicKey");
            if (privacyPublicKey != null) {
              jwtContents.put("privacyPublicKey", privacyPublicKey);
            }

            final String token = jwtAuthProvider.generateToken(jwtContents, options);

            final JsonObject responseBody = new JsonObject().put("token", token);
            final HttpServerResponse response = routingContext.response();
            if (!response.closed()) {
              response.setStatusCode(200);
              response.putHeader("Content-Type", "application/json");
              response.end(responseBody.encode());
            }
          }
        });
  }

  public JWTAuth getJwtAuthProvider() {
    return jwtAuthProvider;
  }
}
