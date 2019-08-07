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
package tech.pegasys.pantheon.cli.subcommands;

import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.getDefaultPantheonDataPath;
import static tech.pegasys.pantheon.cli.subcommands.RetestethSubCommand.COMMAND_NAME;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT;

import tech.pegasys.pantheon.PantheonInfo;
import tech.pegasys.pantheon.cli.custom.JsonRPCWhitelistHostsProperty;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethConfiguration;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethService;

import java.net.InetAddress;
import java.nio.file.Path;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = COMMAND_NAME,
    description = "Run a Retesteth compatible server for reference tests.",
    mixinStandardHelpOptions = true)
public class RetestethSubCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  public static final String COMMAND_NAME = "retesteth";

  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "The path to Pantheon data directory (default: ${DEFAULT-VALUE})")
  final Path dataPath = getDefaultPantheonDataPath(this);

  @Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO)")
  private final Level logLevel = Level.INFO;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--rpc-http-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcHttpHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--rpc-http-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcHttpPort = DEFAULT_JSON_RPC_PORT;

  @Option(
      names = {"--host-whitelist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to whitelist for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCWhitelistHostsProperty hostsWhitelist = new JsonRPCWhitelistHostsProperty();

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
    if (logLevel != null) {
      System.out.println("Setting logging level to " + logLevel.name());
      Configurator.setAllLevels("", logLevel);
    }
  }

  @Override
  public void run() {
    prepareLogging();

    final RetestethConfiguration retestethConfiguration = new RetestethConfiguration(dataPath);
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setHost(rpcHttpHost);
    jsonRpcConfiguration.setPort(rpcHttpPort);
    jsonRpcConfiguration.setHostsWhitelist(hostsWhitelist);

    final RetestethService retestethService =
        new RetestethService(PantheonInfo.version(), retestethConfiguration, jsonRpcConfiguration);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    retestethService.close();
                    LogManager.shutdown();
                  } catch (final Exception e) {
                    LOG.error("Failed to stop Pantheon Retesteth");
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
