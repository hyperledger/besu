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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.authentication;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.RoutingContext;

/** Provides authentication handlers for use in the http and websocket services */
public class AuthenticationService {

  private final JWTAuth jwtAuthProvider;
  @VisibleForTesting public final JWTAuthOptions jwtAuthOptions;
  private final AuthProvider credentialAuthProvider;

  private AuthenticationService(
      final JWTAuth jwtAuthProvider,
      final JWTAuthOptions jwtAuthOptions,
      final AuthProvider credentialAuthProvider) {
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
    final Optional<JWTAuthOptions> jwtAuthOptions =
        makeJwtAuthOptions(
            config.isAuthenticationEnabled(), config.getAuthenticationCredentialsFile());
    if (!jwtAuthOptions.isPresent()) {
      return Optional.empty();
    }

    final Optional<AuthProvider> credentialAuthProvider =
        makeCredentialAuthProvider(
            vertx, config.isAuthenticationEnabled(), config.getAuthenticationCredentialsFile());
    if (!credentialAuthProvider.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(
        new AuthenticationService(
            jwtAuthOptions.map(o -> JWTAuth.create(vertx, o)).get(),
            jwtAuthOptions.get(),
            credentialAuthProvider.get()));
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
    final Optional<JWTAuthOptions> jwtAuthOptions =
        makeJwtAuthOptions(
            config.isAuthenticationEnabled(), config.getAuthenticationCredentialsFile());
    if (!jwtAuthOptions.isPresent()) {
      return Optional.empty();
    }

    final Optional<AuthProvider> credentialAuthProvider =
        makeCredentialAuthProvider(
            vertx, config.isAuthenticationEnabled(), config.getAuthenticationCredentialsFile());
    if (!credentialAuthProvider.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(
        new AuthenticationService(
            jwtAuthOptions.map(o -> JWTAuth.create(vertx, o)).get(),
            jwtAuthOptions.get(),
            credentialAuthProvider.get()));
  }

  private static Optional<JWTAuthOptions> makeJwtAuthOptions(
      final boolean authenticationEnabled, @Nullable final String authenticationCredentialsFile) {
    if (authenticationEnabled && authenticationCredentialsFile != null) {
      final KeyPairGenerator keyGenerator;
      try {
        keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(1024);
      } catch (final NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      final KeyPair keypair = keyGenerator.generateKeyPair();

      final JWTAuthOptions jwtAuthOptions =
          new JWTAuthOptions()
              .setPermissionsClaimKey("permissions")
              .addPubSecKey(
                  new PubSecKeyOptions()
                      .setAlgorithm("RS256")
                      .setPublicKey(
                          Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded()))
                      .setSecretKey(
                          Base64.getEncoder().encodeToString(keypair.getPrivate().getEncoded())));

      return Optional.of(jwtAuthOptions);
    } else {
      return Optional.empty();
    }
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
            final String token = jwtAuthProvider.generateToken(jwtContents, options);

            final JsonObject responseBody = new JsonObject().put("token", token);
            final HttpServerResponse response = routingContext.response();
            response.setStatusCode(200);
            response.putHeader("Content-Type", "application/json");
            response.end(responseBody.encode());
          }
        });
  }

  public JWTAuth getJwtAuthProvider() {
    return jwtAuthProvider;
  }
}
