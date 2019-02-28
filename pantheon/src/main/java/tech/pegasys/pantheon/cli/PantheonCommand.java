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
package tech.pegasys.pantheon.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.getDefaultPantheonDataPath;
import static tech.pegasys.pantheon.cli.NetworkName.MAINNET;
import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT;
import static tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis.DEFAULT_JSON_RPC_APIS;
import static tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT;
import static tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer.DEFAULT_PORT;
import static tech.pegasys.pantheon.metrics.MetricCategory.DEFAULT_METRIC_CATEGORIES;
import static tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;
import static tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration.createDefault;

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.cli.custom.CorsAllowedOriginsProperty;
import tech.pegasys.pantheon.cli.custom.EnodeToURIPropertyConverter;
import tech.pegasys.pantheon.cli.custom.JsonRPCWhitelistHostsProperty;
import tech.pegasys.pantheon.cli.custom.RpcAuthFileValidator;
import tech.pegasys.pantheon.cli.rlp.RLPSubCommand;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfigurationBuilder;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.InvalidConfigurationException;
import tech.pegasys.pantheon.util.PermissioningConfigurationValidator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Suppliers;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@SuppressWarnings("FieldCanBeLocal") // because Picocli injected fields report false positives
@Command(
    description = "This command runs the Pantheon Ethereum client full node.",
    abbreviateSynopsis = true,
    name = "pantheon",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Pantheon is licensed under the Apache License 2.0")
public class PantheonCommand implements DefaultCommandValues, Runnable {

  private final Logger logger;

  private CommandLine commandLine;

  public static class RpcApisConverter implements ITypeConverter<RpcApi> {

    @Override
    public RpcApi convert(final String name) throws RpcApisConversionException {
      final String uppercaseName = name.trim().toUpperCase();

      return Stream.<Function<String, Optional<RpcApi>>>of(
              RpcApis::valueOf, CliqueRpcApis::valueOf, IbftRpcApis::valueOf)
          .map(f -> f.apply(uppercaseName))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst()
          .orElseThrow(() -> new RpcApisConversionException("Invalid value: " + name));
    }
  }

  public static class RpcApisConversionException extends Exception {

    RpcApisConversionException(final String s) {
      super(s);
    }
  }

  private final BlockImporter blockImporter;

  private final PantheonControllerBuilder controllerBuilder;
  private final SynchronizerConfiguration.Builder synchronizerConfigurationBuilder;
  private final RunnerBuilder runnerBuilder;

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  // Property to indicate whether Pantheon has been launched via docker
  private final boolean isDocker = Boolean.getBoolean("pantheon.docker");

  // CLI options defined by user at runtime.
  // Options parsing is done with CLI library Picocli https://picocli.info/

  // Completely disables P2P within Pantheon.
  @Option(
      names = {"--p2p-enabled"},
      description = "Enable P2P functionality (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean p2pEnabled = true;

  // Boolean option to indicate if peers should NOT be discovered, default to false indicates that
  // the peers should be discovered by default.
  //
  // This negative option is required because of the nature of the option that is true when
  // added on the command line. You can't do --option=false, so false is set as default
  // and you have not to set the option at all if you want it false.
  // This seems to be the only way it works with Picocli.
  // Also many other software use the same negative option scheme for false defaults
  // meaning that it's probably the right way to handle disabling options.
  @Option(
      names = {"--discovery-enabled"},
      description = "Enable P2P peer discovery (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean peerDiscoveryEnabled = true;

  // A list of bootstrap nodes can be passed
  // and a hardcoded list will be used otherwise by the Runner.
  // NOTE: we have no control over default value here.
  @Option(
      names = {"--bootnodes"},
      paramLabel = "<enode://id@host:port>",
      description =
          "Comma separated enode URLs for P2P discovery bootstrap. "
              + "Default is a predefined list.",
      split = ",",
      arity = "0..*",
      converter = EnodeToURIPropertyConverter.class)
  private final Collection<URI> bootNodes = null;

  @Option(
      names = {"--max-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum P2P peer connections that can be established (default: ${DEFAULT-VALUE})")
  private final Integer maxPeers = DEFAULT_MAX_PEERS;

  @Option(
      names = {"--banned-node-ids", "--banned-node-id"},
      paramLabel = MANDATORY_NODE_ID_FORMAT_HELP,
      description = "A list of node IDs to ban from the P2P network.",
      split = ",",
      arity = "1..*")
  private final Collection<String> bannedNodeIds = new ArrayList<>();

  @Option(
      hidden = true,
      names = {"--sync-mode"},
      paramLabel = MANDATORY_MODE_FORMAT_HELP,
      description =
          "Synchronization mode, possible values are ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
  private final SyncMode syncMode = DEFAULT_SYNC_MODE;

  @Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: MAINNET)")
  private final NetworkName network = null;

  @Option(
      names = {"--p2p-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for P2P peer discovery to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--p2p-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for P2P peer discovery to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer p2pPort = DEFAULT_PORT;

  @Option(
      names = {"--network-id"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "P2P network identifier. (default: the selected network chain ID or custom genesis chain ID)",
      arity = "1")
  private final Integer networkId = null;

  @Option(
      names = {"--rpc-http-enabled"},
      description = "Set to start the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpEnabled = false;

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

  // A list of origins URLs that are accepted by the JsonRpcHttpServer (CORS)
  @Option(
      names = {"--rpc-http-cors-origins"},
      description = "Comma separated origin domain URLs for CORS validation (default: none)")
  private final CorsAllowedOriginsProperty rpcHttpCorsAllowedOrigins =
      new CorsAllowedOriginsProperty();

  @Option(
      names = {"--rpc-http-api", "--rpc-http-apis"},
      paramLabel = "<api name>",
      split = ",",
      arity = "1..*",
      converter = RpcApisConverter.class,
      description =
          "Comma separated list of APIs to enable on JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Collection<RpcApi> rpcHttpApis = DEFAULT_JSON_RPC_APIS;

  @Option(
      names = {"--rpc-http-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpAuthenticationEnabled = false;

  @Option(
      names = {"--rpc-ws-enabled"},
      description = "Set to start the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsEnabled = false;

  @Option(
      names = {"--rpc-ws-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcWsHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--rpc-ws-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer rpcWsPort = DEFAULT_WEBSOCKET_PORT;

  @Option(
      names = {"--rpc-ws-api", "--rpc-ws-apis"},
      paramLabel = "<api name>",
      split = ",",
      arity = "1..*",
      converter = RpcApisConverter.class,
      description =
          "Comma separated list of APIs to enable on JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Collection<RpcApi> rpcWsApis = DEFAULT_JSON_RPC_APIS;

  private Long rpcWsRefreshDelay;

  @Option(
      names = {"--rpc-ws-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsAuthenticationEnabled = false;

  @Option(
      names = {"--metrics-enabled"},
      description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsEnabled = false;

  @Option(
      names = {"--metrics-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--metrics-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPort = DEFAULT_METRICS_PORT;

  @Option(
      names = {"--metrics-category", "--metrics-categories"},
      paramLabel = "<category name>",
      split = ",",
      arity = "1..*",
      description =
          "Comma separated list of categories to track metrics for (default: ${DEFAULT-VALUE})")
  private final Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;

  @Option(
      names = {"--metrics-push-enabled"},
      description = "Enable the metrics push gateway integration (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsPushEnabled = false;

  @Option(
      names = {"--metrics-push-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPushHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--metrics-push-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPushPort = DEFAULT_METRICS_PUSH_PORT;

  @Option(
      names = {"--metrics-push-interval"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Interval in seconds to push metrics when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer metricsPushInterval = 15;

  @Option(
      names = {"--metrics-push-prometheus-job"},
      description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPrometheusJob = "pantheon-client";

  @Option(
      names = {"--host-whitelist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to whitelist for JSON-RPC access, or * or all to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCWhitelistHostsProperty hostsWhitelist = new JsonRPCWhitelistHostsProperty();

  @Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description =
          "Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO)")
  private final Level logLevel = null;

  @Option(
      names = {"--miner-enabled"},
      description = "Set if node will perform mining (default: ${DEFAULT-VALUE})")
  private final Boolean isMiningEnabled = false;

  @Option(
      names = {"--miner-coinbase"},
      description =
          "Account to which mining rewards are paid. You must specify a valid coinbase if "
              + "mining is enabled using --miner-enabled option",
      arity = "1")
  private final Address coinbase = null;

  @Option(
      names = {"--min-gas-price"},
      description =
          "Minimum price (in Wei) offered by a transaction for it to be included in a mined "
              + "block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Wei minTransactionGasPrice = DEFAULT_MIN_TRANSACTION_GAS_PRICE;

  @Option(
      names = {"--miner-extra-data"},
      description =
          "A hex string representing the (32) bytes to be included in the extra data "
              + "field of a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final BytesValue extraData = DEFAULT_EXTRA_DATA;

  @Option(
      names = {"--permissions-nodes-enabled"},
      description = "Enable node level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesEnabled = false;

  @Option(
      names = {"--permissions-accounts-enabled"},
      description = "Enable account level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsEnabled = false;

  @Option(
      names = {"--privacy-enabled"},
      description = "Enable private transactions (default: ${DEFAULT-VALUE})")
  private final Boolean isPrivacyEnabled = false;

  @Option(
      names = {"--privacy-url"},
      description = "The URL on which the enclave is running")
  private final URI privacyUrl = PrivacyParameters.DEFAULT_ENCLAVE_URL;

  @Option(
      names = {"--privacy-precompiled-address"},
      description =
          "The address to which the privacy pre-compiled contract will be mapped to (default: ${DEFAULT-VALUE})")
  private final Integer privacyPrecompiledAddress = Address.PRIVACY;

  // Inner class so we can get to loggingLevel.
  public class PantheonExceptionHandler
      extends CommandLine.AbstractHandler<List<Object>, PantheonExceptionHandler>
      implements CommandLine.IExceptionHandler2<List<Object>> {

    @Override
    public List<Object> handleParseException(final ParameterException ex, final String[] args) {
      if (logLevel != null && Level.DEBUG.isMoreSpecificThan(logLevel)) {
        ex.printStackTrace(err());
      } else {
        err().println(ex.getMessage());
      }
      if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, err())) {
        ex.getCommandLine().usage(err(), ansi());
      }
      return returnResultOrExit(null);
    }

    @Override
    public List<Object> handleExecutionException(
        final ExecutionException ex, final CommandLine.ParseResult parseResult) {
      return throwOrExit(ex);
    }

    @Override
    protected PantheonExceptionHandler self() {
      return this;
    }
  }

  private final Supplier<PantheonExceptionHandler> exceptionHandlerSupplier =
      Suppliers.memoize(PantheonExceptionHandler::new);
  private final Supplier<MetricsSystem> metricsSystem =
      Suppliers.memoize(() -> PrometheusMetricsSystem.init(metricsConfiguration()));

  public PantheonCommand(
      final Logger logger,
      final BlockImporter blockImporter,
      final RunnerBuilder runnerBuilder,
      final PantheonControllerBuilder controllerBuilder,
      final SynchronizerConfiguration.Builder synchronizerConfigurationBuilder) {
    this.logger = logger;
    this.blockImporter = blockImporter;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilder = controllerBuilder;
    this.synchronizerConfigurationBuilder = synchronizerConfigurationBuilder;
  }

  private StandaloneCommand standaloneCommands;

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final PantheonExceptionHandler exceptionHandler,
      final InputStream in,
      final String... args) {

    commandLine = new CommandLine(this);

    commandLine.setCaseInsensitiveEnumValuesAllowed(true);

    standaloneCommands = new StandaloneCommand();

    if (isFullInstantiation()) {
      commandLine.addMixin("standaloneCommands", standaloneCommands);
    }

    commandLine.addSubcommand(
        BlocksSubCommand.COMMAND_NAME, new BlocksSubCommand(blockImporter, resultHandler.out()));
    commandLine.addSubcommand(
        PublicKeySubCommand.COMMAND_NAME, new PublicKeySubCommand(resultHandler.out()));
    commandLine.addSubcommand(
        PasswordSubCommand.COMMAND_NAME, new PasswordSubCommand(resultHandler.out()));
    commandLine.addSubcommand(
        RLPSubCommand.COMMAND_NAME, new RLPSubCommand(resultHandler.out(), in));

    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(BytesValue.class, BytesValue::fromHexString);
    commandLine.registerConverter(Level.class, Level::valueOf);
    commandLine.registerConverter(SyncMode.class, SyncMode::fromString);
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));

    // Create a handler that will search for a config file option and use it for default values
    // and eventually it will run regular parsing of the remaining options.
    final ConfigOptionSearchAndRunHandler configParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler, exceptionHandler, CONFIG_FILE_OPTION_NAME, isDocker);
    commandLine.parseWithHandlers(configParsingHandler, exceptionHandler, args);
  }

  @Override
  public void run() {
    // set log level per CLI flags
    if (logLevel != null) {
      System.out.println("Setting logging level to " + logLevel.name());
      Configurator.setAllLevels("", logLevel);
    }

    // Check that P2P options are able to work or send an error
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2pEnabled,
        Arrays.asList(
            "--bootnodes",
            "--discovery-enabled",
            "--max-peers",
            "--banned-node-id",
            "--banned-node-ids"));

    // Check that mining options are able to work or send an error
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--miner-enabled",
        !isMiningEnabled,
        Arrays.asList("--miner-coinbase", "--min-gas-price", "--miner-extra-data"));

    //noinspection ConstantConditions
    if (isMiningEnabled && coinbase == null) {
      throw new ParameterException(
          this.commandLine,
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled)"
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }

    if (permissionsConfigFile() != null) {
      if (!permissionsAccountsEnabled && !permissionsNodesEnabled) {
        logger.warn(
            "Permissions config file set {} but no permissions enabled", permissionsConfigFile());
      }
    }

    final EthNetworkConfig ethNetworkConfig = updateNetworkConfig(getNetwork());
    try {
      final JsonRpcConfiguration jsonRpcConfiguration = jsonRpcConfiguration();
      final WebSocketConfiguration webSocketConfiguration = webSocketConfiguration();
      final Optional<PermissioningConfiguration> permissioningConfiguration =
          permissioningConfiguration();
      permissioningConfiguration.ifPresent(
          p -> ensureAllBootnodesAreInWhitelist(ethNetworkConfig, p));

      synchronize(
          buildController(),
          p2pEnabled,
          peerDiscoveryEnabled,
          ethNetworkConfig.getBootNodes(),
          maxPeers,
          p2pHost,
          p2pPort,
          jsonRpcConfiguration,
          webSocketConfiguration,
          metricsConfiguration(),
          permissioningConfiguration);
    } catch (Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  private NetworkName getNetwork() {
    //noinspection ConstantConditions network is not always null but injected by PicoCLI if used
    return network == null ? MAINNET : network;
  }

  private void ensureAllBootnodesAreInWhitelist(
      final EthNetworkConfig ethNetworkConfig,
      final PermissioningConfiguration permissioningConfiguration) {
    try {
      PermissioningConfigurationValidator.areAllBootnodesAreInWhitelist(
          ethNetworkConfig, permissioningConfiguration);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage());
    }
  }

  PantheonController<?> buildController() {
    try {
      return controllerBuilder
          .synchronizerConfiguration(buildSyncConfig())
          .homePath(dataDir())
          .ethNetworkConfig(updateNetworkConfig(getNetwork()))
          .syncWithOttoman(false) // ottoman feature is still there but it's now removed from CLI
          .miningParameters(
              new MiningParameters(coinbase, minTransactionGasPrice, extraData, isMiningEnabled))
          .devMode(NetworkName.DEV.equals(getNetwork()))
          .nodePrivateKeyFile(nodePrivateKeyFile())
          .metricsSystem(metricsSystem.get())
          .privacyParameters(privacyParameters())
          .build();
    } catch (final InvalidConfigurationException e) {
      throw new ExecutionException(this.commandLine, e.getMessage());
    } catch (final IOException e) {
      throw new ExecutionException(this.commandLine, "Invalid path", e);
    }
  }

  private String getPermissionsConfigFile() {

    return permissionsConfigFile() != null
        ? permissionsConfigFile()
        : dataDir().toAbsolutePath()
            + System.getProperty("file.separator")
            + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION;
  }

  private JsonRpcConfiguration jsonRpcConfiguration() throws Exception {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-enabled",
        !isRpcHttpEnabled,
        Arrays.asList(
            "--rpc-http-api",
            "--rpc-http-apis",
            "--rpc-http-cors-origins",
            "--rpc-http-host",
            "--rpc-http-port",
            "--rpc-http-authentication-enabled",
            "--rpc-http-authentication-credentials-file"));

    if (isRpcHttpAuthenticationEnabled && rpcHttpAuthenticationCredentialsFile() == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file");
    }

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(isRpcHttpEnabled);
    jsonRpcConfiguration.setHost(rpcHttpHost);
    jsonRpcConfiguration.setPort(rpcHttpPort);
    jsonRpcConfiguration.setCorsAllowedDomains(rpcHttpCorsAllowedOrigins);
    jsonRpcConfiguration.setRpcApis(rpcHttpApis);
    jsonRpcConfiguration.setHostsWhitelist(hostsWhitelist);
    jsonRpcConfiguration.setAuthenticationEnabled(isRpcHttpAuthenticationEnabled);
    jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile());
    return jsonRpcConfiguration;
  }

  private WebSocketConfiguration webSocketConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-ws-enabled",
        !isRpcWsEnabled,
        Arrays.asList(
            "--rpc-ws-api",
            "--rpc-ws-apis",
            "--rpc-ws-host",
            "--rpc-ws-port",
            "--rpc-ws-authentication-enabled",
            "--rpc-ws-authentication-credentials-file"));

    if (isRpcWsAuthenticationEnabled && rpcWsAuthenticationCredentialsFile() == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file");
    }

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isRpcWsEnabled);
    webSocketConfiguration.setHost(rpcWsHost);
    webSocketConfiguration.setPort(rpcWsPort);
    webSocketConfiguration.setRpcApis(rpcWsApis);
    webSocketConfiguration.setAuthenticationEnabled(isRpcWsAuthenticationEnabled);
    webSocketConfiguration.setAuthenticationCredentialsFile(rpcWsAuthenticationCredentialsFile());
    webSocketConfiguration.setHostsWhitelist(hostsWhitelist);
    return webSocketConfiguration;
  }

  MetricsConfiguration metricsConfiguration() {
    if (isMetricsEnabled && isMetricsPushEnabled) {
      throw new ParameterException(
          this.commandLine,
          "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
              + "time.  Please refer to CLI reference for more details about this constraint.");
    }

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-enabled",
        !isMetricsEnabled,
        Arrays.asList("--metrics-host", "--metrics-port"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !isMetricsPushEnabled,
        Arrays.asList(
            "--metrics-push-host",
            "--metrics-push-port",
            "--metrics-push-interval",
            "--metrics-push-prometheus-job"));

    final MetricsConfiguration metricsConfiguration = createDefault();
    metricsConfiguration.setEnabled(isMetricsEnabled);
    metricsConfiguration.setHost(metricsHost);
    metricsConfiguration.setPort(metricsPort);
    metricsConfiguration.setMetricCategories(metricCategories);
    metricsConfiguration.setPushEnabled(isMetricsPushEnabled);
    metricsConfiguration.setPushHost(metricsPushHost);
    metricsConfiguration.setPushPort(metricsPushPort);
    metricsConfiguration.setPushInterval(metricsPushInterval);
    metricsConfiguration.setPrometheusJob(metricsPrometheusJob);
    metricsConfiguration.setHostsWhitelist(hostsWhitelist);
    return metricsConfiguration;
  }

  private Optional<PermissioningConfiguration> permissioningConfiguration() throws Exception {

    if (!permissionsAccountsEnabled && !permissionsNodesEnabled) {
      if (rpcHttpApis.contains(RpcApis.PERM) || rpcWsApis.contains(RpcApis.PERM)) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfigurationFromToml(
            getPermissionsConfigFile(), permissionsNodesEnabled, permissionsAccountsEnabled);
    return Optional.of(permissioningConfiguration);
  }

  private PrivacyParameters privacyParameters() throws IOException {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-enabled",
        !isPrivacyEnabled,
        Arrays.asList(
            "--privacy-url", "--privacy-public-key-file", "--privacy-precompiled-address"));

    final PrivacyParameters privacyParameters = PrivacyParameters.noPrivacy();
    if (isPrivacyEnabled) {
      privacyParameters.setUrl(privacyUrl.toString());
      if (privacyPublicKeyFile() != null) {
        privacyParameters.setPublicKeyUsingFile(privacyPublicKeyFile());
      } else {
        throw new ParameterException(
            commandLine, "Please specify Enclave public key file path to enable privacy");
      }
      privacyParameters.setPrivacyAddress(privacyPrecompiledAddress);
      privacyParameters.enablePrivateDB(dataDir());
    }
    return privacyParameters;
  }

  private SynchronizerConfiguration buildSyncConfig() {
    synchronizerConfigurationBuilder.syncMode(syncMode);
    synchronizerConfigurationBuilder.maxTrailingPeers(MAX_TRAILING_PEERS);
    return synchronizerConfigurationBuilder.build();
  }

  // Blockchain synchronisation from peers.
  private void synchronize(
      final PantheonController<?> controller,
      final boolean p2pEnabled,
      final boolean peerDiscoveryEnabled,
      final Collection<?> bootstrapNodes,
      final int maxPeers,
      final String discoveryHost,
      final int discoveryPort,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration) {

    checkNotNull(runnerBuilder);

    permissioningConfiguration.ifPresent(runnerBuilder::permissioningConfiguration);

    final Runner runner =
        runnerBuilder
            .vertx(Vertx.vertx())
            .pantheonController(controller)
            .p2pEnabled(p2pEnabled)
            .discovery(peerDiscoveryEnabled)
            .bootstrapPeers(bootstrapNodes)
            .discoveryHost(discoveryHost)
            .discoveryPort(discoveryPort)
            .maxPeers(maxPeers)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .dataDir(dataDir())
            .bannedNodeIds(bannedNodeIds)
            .metricsSystem(metricsSystem.get())
            .metricsConfiguration(metricsConfiguration)
            .build();

    addShutdownHook(runner);
    runner.execute();
  }

  private void addShutdownHook(final Runner runner) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    runner.close();

                    LogManager.shutdown();
                  } catch (final Exception e) {
                    logger.error("Failed to stop Pantheon");
                  }
                }));
  }

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

  private EthNetworkConfig updateNetworkConfig(final NetworkName network) {
    final EthNetworkConfig.Builder builder =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network));

    // custom genesis file use comes with specific default values for the genesis file itself
    // but also for the network id and the bootnodes list.
    final File genesisFile = genesisFile();
    if (genesisFile != null) {

      //noinspection ConstantConditions network is not always null but injected by PicoCLI if used
      if (this.network != null) {
        // We check if network option was really provided by user and not only looking at the
        // default value.
        // if user provided it and provided the genesis file option at the same time, it raises a
        // conflict error
        throw new ParameterException(
            this.commandLine,
            "--network option and --genesis-file option can't be used at the same time.  Please "
                + "refer to CLI reference for more details about this constraint.");
      }

      builder.setGenesisConfig(genesisConfig());

      if (networkId == null) {
        // if no network id option is defined on the CLI we have to set a default value from the
        // genesis file.
        // We do the genesis parsing only in this case as we already have network id constants
        // for known networks to speed up the process.
        // Also we have to parse the genesis as we don't already have a parsed version at this
        // stage.
        // If no chain id is found in the genesis as it's an optional, we use mainnet network id.
        try {
          final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(genesisConfig());
          builder.setNetworkId(
              genesisConfigFile
                  .getConfigOptions()
                  .getChainId()
                  .orElse(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId()));
        } catch (final DecodeException e) {
          throw new ParameterException(
              this.commandLine, String.format("Unable to parse genesis file %s.", genesisFile), e);
        }
      }

      if (bootNodes == null) {
        // We default to an empty bootnodes list if the option is not provided on CLI because
        // mainnet bootnodes won't work as the default value for a custom genesis,
        // so it's better to have an empty list as default value that forces to create a custom one
        // than a useless one that may make user think that it can work when it can't.
        builder.setBootNodes(new ArrayList<>());
      }
    }

    if (networkId != null) {
      builder.setNetworkId(networkId);
    }

    if (bootNodes != null) {
      builder.setBootNodes(bootNodes);
    }

    return builder.build();
  }

  private String genesisConfig() {
    try {
      return Resources.toString(genesisFile().toURI().toURL(), UTF_8);
    } catch (final IOException e) {
      throw new ParameterException(
          this.commandLine, String.format("Unable to load genesis file %s.", genesisFile()), e);
    }
  }

  private File genesisFile() {
    if (isFullInstantiation()) {
      return standaloneCommands.genesisFile;
    } else if (isDocker) {
      final File genesisFile = new File(DOCKER_GENESIS_LOCATION);
      if (genesisFile.exists()) {
        return genesisFile;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  private Path dataDir() {
    if (isFullInstantiation()) {
      return standaloneCommands.dataPath.toAbsolutePath();
    } else if (isDocker) {
      return Paths.get(DOCKER_DATADIR_LOCATION);
    } else {
      return getDefaultPantheonDataPath(this);
    }
  }

  private File nodePrivateKeyFile() {
    File nodePrivateKeyFile = null;
    if (isFullInstantiation()) {
      nodePrivateKeyFile = standaloneCommands.nodePrivateKeyFile;
    }

    return nodePrivateKeyFile != null
        ? nodePrivateKeyFile
        : KeyPairUtil.getDefaultKeyFile(dataDir());
  }

  private File privacyPublicKeyFile() {
    if (isDocker) {
      final File keyFile = new File(DOCKER_PRIVACY_PUBLIC_KEY_FILE);
      if (keyFile.exists()) {
        return keyFile;
      } else {
        return null;
      }
    } else {
      return standaloneCommands.privacyPublicKeyFile;
    }
  }

  private String rpcHttpAuthenticationCredentialsFile() throws Exception {
    String filename = null;
    if (isFullInstantiation()) {
      filename = standaloneCommands.rpcHttpAuthenticationCredentialsFile;
    } else if (isDocker) {
      final File authFile = new File(DOCKER_RPC_HTTP_AUTHENTICATION_CREDENTIALS_FILE_LOCATION);
      if (authFile.exists()) {
        filename = authFile.getAbsolutePath();
      }
    }

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "HTTP");
    }
    return filename;
  }

  private String rpcWsAuthenticationCredentialsFile() {
    String filename = null;
    if (isFullInstantiation()) {
      filename = standaloneCommands.rpcWsAuthenticationCredentialsFile;
    } else if (isDocker) {
      final File authFile = new File(DOCKER_RPC_WS_AUTHENTICATION_CREDENTIALS_FILE_LOCATION);
      if (authFile.exists()) {
        filename = authFile.getAbsolutePath();
      }
    }

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "WS");
    }
    return filename;
  }

  private String permissionsConfigFile() {
    String filename = null;
    if (isFullInstantiation()) {
      filename = standaloneCommands.permissionsConfigFile;
    } else if (isDocker) {
      final File file = new File(DOCKER_PERMISSIONS_CONFIG_FILE_LOCATION);
      if (file.exists()) {
        filename = file.getAbsolutePath();
      }
    }

    return filename;
  }

  private boolean isFullInstantiation() {
    return !isDocker;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem.get();
  }

  public PantheonExceptionHandler exceptionHandler() {
    return exceptionHandlerSupplier.get();
  }
}
