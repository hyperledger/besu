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
import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.Test;

public class AuthenticationServiceTest {
  private final Vertx vertx = Vertx.vertx();

  @Test
  public void authenticationServiceNotCreatedWhenRpcAuthenticationDisabledAndHasCredentialsFile() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(vertx, jsonRpcConfiguration);
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
        AuthenticationService.create(vertx, jsonRpcConfiguration);
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
        AuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authenticationServiceNotCreatedWhenWsAuthenticationDisabledAndHasCredentialsFile() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(vertx, webSocketConfiguration);
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
        AuthenticationService.create(vertx, webSocketConfiguration);
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
        AuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authServiceNotCreatedWhenRpcAuthnDisabledAndHasAlgorithmSpecified() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationAlgorithm(JwtAlgorithm.RS256);

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(vertx, jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authServiceNotCreatedWhenWsAuthDisabledAndHasAlgorithmSpecified() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setAuthenticationAlgorithm(JwtAlgorithm.RS256);

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(vertx, webSocketConfiguration);
    assertThat(authenticationService).isEmpty();
  }
}
