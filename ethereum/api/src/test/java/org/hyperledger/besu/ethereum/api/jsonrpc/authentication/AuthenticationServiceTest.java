package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.Test;

public class AuthenticationServiceTest {

  @Test
  public void authenticationServiceNotCreatedWhenAuthenticationDisabledAndHasCredentialsFile() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(Vertx.vertx(), jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void authenticationServiceNotCreatedWhenAuthenticationDisabledAndHasPublicKeyFile()
      throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(Vertx.vertx(), jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }

  @Test
  public void
      authenticationServiceNotCreatedWhenAuthenticationDisabledAndNotCredentialsFileOrPublicKeyFile()
          throws IOException {
    final File publicKeyFile = File.createTempFile("publicKey", "jwt");
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setAuthenticationEnabled(false);
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(publicKeyFile);
    jsonRpcConfiguration.setAuthenticationCredentialsFile("some/file/path");

    final Optional<AuthenticationService> authenticationService =
        AuthenticationService.create(Vertx.vertx(), jsonRpcConfiguration);
    assertThat(authenticationService).isEmpty();
  }
}
