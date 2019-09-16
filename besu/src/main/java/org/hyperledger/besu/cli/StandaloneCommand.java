/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.cli;

import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;

import java.io.File;
import java.nio.file.Path;

import picocli.CommandLine;

class StandaloneCommand implements DefaultCommandValues {

  // While this variable is never read it is needed for the PicoCLI to create
  // the config file option that is read elsewhere.
  @SuppressWarnings("UnusedVariable")
  @CommandLine.Option(
      names = {CONFIG_FILE_OPTION_NAME},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "TOML config file (default: none)")
  private final File configFile = null;

  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "The path to Besu data directory (default: ${DEFAULT-VALUE})")
  final Path dataPath = getDefaultBesuDataPath(this);

  // Genesis file path with null default option if the option
  // is not defined on command line as this default is handled by Runner
  // to use mainnet json file from resources as indicated in the
  // default network option
  // Then we have no control over genesis default value here.
  @CommandLine.Option(
      names = {"--genesis-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Genesis file. Setting this option makes --network option ignored and requires --network-id to be set.")
  final File genesisFile = null;

  @CommandLine.Option(
      names = {"--node-private-key-file"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description =
          "The node's private key file (default: a file named \"key\" in the Besu data folder)")
  final File nodePrivateKeyFile = null;

  @CommandLine.Option(
      names = {"--rpc-http-authentication-credentials-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Storage file for JSON-RPC HTTP authentication credentials (default: ${DEFAULT-VALUE})",
      arity = "1")
  String rpcHttpAuthenticationCredentialsFile = null;

  @CommandLine.Option(
      names = {"--rpc-ws-authentication-credentials-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Storage file for JSON-RPC WebSocket authentication credentials (default: ${DEFAULT-VALUE})",
      arity = "1")
  String rpcWsAuthenticationCredentialsFile = null;

  @CommandLine.Option(
      names = {"--privacy-public-key-file"},
      description = "The enclave's public key file")
  final File privacyPublicKeyFile = null;

  @CommandLine.Option(
      names = {"--permissions-nodes-config-file"},
      description =
          "Node permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  String nodePermissionsConfigFile = null;

  @CommandLine.Option(
      names = {"--permissions-accounts-config-file"},
      description =
          "Account permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  String accountPermissionsConfigFile = null;
}
