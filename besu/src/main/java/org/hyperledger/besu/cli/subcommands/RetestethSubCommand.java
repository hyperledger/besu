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
package org.hyperledger.besu.cli.subcommands;

import static org.hyperledger.besu.cli.subcommands.RetestethSubCommand.COMMAND_NAME;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.custom.JsonRPCAllowlistHostsProperty;
import org.hyperledger.besu.cli.options.stable.LoggingLevelOption;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.retesteth.RetestethConfiguration;
import org.hyperledger.besu.ethereum.retesteth.RetestethService;
import org.hyperledger.besu.util.Log4j2ConfiguratorUtil;

import java.net.InetAddress;
import java.nio.file.Path;

import org.apache.logging.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
    name = COMMAND_NAME,
    description = "Run a Retesteth compatible server for reference tests.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
@SuppressWarnings("unused")
public class RetestethSubCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(RetestethSubCommand.class);

  public static final String COMMAND_NAME = "retesteth";

  /**
   * Using a distinct port for retesteth will result in less testing collisions and accidental RPC
   * calls. This is <code>0xba5e</code> in hex, a hex speak play on the english translation of
   * "Besu."
   */
  public static final int RETESTETH_PORT = 47710;

  @Option(
      names = {"--data-path"},
      paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
      description = "The path to Besu data directory (default: ${DEFAULT-VALUE})")
  private final Path dataPath = DefaultCommandValues.getDefaultBesuDataPath(this);

  @Mixin private LoggingLevelOption loggingLevelOption;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--rpc-http-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "Host for Retesteth JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcHttpHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--rpc-http-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port for Retesteth JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcHttpPort = RETESTETH_PORT;

  @Option(
      names = {"--host-allowlist", "--host-whitelist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to allow for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCAllowlistHostsProperty hostsAllowlist = new JsonRPCAllowlistHostsProperty();

  private InetAddress autoDiscoveredDefaultIP;

  // Used to discover the default IP of the client.
  // Loopback IP is used by default as this is how smokeTests require it to be
  // and it's probably a good security behaviour to default only on the localhost.
  private InetAddress autoDiscoverDefaultIP() {

    if (autoDiscoveredDefaultIP != null) {
      return autoDiscoveredDefaultIP;
    }

    autoDiscoveredDefaultIP = InetAddress.getLoopbackAddress();

    return autoDiscoveredDefaultIP;
  }

  private void prepareLogging() {
    // set log level per CLI flags
    final Level logLevel = loggingLevelOption.getLogLevel();
    if (logLevel != null) {
      System.out.println("Setting logging level to " + logLevel.name());
      Log4j2ConfiguratorUtil.setAllLevels("", logLevel);
    }
  }

  @Override
  public void run() {
    prepareLogging();

    final RetestethConfiguration retestethConfiguration = new RetestethConfiguration(dataPath);
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setHost(rpcHttpHost);
    jsonRpcConfiguration.setPort(rpcHttpPort);
    jsonRpcConfiguration.setHostsAllowlist(hostsAllowlist);

    final RetestethService retestethService =
        new RetestethService(BesuInfo.version(), retestethConfiguration, jsonRpcConfiguration);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    retestethService.close();
                    Log4j2ConfiguratorUtil.shutdown();
                  } catch (final Exception e) {
                    LOG.error("Failed to stop Besu Retesteth");
                  }
                }));
    retestethService.start();
    try {
      Thread.sleep(Long.MAX_VALUE); // Is there a better way?
    } catch (final InterruptedException e) {
      // e.printStackTrace();
    }
  }
}
