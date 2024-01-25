/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.VALID_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT;

import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import picocli.CommandLine;

public class RpcWebsocketOptions {
  @CommandLine.Option(
      names = {"--rpc-ws-authentication-jwt-algorithm"},
      description =
          "Encryption algorithm used for Websockets JWT public key. Possible values are ${COMPLETION-CANDIDATES}"
              + " (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final JwtAlgorithm rpcWebsocketsAuthenticationAlgorithm =
      DefaultCommandValues.DEFAULT_JWT_ALGORITHM;

  @CommandLine.Option(
      names = {"--rpc-ws-enabled"},
      description = "Set to start the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-ws-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "Host for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcWsHost;

  @CommandLine.Option(
      names = {"--rpc-ws-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcWsPort = DEFAULT_WEBSOCKET_PORT;

  @CommandLine.Option(
      names = {"--rpc-ws-max-frame-size"},
      description =
          "Maximum size in bytes for JSON-RPC WebSocket frames (default: ${DEFAULT-VALUE}). If this limit is exceeded, the websocket will be disconnected.",
      arity = "1")
  private final Integer rpcWsMaxFrameSize = DefaultCommandValues.DEFAULT_WS_MAX_FRAME_SIZE;

  @CommandLine.Option(
      names = {"--rpc-ws-max-active-connections"},
      description =
          "Maximum number of WebSocket connections allowed for JSON-RPC (default: ${DEFAULT-VALUE}). Once this limit is reached, incoming connections will be rejected.",
      arity = "1")
  private final Integer rpcWsMaxConnections = DefaultCommandValues.DEFAULT_WS_MAX_CONNECTIONS;

  @CommandLine.Option(
      names = {"--rpc-ws-api", "--rpc-ws-apis"},
      paramLabel = "<api name>",
      split = " {0,1}, {0,1}",
      arity = "1..*",
      description =
          "Comma separated list of APIs to enable on JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final List<String> rpcWsApis = DEFAULT_RPC_APIS;

  @CommandLine.Option(
      names = {"--rpc-ws-api-methods-no-auth", "--rpc-ws-api-method-no-auth"},
      paramLabel = "<api name>",
      split = " {0,1}, {0,1}",
      arity = "1..*",
      description =
          "Comma separated list of RPC methods to exclude from RPC authentication services, RPC WebSocket authentication must be enabled")
  private final List<String> rpcWsApiMethodsNoAuth = new ArrayList<String>();

  @CommandLine.Option(
      names = {"--rpc-ws-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsAuthenticationEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-ws-authentication-credentials-file"},
      paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
      description =
          "Storage file for JSON-RPC WebSocket authentication credentials (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcWsAuthenticationCredentialsFile = null;

  @CommandLine.Option(
      names = {"--rpc-ws-authentication-jwt-public-key-file"},
      paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
      description = "JWT public key file for JSON-RPC WebSocket authentication",
      arity = "1")
  private final File rpcWsAuthenticationPublicKeyFile = null;

  public void validate(
      final Logger logger, final CommandLine commandLine, final Predicate<String> configuredApis) {
    checkOptionDependencies(logger, commandLine);

    if (!rpcWsApis.stream().allMatch(configuredApis)) {
      final List<String> invalidWsApis = new ArrayList<>(rpcWsApis);
      invalidWsApis.removeAll(VALID_APIS);
      throw new CommandLine.ParameterException(
          commandLine,
          "Invalid value for option '--rpc-ws-api': invalid entries found " + invalidWsApis);
    }

    final boolean validWsApiMethods =
        rpcWsApiMethodsNoAuth.stream().allMatch(RpcMethod::rpcMethodExists);

    if (!validWsApiMethods) {
      throw new CommandLine.ParameterException(
          commandLine,
          "Invalid value for option '--rpc-ws-api-methods-no-auth', options must be valid RPC methods");
    }

    if (isRpcWsAuthenticationEnabled
        && rpcWsAuthenticationCredentialsFile(commandLine) == null
        && rpcWsAuthenticationPublicKeyFile == null) {
      throw new CommandLine.ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file or authentication public key file");
    }
  }

  private void checkOptionDependencies(final Logger logger, final CommandLine commandLine) {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-ws-enabled",
        !isRpcWsEnabled,
        List.of(
            "--rpc-ws-api",
            "--rpc-ws-apis",
            "--rpc-ws-api-method-no-auth",
            "--rpc-ws-api-methods-no-auth",
            "--rpc-ws-host",
            "--rpc-ws-port",
            "--rpc-ws-max-frame-size",
            "--rpc-ws-max-active-connections",
            "--rpc-ws-authentication-enabled",
            "--rpc-ws-authentication-credentials-file",
            "--rpc-ws-authentication-public-key-file",
            "--rpc-ws-authentication-jwt-algorithm"));

    if (isRpcWsAuthenticationEnabled) {
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--rpc-ws-authentication-public-key-file",
          rpcWsAuthenticationPublicKeyFile == null,
          List.of("--rpc-ws-authentication-jwt-algorithm"));
    }
  }

  public WebSocketConfiguration webSocketConfiguration(
      final List<String> hostsAllowlist, final String defaultHostAddress, final Long wsTimoutSec) {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isRpcWsEnabled);
    webSocketConfiguration.setHost(
        Strings.isNullOrEmpty(rpcWsHost) ? defaultHostAddress : rpcWsHost);
    webSocketConfiguration.setPort(rpcWsPort);
    webSocketConfiguration.setMaxFrameSize(rpcWsMaxFrameSize);
    webSocketConfiguration.setMaxActiveConnections(rpcWsMaxConnections);
    webSocketConfiguration.setRpcApis(rpcWsApis);
    webSocketConfiguration.setRpcApisNoAuth(
        rpcWsApiMethodsNoAuth.stream().distinct().collect(Collectors.toList()));
    webSocketConfiguration.setAuthenticationEnabled(isRpcWsAuthenticationEnabled);
    webSocketConfiguration.setAuthenticationCredentialsFile(rpcWsAuthenticationCredentialsFile);
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    webSocketConfiguration.setAuthenticationPublicKeyFile(rpcWsAuthenticationPublicKeyFile);
    webSocketConfiguration.setAuthenticationAlgorithm(rpcWebsocketsAuthenticationAlgorithm);
    webSocketConfiguration.setTimeoutSec(wsTimoutSec);
    return webSocketConfiguration;
  }

  private String rpcWsAuthenticationCredentialsFile(final CommandLine commandLine) {
    final String filename = rpcWsAuthenticationCredentialsFile;

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "WS");
    }
    return filename;
  }

  public List<String> getRpcWsApis() {
    return rpcWsApis;
  }

  public Boolean isRpcWsEnabled() {
    return isRpcWsEnabled;
  }

  public Integer getRpcWsPort() {
    return rpcWsPort;
  }
}
