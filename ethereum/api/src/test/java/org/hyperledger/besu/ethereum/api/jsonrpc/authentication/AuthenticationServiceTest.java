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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import org.junit.Test;

public class AuthenticationServiceTest {
  private final Vertx vertx = Vertx.vertx();
  private static final String INVALID_TOKEN_WITHOUT_EXP =
      "ewogICJhbGciOiAibm9uZSIsCiAgInR5cCI6ICJKV1QiCn"
          + "0.eyJpYXQiOjE1MTYyMzkwMjIsInBlcm1pc3Npb25zIjpbIm5ldDpwZWVyQ291bnQiXX0";

  @Test
  public void authenticationServiceNotCreatedWhenRpcAuthenticationDisabledAndHasCredentialsFile() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authenticationServiceNotCreatedWhenRpcAuthenticationDisabledAndHasPublicKeyFile()
      throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void
      authenticationServiceNotCreatedWhenRpcAuthenticationDisabledAndNoCredentialsFileOrPublicKeyFile()
          throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);
    jsonRpcConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authenticationServiceNotCreatedWhenWsAuthenticationDisabledAndHasCredentialsFile() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authenticationServiceNotCreatedWhenWsAuthenticationDisabledAndHasPublicKeyFile()
      throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void
      authenticationServiceNotCreatedWhenWsAuthenticationDisabledAndNoCredentialsFileOrPublicKeyFile()
          throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);
    webSocketConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authServiceNotCreatedWhenRpcAuthnDisabledAndHasAlgorithmSpecified() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationAlgorithm(JwtAlgorithm.RS256);

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authServiceNotCreatedWhenWsAuthDisabledAndHasAlgorithmSpecified() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationAlgorithm(JwtAlgorithm.RS256);

    final Optional<AuthenticationService> authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void getUserFailsIfTokenDoesNotHaveExpiryClaim() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(true);
    final AuthenticationService authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration).get();
    final StubUserHandler handler = new StubUserHandler();

    authenticationService.authenticate(INVALID_TOKEN_WITHOUT_EXP, handler);

    assertThat(handler.getEvent()).isEmpty();
  }

  @Test
  public void getUserSucceedsWithValidToken() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(true);
    webSocketConfiguration.setAuthenticationPublicKeyFile(null);
    final AuthenticationService authenticationService =
        DefaultAuthenticationService.create(vertx, webSocketConfiguration).get();
    final StubUserHandler handler = new StubUserHandler();
    final JsonObject jwtContents =
        new JsonObject()
            .put("permissions", new JsonArray(Arrays.asList("net:peerCount")))
            .put("username", "successKid");
    final JWTOptions options = new JWTOptions().setExpiresInMinutes(5).setAlgorithm("RS256");
    final String token =
        authenticationService.getJwtAuthProvider().generateToken(jwtContents, options);
    authenticationService.authenticate(token, handler);

    User successKid = handler.getEvent().get();
    assertThat(successKid.attributes().getLong("exp")).isNotNull();
    assertThat(successKid.attributes().getLong("iat")).isNotNull();
    assertThat(successKid.principal().getJsonArray("permissions").getString(0))
        .isEqualTo("net:peerCount");
  }

  private static class StubUserHandler implements Handler<Optional<User>> {
    private Optional<User> event;

    @Override
    public void handle(final Optional<User> event) {
      this.event = event;
    }

    public Optional<User> getEvent() {
      return event;
    }
  }
}
