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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static tech.pegasys.pantheon.cli.CommandLineUtils.checkOptionDependencies;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.getDefaultPantheonDataPath;
import static tech.pegasys.pantheon.cli.NetworkName.MAINNET;
import static tech.pegasys.pantheon.controller.PantheonController.DATABASE_PATH;
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
import tech.pegasys.pantheon.cli.PublicKeySubCommand.KeyLoader;
import tech.pegasys.pantheon.cli.converter.RpcApisConverter;
import tech.pegasys.pantheon.cli.custom.CorsAllowedOriginsProperty;
import tech.pegasys.pantheon.cli.custom.JsonRPCWhitelistHostsProperty;
import tech.pegasys.pantheon.cli.custom.RpcAuthFileValidator;
import tech.pegasys.pantheon.cli.rlp.RLPSubCommand;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.TrailingPeerRequirements;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.peers.StaticNodesParser;
import tech.pegasys.pantheon.ethereum.permissioning.LocalPermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfigurationBuilder;
import tech.pegasys.pantheon.ethereum.permissioning.SmartContractPermissioningConfiguration;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.metrics.vertx.VertxMetricsAdapterFactory;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.InvalidConfigurationException;
import tech.pegasys.pantheon.util.PermissioningConfigurationValidator;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;
import tech.pegasys.pantheon.util.number.PositiveNumber;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.metrics.MetricsOptions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
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

  private final BlockImporter blockImporter;

  private final SynchronizerConfiguration.Builder synchronizerConfigurationBuilder;
  private final EthereumWireProtocolConfiguration.Builder ethereumWireConfigurationBuilder;
  private final RocksDbConfiguration.Builder rocksDbConfigurationBuilder;
  private final RunnerBuilder runnerBuilder;
  private final PantheonController.Builder controllerBuilderFactory;

  protected KeyLoader getKeyLoader() {
    return KeyPairUtil::loadKeyPair;
  }

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
      arity = "0..*")
  void setBootnodes(final List<String> values) {
    try {
      bootNodes =
          values.stream().map((s) -> EnodeURL.fromString(s).toURI()).collect(Collectors.toList());
    } catch (final IllegalArgumentException e) {
      throw new ParameterException(commandLine, e.getMessage());
    }
  }

  private Collection<URI> bootNodes = null;

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
      names = {"--sync-mode"},
      paramLabel = MANDATORY_MODE_FORMAT_HELP,
      description =
          "Synchronization mode, possible values are ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
  private final SyncMode syncMode = DEFAULT_SYNC_MODE;

  @Option(
      names = {"--fast-sync-min-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Minimum number of peers required before starting fast sync. (default: ${DEFAULT-VALUE})")
  private final Integer fastSyncMinPeerCount = FAST_SYNC_MIN_PEER_COUNT;

  @Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: MAINNET)")
  private final NetworkName network = null;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--p2p-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Ip address this node advertises to its peers (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--p2p-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port on which to listen for p2p communication (default: ${DEFAULT-VALUE})",
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

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
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

  @Option(
      names = {"--rpc-ws-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsAuthenticationEnabled = false;

  @Option(
      names = {"--metrics-enabled"},
      description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsEnabled = false;

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
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

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
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

  @SuppressWarnings("FieldMayBeFinal") // Because PicoCLI requires Strings to not be final.
  @Option(
      names = {"--metrics-push-prometheus-job"},
      description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPrometheusJob = "pantheon-client";

  @Option(
      names = {"--host-whitelist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to whitelist for JSON-RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
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
      names = {"--permissions-nodes-config-file-enabled"},
      description = "Enable node level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesEnabled = false;

  @Option(
      names = {"--permissions-accounts-config-file-enabled"},
      description = "Enable account level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsEnabled = false;

  @Option(
      names = {"--permissions-nodes-contract-address"},
      description = "Address of the node permissioning smart contract",
      arity = "1")
  private final Address permissionsNodesContractAddress = null;

  @Option(
      names = {"--permissions-nodes-contract-enabled"},
      description = "Enable node level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesContractEnabled = false;

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

  @Option(
      names = {"--tx-pool-max-size"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum number of pending transactions that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer txPoolMaxSize = PendingTransactions.MAX_PENDING_TRANSACTIONS;

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
      final PantheonController.Builder controllerBuilderFactory,
      final SynchronizerConfiguration.Builder synchronizerConfigurationBuilder,
      final EthereumWireProtocolConfiguration.Builder ethereumWireConfigurationBuilder,
      final RocksDbConfiguration.Builder rocksDbConfigurationBuilder) {
    this.logger = logger;
    this.blockImporter = blockImporter;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilderFactory = controllerBuilderFactory;
    this.synchronizerConfigurationBuilder = synchronizerConfigurationBuilder;
    this.ethereumWireConfigurationBuilder = ethereumWireConfigurationBuilder;
    this.rocksDbConfigurationBuilder = rocksDbConfigurationBuilder;
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
        PublicKeySubCommand.COMMAND_NAME,
        new PublicKeySubCommand(resultHandler.out(), getKeyLoader()));
    commandLine.addSubcommand(
        PasswordSubCommand.COMMAND_NAME, new PasswordSubCommand(resultHandler.out()));
    commandLine.addSubcommand(
        RLPSubCommand.COMMAND_NAME, new RLPSubCommand(resultHandler.out(), in));

    commandLine.registerConverter(Address.class, Address::fromHexStringStrict);
    commandLine.registerConverter(BytesValue.class, BytesValue::fromHexString);
    commandLine.registerConverter(Level.class, Level::valueOf);
    commandLine.registerConverter(SyncMode.class, SyncMode::fromString);
    commandLine.registerConverter(UInt256.class, (arg) -> UInt256.of(new BigInteger(arg)));
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));
    commandLine.registerConverter(PositiveNumber.class, PositiveNumber::fromString);

    // Add performance options
    UnstableOptionsSubCommand.createUnstableOptions(
        commandLine,
        ImmutableMap.of(
            "Synchronizer",
            synchronizerConfigurationBuilder,
            "RocksDB",
            rocksDbConfigurationBuilder,
            "Ethereum Wire Protocol",
            ethereumWireConfigurationBuilder));

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
    checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2pEnabled,
        asList(
            "--bootnodes",
            "--discovery-enabled",
            "--max-peers",
            "--banned-node-id",
            "--banned-node-ids"));

    // Check that mining options are able to work or send an error
    checkOptionDependencies(
        logger,
        commandLine,
        "--miner-enabled",
        !isMiningEnabled,
        asList("--miner-coinbase", "--min-gas-price", "--miner-extra-data"));

    checkOptionDependencies(
        logger,
        commandLine,
        "--sync-mode",
        !SyncMode.FAST.equals(syncMode),
        singletonList("--fast-sync-min-peers"));

    //noinspection ConstantConditions
    if (isMiningEnabled && coinbase == null) {
      throw new ParameterException(
          this.commandLine,
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled)"
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }

    final EthNetworkConfig ethNetworkConfig = updateNetworkConfig(getNetwork());
    try {
      final JsonRpcConfiguration jsonRpcConfiguration = jsonRpcConfiguration();
      final WebSocketConfiguration webSocketConfiguration = webSocketConfiguration();
      final Optional<PermissioningConfiguration> permissioningConfiguration =
          permissioningConfiguration();

      final Collection<EnodeURL> staticNodes = loadStaticNodes();
      logger.info("Connecting to {} static nodes.", staticNodes.size());
      logger.trace("Static Nodes = {}", staticNodes);

      permissioningConfiguration
          .flatMap(PermissioningConfiguration::getLocalConfig)
          .ifPresent(p -> ensureAllNodesAreInWhitelist(ethNetworkConfig.getBootNodes(), p));

      permissioningConfiguration
          .flatMap(PermissioningConfiguration::getLocalConfig)
          .ifPresent(
              p ->
                  ensureAllNodesAreInWhitelist(
                      staticNodes.stream().map(EnodeURL::toURI).collect(Collectors.toList()), p));

      synchronize(
          buildController(),
          p2pEnabled,
          peerDiscoveryEnabled,
          ethNetworkConfig,
          maxPeers,
          p2pHost,
          p2pPort,
          jsonRpcConfiguration,
          webSocketConfiguration,
          metricsConfiguration(),
          permissioningConfiguration,
          staticNodes);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  private NetworkName getNetwork() {
    //noinspection ConstantConditions network is not always null but injected by PicoCLI if used
    return network == null ? MAINNET : network;
  }

  private void ensureAllNodesAreInWhitelist(
      final Collection<URI> enodeAddresses,
      final LocalPermissioningConfiguration permissioningConfiguration) {
    try {
      PermissioningConfigurationValidator.areAllNodesAreInWhitelist(
          enodeAddresses, permissioningConfiguration);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage());
    }
  }

  PantheonController<?> buildController() {
    try {
      return controllerBuilderFactory
          .fromEthNetworkConfig(updateNetworkConfig(getNetwork()))
          .synchronizerConfiguration(buildSyncConfig())
          .ethereumWireProtocolConfiguration(ethereumWireConfigurationBuilder.build())
          .rocksdDbConfiguration(buildRocksDbConfiguration())
          .dataDirectory(dataDir())
          .miningParameters(
              new MiningParameters(coinbase, minTransactionGasPrice, extraData, isMiningEnabled))
          .maxPendingTransactions(txPoolMaxSize)
          .nodePrivateKeyFile(nodePrivateKeyFile())
          .metricsSystem(metricsSystem.get())
          .privacyParameters(privacyParameters())
          .clock(Clock.systemUTC())
          .build();
    } catch (final InvalidConfigurationException e) {
      throw new ExecutionException(this.commandLine, e.getMessage());
    } catch (final IOException e) {
      throw new ExecutionException(this.commandLine, "Invalid path", e);
    }
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {

    checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-enabled",
        !isRpcHttpEnabled,
        asList(
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
    jsonRpcConfiguration.setRpcApis(rpcHttpApis.stream().distinct().collect(Collectors.toList()));
    jsonRpcConfiguration.setHostsWhitelist(hostsWhitelist);
    jsonRpcConfiguration.setAuthenticationEnabled(isRpcHttpAuthenticationEnabled);
    jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile());
    return jsonRpcConfiguration;
  }

  private WebSocketConfiguration webSocketConfiguration() {

    checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-ws-enabled",
        !isRpcWsEnabled,
        asList(
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

    checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-enabled",
        !isMetricsEnabled,
        asList("--metrics-host", "--metrics-port"));

    checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !isMetricsPushEnabled,
        asList(
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
    final Optional<LocalPermissioningConfiguration> localPermissioningConfigurationOptional;
    final Optional<SmartContractPermissioningConfiguration>
        smartContractPermissioningConfigurationOptional;

    if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
      if (rpcHttpApis.contains(RpcApis.PERM) || rpcWsApis.contains(RpcApis.PERM)) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    if (localPermissionsEnabled()) {
      final Optional<String> nodePermissioningConfigFile =
          Optional.ofNullable(nodePermissionsConfigFile());
      final Optional<String> accountPermissioningConfigFile =
          Optional.ofNullable(accountsPermissionsConfigFile());

      final LocalPermissioningConfiguration localPermissioningConfiguration =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              permissionsNodesEnabled,
              nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath()),
              permissionsAccountsEnabled,
              accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath()));

      localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration);
    } else {
      if (nodePermissionsConfigFile() != null && !permissionsNodesEnabled) {
        logger.warn(
            "Node permissioning config file set {} but no permissions enabled",
            nodePermissionsConfigFile());
      }

      if (accountsPermissionsConfigFile() != null && !permissionsAccountsEnabled) {
        logger.warn(
            "Account permissioning config file set {} but no permissions enabled",
            accountsPermissionsConfigFile());
      }
      localPermissioningConfigurationOptional = Optional.empty();
    }

    if (contractPermissionsEnabled()) {
      if (permissionsNodesContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No contract address specified. Cannot enable contract based permissions.");
      }
      final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
          PermissioningConfigurationBuilder.smartContractPermissioningConfiguration(
              permissionsNodesContractAddress, permissionsNodesContractEnabled);
      smartContractPermissioningConfigurationOptional =
          Optional.of(smartContractPermissioningConfiguration);
    } else {
      if (permissionsNodesContractAddress != null) {
        logger.warn(
            "Smart contract address set {} but no contract permissions enabled",
            permissionsNodesContractAddress);
      }
      smartContractPermissioningConfigurationOptional = Optional.empty();
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            localPermissioningConfigurationOptional,
            smartContractPermissioningConfigurationOptional);

    return Optional.of(permissioningConfiguration);
  }

  private boolean localPermissionsEnabled() {
    return permissionsAccountsEnabled || permissionsNodesEnabled;
  }

  private boolean contractPermissionsEnabled() {
    // TODO add permissionsAccountsContractEnabled
    return permissionsNodesContractEnabled;
  }

  private PrivacyParameters privacyParameters() throws IOException {

    checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-enabled",
        !isPrivacyEnabled,
        asList("--privacy-url", "--privacy-public-key-file", "--privacy-precompiled-address"));

    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    if (isPrivacyEnabled) {
      privacyParametersBuilder.setEnabled(true);
      privacyParametersBuilder.setEnclaveUrl(privacyUrl);
      if (privacyPublicKeyFile() != null) {
        privacyParametersBuilder.setEnclavePublicKeyUsingFile(privacyPublicKeyFile());
      } else {
        throw new ParameterException(
            commandLine, "Please specify Enclave public key file path to enable privacy");
      }
      privacyParametersBuilder.setPrivacyAddress(privacyPrecompiledAddress);
      privacyParametersBuilder.setMetricsSystem(metricsSystem.get());
      privacyParametersBuilder.setDataDir(dataDir());
    }
    return privacyParametersBuilder.build();
  }

  private SynchronizerConfiguration buildSyncConfig() {
    return synchronizerConfigurationBuilder
        .syncMode(syncMode)
        .fastSyncMinimumPeerCount(fastSyncMinPeerCount)
        .maxTrailingPeers(TrailingPeerRequirements.calculateMaxTrailingPeers(maxPeers))
        .build();
  }

  private RocksDbConfiguration buildRocksDbConfiguration() {
    return rocksDbConfigurationBuilder.databaseDir(dataDir().resolve(DATABASE_PATH)).build();
  }

  // Blockchain synchronisation from peers.
  private void synchronize(
      final PantheonController<?> controller,
      final boolean p2pEnabled,
      final boolean peerDiscoveryEnabled,
      final EthNetworkConfig ethNetworkConfig,
      final int maxPeers,
      final String p2pAdvertisedHost,
      final int p2pListenPort,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Collection<EnodeURL> staticNodes) {

    checkNotNull(runnerBuilder);

    permissioningConfiguration.ifPresent(runnerBuilder::permissioningConfiguration);

    final MetricsSystem metricsSystem = this.metricsSystem.get();
    final Runner runner =
        runnerBuilder
            .vertx(Vertx.vertx(createVertxOptions(metricsSystem)))
            .pantheonController(controller)
            .p2pEnabled(p2pEnabled)
            .discovery(peerDiscoveryEnabled)
            .ethNetworkConfig(ethNetworkConfig)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pListenPort(p2pListenPort)
            .maxPeers(maxPeers)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .dataDir(dataDir())
            .bannedNodeIds(bannedNodeIds)
            .metricsSystem(metricsSystem)
            .metricsConfiguration(metricsConfiguration)
            .staticNodes(staticNodes)
            .build();

    addShutdownHook(runner);
    runner.start();
    runner.awaitStop();
  }

  private VertxOptions createVertxOptions(final MetricsSystem metricsSystem) {
    return new VertxOptions()
        .setMetricsOptions(
            new MetricsOptions()
                .setEnabled(true)
                .setFactory(new VertxMetricsAdapterFactory(metricsSystem)));
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
                  .map(BigInteger::intValueExact)
                  .orElse(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId()));
        } catch (final DecodeException e) {
          throw new ParameterException(
              this.commandLine, String.format("Unable to parse genesis file %s.", genesisFile), e);
        } catch (final ArithmeticException e) {
          throw new ParameterException(
              this.commandLine,
              "No networkId specified and chainId in "
                  + "genesis file is too large to be used as a networkId");
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

  File nodePrivateKeyFile() {
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

  private String rpcHttpAuthenticationCredentialsFile() {
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

  private String nodePermissionsConfigFile() {
    return permissionsConfigFile(standaloneCommands.nodePermissionsConfigFile);
  }

  private String accountsPermissionsConfigFile() {
    return permissionsConfigFile(standaloneCommands.accountPermissionsConfigFile);
  }

  private String permissionsConfigFile(final String permissioningFilename) {
    String filename = null;
    if (isFullInstantiation()) {
      filename = permissioningFilename;
    } else if (isDocker) {
      final File file = new File(DOCKER_PERMISSIONS_CONFIG_FILE_LOCATION);
      if (file.exists()) {
        filename = file.getAbsolutePath();
      }
    }
    return filename;
  }

  private String getDefaultPermissioningFilePath() {
    return dataDir().toAbsolutePath()
        + System.getProperty("file.separator")
        + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION;
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

  private Set<EnodeURL> loadStaticNodes() throws IOException {
    final String staticNodesFilname = "static-nodes.json";
    final Path staticNodesPath = dataDir().resolve(staticNodesFilname);

    return StaticNodesParser.fromPath(staticNodesPath);
  }
}
