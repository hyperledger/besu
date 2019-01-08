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
import static tech.pegasys.pantheon.cli.DefaultCommandValues.getDefaultPantheonDataDir;

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.cli.custom.CorsAllowedOriginsProperty;
import tech.pegasys.pantheon.cli.custom.EnodeToURIPropertyConverter;
import tech.pegasys.pantheon.cli.custom.JsonRPCWhitelistHostsProperty;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.util.InvalidConfigurationException;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.DefaultExceptionHandler;
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
  footer = "Pantheon is licensed under the Apache License 2.0"
)
public class PantheonCommand implements DefaultCommandValues, Runnable {

  private static final int DEFAULT_MAX_PEERS = 25;

  // Default should be FAST for the next release
  // but we use FULL for the moment as Fast is still in progress
  private static final SyncMode DEFAULT_SYNC_MODE = SyncMode.FULL;

  private static final String MANDATORY_HOST_AND_PORT_FORMAT_HELP = "<HOST:PORT>";
  private static final String MANDATORY_INTEGER_FORMAT_HELP = "<INTEGER>";
  private static final String MANDATORY_MODE_FORMAT_HELP = "<MODE>";

  private static final Wei DEFAULT_MIN_TRANSACTION_GAS_PRICE = Wei.of(1000);
  private static final BytesValue DEFAULT_EXTRA_DATA = BytesValue.EMPTY;
  private static final long DEFAULT_MAX_REFRESH_DELAY = 3600000;
  private static final long DEFAULT_MIN_REFRESH_DELAY = 1;

  private static final String DOCKER_GENESIS_LOCATION = "/etc/pantheon/genesis.json";
  private static final String DOCKER_DATADIR_LOCATION = "/var/lib/pantheon";

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

  private final MetricsSystem metricsSystem = PrometheusMetricsSystem.init();

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  // Property to indicate whether Pantheon has been launched via docker
  private final boolean isDocker = Boolean.getBoolean("pantheon.docker");

  // CLI options defined by user at runtime.
  // Options parsing is done with CLI library Picocli https://picocli.info/

  @Option(
    names = {"--node-private-key"},
    paramLabel = MANDATORY_PATH_FORMAT_HELP,
    description =
        "the path to the node's private key file (default: a file named \"key\" in the Pantheon data folder)"
  )
  private final File nodePrivateKeyFile = null;

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
    names = {"--no-discovery"},
    description = "Disable p2p peer discovery (default: ${DEFAULT-VALUE})"
  )
  private final Boolean noPeerDiscovery = false;

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
    arity = "1..*",
    converter = EnodeToURIPropertyConverter.class
  )
  private final Collection<URI> bootstrapNodes = null;

  @Option(
    names = {"--max-peers"},
    paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
    description = "Maximum p2p peer connections that can be established (default: ${DEFAULT-VALUE})"
  )
  private final Integer maxPeers = DEFAULT_MAX_PEERS;

  @Option(
    names = {"--max-trailing-peers"},
    paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
    description =
        "Maximum p2p peer connections for peers that are trailing behind our chain head (default: unlimited)"
  )
  private final Integer maxTrailingPeers = Integer.MAX_VALUE;

  @Option(
    names = {"--banned-nodeids"},
    description = "A list of node IDs to ban from the p2p network.",
    split = ",",
    arity = "1..*"
  )
  private final Collection<String> bannedNodeIds = new ArrayList<>();

  // TODO: Re-enable as per NC-1057/NC-1681
  //  @Option(
  //    names = {"--sync-mode"},
  //    paramLabel = MANDATORY_MODE_FORMAT_HELP,
  //    description =
  //        "Synchronization mode (Value can be one of ${COMPLETION-CANDIDATES}, default:
  // ${DEFAULT-VALUE})"
  //  )
  private final SyncMode syncMode = DEFAULT_SYNC_MODE;

  // Boolean option to indicate if the client have to sync against the ottoman test network
  // (see https://github.com/ethereum/EIPs/issues/650).
  @Option(
    names = {"--ottoman"},
    description =
        "Synchronize against the Ottoman test network, only useful if using an iBFT genesis file"
            + " - see https://github.com/ethereum/EIPs/issues/650 (default: ${DEFAULT-VALUE})"
  )
  private final Boolean syncWithOttoman = false;

  @Option(
    names = {"--rinkeby"},
    description =
        "Use the Rinkeby test network"
            + " - see https://github.com/ethereum/EIPs/issues/225 (default: ${DEFAULT-VALUE})"
  )
  private final Boolean rinkeby = false;

  @Option(
    names = {"--ropsten"},
    description = "Use the Ropsten test network (default: ${DEFAULT-VALUE})"
  )
  private final Boolean ropsten = false;

  @Option(
    names = {"--goerli"},
    description = "Use the Goerli test network (default: ${DEFAULT-VALUE})"
  )
  private final Boolean goerli = false;

  @Option(
    names = {"--p2p-listen"},
    paramLabel = MANDATORY_HOST_AND_PORT_FORMAT_HELP,
    description = "Host and port for p2p peers discovery to listen on (default: ${DEFAULT-VALUE})",
    arity = "1"
  )
  private final HostAndPort p2pHostAndPort = getDefaultHostAndPort(DefaultPeer.DEFAULT_PORT);

  @Option(
    names = {"--network-id"},
    paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
    description = "P2P network identifier (default: ${DEFAULT-VALUE})",
    arity = "1"
  )
  private final Integer networkId = null;

  @Option(
    names = {"--rpc-enabled"},
    description = "Set if the JSON-RPC service should be started (default: ${DEFAULT-VALUE})"
  )
  private final Boolean isJsonRpcEnabled = false;

  @Option(
    names = {"--rpc-listen"},
    paramLabel = MANDATORY_HOST_AND_PORT_FORMAT_HELP,
    description = "Host and port for JSON-RPC to listen on (default: ${DEFAULT-VALUE})",
    arity = "1"
  )
  private final HostAndPort rpcHostAndPort =
      getDefaultHostAndPort(JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT);

  // A list of origins URLs that are accepted by the JsonRpcHttpServer (CORS)
  @Option(
    names = {"--rpc-cors-origins"},
    description = "Comma separated origin domain URLs for CORS validation (default: none)"
  )
  private final CorsAllowedOriginsProperty rpcCorsAllowedOrigins = new CorsAllowedOriginsProperty();

  @Option(
    names = {"--rpc-api"},
    paramLabel = "<api name>",
    split = ",",
    arity = "1..*",
    converter = RpcApisConverter.class,
    description = "Comma separated APIs to enable on JSON-RPC channel. default: ${DEFAULT-VALUE}",
    defaultValue = "ETH,NET,WEB3,CLIQUE,IBFT"
  )
  private final Collection<RpcApi> rpcApis = null;

  @Option(
    names = {"--ws-enabled"},
    description =
        "Set if the WS-RPC (WebSocket) service should be started (default: ${DEFAULT-VALUE})"
  )
  private final Boolean isWsRpcEnabled = false;

  @Option(
    names = {"--ws-listen"},
    paramLabel = MANDATORY_HOST_AND_PORT_FORMAT_HELP,
    description = "Host and port for WS-RPC (WebSocket) to listen on (default: ${DEFAULT-VALUE})",
    arity = "1"
  )
  private final HostAndPort wsHostAndPort =
      getDefaultHostAndPort(WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT);

  @Option(
    names = {"--ws-api"},
    paramLabel = "<api name>",
    split = ",",
    arity = "1..*",
    converter = RpcApisConverter.class,
    description = "Comma separated APIs to enable on WebSocket channel. default: ${DEFAULT-VALUE}",
    defaultValue = "ETH,NET,WEB3,CLIQUE,IBFT"
  )
  private final Collection<RpcApi> wsApis = null;

  private Long refreshDelay;

  @Option(
    names = {"--ws-refresh-delay"},
    paramLabel = "<refresh delay>",
    arity = "1",
    description =
        "Refresh delay of websocket subscription sync in milliseconds. "
            + "default: ${DEFAULT-VALUE}",
    defaultValue = "" + WebSocketConfiguration.DEFAULT_WEBSOCKET_REFRESH_DELAY
  )
  private Long configureRefreshDelay(final Long refreshDelay) {
    if (refreshDelay < DEFAULT_MIN_REFRESH_DELAY || refreshDelay > DEFAULT_MAX_REFRESH_DELAY) {
      throw new ParameterException(
          new CommandLine(this),
          String.format(
              "refreshDelay must be a positive integer between %s and %s",
              String.valueOf(DEFAULT_MIN_REFRESH_DELAY),
              String.valueOf(DEFAULT_MAX_REFRESH_DELAY)));
    }
    this.refreshDelay = refreshDelay;
    return refreshDelay;
  }

  @Option(
    names = {"--metrics-enabled"},
    description = "Set if the metrics exporter should be started (default: ${DEFAULT-VALUE})"
  )
  private final Boolean isMetricsEnabled = false;

  @Option(
    names = {"--metrics-listen"},
    paramLabel = MANDATORY_HOST_AND_PORT_FORMAT_HELP,
    description = "Host and port for the metrics exporter to listen on (default: ${DEFAULT-VALUE})",
    arity = "1"
  )
  private final HostAndPort metricsHostAndPort =
      getDefaultHostAndPort(MetricsConfiguration.DEFAULT_METRICS_PORT);

  @Option(
    names = {"--host-whitelist"},
    paramLabel = "<hostname>",
    description =
        "Comma separated list of hostnames to whitelist for RPC access.  default: ${DEFAULT-VALUE}",
    defaultValue = "localhost"
  )
  private final JsonRPCWhitelistHostsProperty hostsWhitelist = new JsonRPCWhitelistHostsProperty();

  @Option(
    names = {"--dev-mode"},
    description =
        "set during development to have a custom genesis with specific chain id "
            + "and reduced difficulty to enable CPU mining (default: ${DEFAULT-VALUE})."
  )
  private final Boolean isDevMode = false;

  @Option(
    names = {"--logging", "-l"},
    description = "Logging verbosity: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO)."
  )
  private final Level logLevel = null;

  @Option(
    names = {"--miner-enabled"},
    description = "set if node should perform mining (default: ${DEFAULT-VALUE})"
  )
  private final Boolean isMiningEnabled = false;

  @Option(
    names = {"--miner-coinbase"},
    description =
        "the account to which mining rewards are to be paid, must be specified if "
            + "mining is enabled.",
    arity = "1"
  )
  private final Address coinbase = null;

  @Option(
    names = {"--miner-minTransactionGasPriceWei"},
    description =
        "the minimum price offered by a transaction for it to be included in a mined "
            + "block (default: ${DEFAULT-VALUE}).",
    arity = "1"
  )
  private final Wei minTransactionGasPrice = DEFAULT_MIN_TRANSACTION_GAS_PRICE;

  @Option(
    names = {"--miner-extraData"},
    description =
        "a hex string representing the (32) bytes to be included in the extra data "
            + "field of a mined block. (default: ${DEFAULT-VALUE}).",
    arity = "1"
  )
  private final BytesValue extraData = DEFAULT_EXTRA_DATA;

  // Permissioning: A list of whitelist nodes can be passed.
  @Option(
    names = {"--nodes-whitelist"},
    paramLabel = "<enode://id@host:port>",
    description =
        "Comma separated enode URLs for permissioned networks. "
            + "Not intended to be used with mainnet or public testnets.",
    split = ",",
    arity = "0..*",
    converter = EnodeToURIPropertyConverter.class
  )
  private final Collection<URI> nodesWhitelist = null;

  @Option(
    names = {"--accounts-whitelist"},
    paramLabel = "<hex string of account public key>",
    description =
        "Comma separated hex strings of account public keys "
            + "for permissioned/role-based transactions. You may specify an empty list.",
    split = ",",
    arity = "0..*"
  )
  private final Collection<String> accountsWhitelist = null;

  public PantheonCommand(
      final BlockImporter blockImporter,
      final RunnerBuilder runnerBuilder,
      final PantheonControllerBuilder controllerBuilder,
      final SynchronizerConfiguration.Builder synchronizerConfigurationBuilder) {
    this.blockImporter = blockImporter;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilder = controllerBuilder;
    this.synchronizerConfigurationBuilder = synchronizerConfigurationBuilder;
  }

  private StandaloneCommand standaloneCommands;

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String... args) {

    final CommandLine commandLine = new CommandLine(this);

    standaloneCommands = new StandaloneCommand();

    if (isFullInstantiation()) {
      commandLine.addMixin("standaloneCommands", standaloneCommands);
    }

    final ImportSubCommand importSubCommand = new ImportSubCommand(blockImporter);
    commandLine.addSubcommand("import", importSubCommand);
    commandLine.addSubcommand("export-pub-key", new ExportPublicKeySubCommand());

    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(BytesValue.class, BytesValue::fromHexString);
    commandLine.registerConverter(HostAndPort.class, HostAndPort::fromString);
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

    //noinspection ConstantConditions
    if (isMiningEnabled && coinbase == null) {
      throw new ParameterException(
          new CommandLine(this),
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled)"
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }
    if (trueCount(ropsten, rinkeby, goerli) > 1) {
      throw new ParameterException(
          new CommandLine(this),
          "Unable to connect to multiple networks simultaneously. Specify one of --ropsten, --rinkeby or --goerli");
    }

    final EthNetworkConfig ethNetworkConfig = ethNetworkConfig();
    PermissioningConfiguration permissioningConfiguration = permissioningConfiguration();
    ensureAllBootnodesAreInWhitelist(ethNetworkConfig, permissioningConfiguration);

    synchronize(
        buildController(),
        noPeerDiscovery,
        ethNetworkConfig.getBootNodes(),
        maxPeers,
        p2pHostAndPort,
        jsonRpcConfiguration(),
        webSocketConfiguration(),
        metricsConfiguration(),
        permissioningConfiguration);
  }

  private void ensureAllBootnodesAreInWhitelist(
      final EthNetworkConfig ethNetworkConfig,
      final PermissioningConfiguration permissioningConfiguration) {
    List<Peer> bootnodes =
        DiscoveryConfiguration.getBootstrapPeersFromGenericCollection(
            ethNetworkConfig.getBootNodes());
    if (permissioningConfiguration.isNodeWhitelistSet() && bootnodes != null) {
      List<Peer> whitelist =
          permissioningConfiguration
              .getNodeWhitelist()
              .stream()
              .map(DefaultPeer::fromURI)
              .collect(Collectors.toList());
      for (Peer bootnode : bootnodes) {
        if (!whitelist.contains(bootnode)) {
          throw new ParameterException(
              new CommandLine(this),
              "Cannot start node with bootnode(s) that are not in nodes-whitelist " + bootnode);
        }
      }
    }
  }

  private static int trueCount(final Boolean... b) {
    return (int) Arrays.stream(b).filter(bool -> bool).count();
  }

  PantheonController<?> buildController() {
    try {
      return controllerBuilder
          .synchronizerConfiguration(buildSyncConfig(syncMode))
          .homePath(dataDir())
          .ethNetworkConfig(ethNetworkConfig())
          .syncWithOttoman(syncWithOttoman)
          .miningParameters(
              new MiningParameters(coinbase, minTransactionGasPrice, extraData, isMiningEnabled))
          .devMode(isDevMode)
          .nodePrivateKeyFile(getNodePrivateKeyFile())
          .metricsSystem(metricsSystem)
          .build();
    } catch (final InvalidConfigurationException e) {
      throw new ExecutionException(new CommandLine(this), e.getMessage());
    } catch (final IOException e) {
      throw new ExecutionException(new CommandLine(this), "Invalid path", e);
    }
  }

  private File getNodePrivateKeyFile() {
    return nodePrivateKeyFile != null
        ? nodePrivateKeyFile
        : KeyPairUtil.getDefaultKeyFile(dataDir());
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(isJsonRpcEnabled);
    jsonRpcConfiguration.setHost(rpcHostAndPort.getHost());
    jsonRpcConfiguration.setPort(rpcHostAndPort.getPort());
    jsonRpcConfiguration.setCorsAllowedDomains(rpcCorsAllowedOrigins);
    jsonRpcConfiguration.setRpcApis(rpcApis);
    jsonRpcConfiguration.setHostsWhitelist(hostsWhitelist);
    return jsonRpcConfiguration;
  }

  private WebSocketConfiguration webSocketConfiguration() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isWsRpcEnabled);
    webSocketConfiguration.setHost(wsHostAndPort.getHost());
    webSocketConfiguration.setPort(wsHostAndPort.getPort());
    webSocketConfiguration.setRpcApis(wsApis);
    webSocketConfiguration.setRefreshDelay(refreshDelay);
    return webSocketConfiguration;
  }

  MetricsConfiguration metricsConfiguration() {
    final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
    metricsConfiguration.setEnabled(isMetricsEnabled);
    metricsConfiguration.setHost(metricsHostAndPort.getHost());
    metricsConfiguration.setPort(metricsHostAndPort.getPort());
    metricsConfiguration.setHostsWhitelist(hostsWhitelist);
    return metricsConfiguration;
  }

  private PermissioningConfiguration permissioningConfiguration() {
    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);
    permissioningConfiguration.setAccountWhitelist(accountsWhitelist);
    return permissioningConfiguration;
  }

  private SynchronizerConfiguration buildSyncConfig(final SyncMode syncMode) {
    checkNotNull(syncMode);
    synchronizerConfigurationBuilder.syncMode(syncMode);
    synchronizerConfigurationBuilder.maxTrailingPeers(maxTrailingPeers);
    return synchronizerConfigurationBuilder.build();
  }

  // Blockchain synchronisation from peers.
  private void synchronize(
      final PantheonController<?> controller,
      final boolean noPeerDiscovery,
      final Collection<?> bootstrapNodes,
      final int maxPeers,
      final HostAndPort discoveryHostAndPort,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final PermissioningConfiguration permissioningConfiguration) {

    checkNotNull(runnerBuilder);

    Runner runner =
        runnerBuilder
            .vertx(Vertx.vertx())
            .pantheonController(controller)
            // BEWARE: Peer discovery boolean must be inverted as it's negated in the options !
            .discovery(!noPeerDiscovery)
            .bootstrapPeers(bootstrapNodes)
            .discoveryHost(discoveryHostAndPort.getHost())
            .discoveryPort(discoveryHostAndPort.getPort())
            .maxPeers(maxPeers)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .dataDir(dataDir())
            .bannedNodeIds(bannedNodeIds)
            .metricsSystem(metricsSystem)
            .metricsConfiguration(metricsConfiguration)
            .permissioningConfiguration(permissioningConfiguration)
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
                  } catch (final Exception e) {
                    throw new RuntimeException(e);
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

  private HostAndPort getDefaultHostAndPort(final int port) {
    return HostAndPort.fromParts(autoDiscoverDefaultIP().getHostAddress(), port);
  }

  private EthNetworkConfig ethNetworkConfig() {
    final EthNetworkConfig predefinedNetworkConfig;
    if (rinkeby) {
      predefinedNetworkConfig = EthNetworkConfig.rinkeby();
    } else if (ropsten) {
      predefinedNetworkConfig = EthNetworkConfig.ropsten();
    } else if (goerli) {
      predefinedNetworkConfig = EthNetworkConfig.goerli();
    } else {
      predefinedNetworkConfig = EthNetworkConfig.mainnet();
    }
    return updateNetworkConfig(predefinedNetworkConfig);
  }

  private EthNetworkConfig updateNetworkConfig(final EthNetworkConfig ethNetworkConfig) {
    final EthNetworkConfig.Builder builder = new EthNetworkConfig.Builder(ethNetworkConfig);
    if (genesisFile() != null) {
      builder.setGenesisConfig(genesisConfig());
    }
    if (networkId != null) {
      builder.setNetworkId(networkId);
    }
    if (bootstrapNodes != null) {
      builder.setBootNodes(bootstrapNodes);
    }
    return builder.build();
  }

  private String genesisConfig() {
    try {
      return Resources.toString(genesisFile().toURI().toURL(), UTF_8);
    } catch (IOException e) {
      throw new ParameterException(
          new CommandLine(this),
          String.format("Unable to load genesis file %s.", genesisFile()),
          e);
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
      return standaloneCommands.dataDir;
    } else if (isDocker) {
      return Paths.get(DOCKER_DATADIR_LOCATION);
    } else {
      return getDefaultPantheonDataDir(this);
    }
  }

  private boolean isFullInstantiation() {
    return !isDocker;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }
}
