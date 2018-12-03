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

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.cli.custom.CorsAllowedOriginsProperty;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftRpcApis;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration.Builder;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.util.InvalidConfigurationException;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
public class PantheonCommand implements Runnable {

  private static final int DEFAULT_MAX_PEERS = 25;

  // Default should be FAST for the next release
  // but we use FULL for the moment as Fast is still in progress
  private static final SyncMode DEFAULT_SYNC_MODE = SyncMode.FULL;

  private static final String PANTHEON_HOME_PROPERTY_NAME = "pantheon.home";
  private static final String DEFAULT_DATA_DIR_PATH = "./build/data";

  private static final String MANDATORY_HOST_AND_PORT_FORMAT_HELP = "<HOST:PORT>";
  private static final String MANDATORY_PATH_FORMAT_HELP = "<PATH>";
  private static final String MANDATORY_INTEGER_FORMAT_HELP = "<INTEGER>";
  private static final String MANDATORY_MODE_FORMAT_HELP = "<MODE>";

  private static final Wei DEFAULT_MIN_TRANSACTION_GAS_PRICE = Wei.of(1000);
  private static final BytesValue DEFAULT_EXTRA_DATA = BytesValue.EMPTY;

  private static final String CONFIG_FILE_OPTION_NAME = "--config";

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
  private final Builder synchronizerConfigurationBuilder;
  private final RunnerBuilder runnerBuilder;

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  // CLI options defined by user at runtime.
  // Options parsing is done with CLI library Picocli https://picocli.info/

  @Option(
    names = {CONFIG_FILE_OPTION_NAME},
    paramLabel = MANDATORY_PATH_FORMAT_HELP,
    description = "TOML config file (default: none)"
  )
  private final File configFile = null;

  @Option(
    names = {"--datadir"},
    paramLabel = MANDATORY_PATH_FORMAT_HELP,
    description = "the path to Pantheon data directory (default: ${DEFAULT-VALUE})"
  )
  private final Path dataDir = getDefaultPantheonDataDir();

  @Option(
    names = {"--node-private-key"},
    paramLabel = MANDATORY_PATH_FORMAT_HELP,
    description =
        "the path to the node's private key file (default: a file named \"key\" in the Pantheon data folder)"
  )
  private final File nodePrivateKeyFile = null;

  // Genesis file path with null default option if the option
  // is not defined on command line as this default is handled by Runner
  // to use mainnet json file from resources
  // NOTE: we have no control over default value here.
  @Option(
    names = {"--genesis"},
    paramLabel = MANDATORY_PATH_FORMAT_HELP,
    description = "The path to genesis file (default: Pantheon embedded mainnet genesis file)"
  )
  private final File genesisFile = null;

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
    arity = "1..*"
  )
  private final Collection<String> bootstrapNodes = null;

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
    description = "Comma separated origin domain URLs for CORS validation (default: none)",
    converter = CorsAllowedOriginsProperty.CorsAllowedOriginsPropertyConverter.class
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
        "Comma separated enode URLs for permissioned networks. You may specify an empty list.",
    split = ",",
    arity = "0..*"
  )
  private final Collection<String> nodesWhitelist = null;

  public PantheonCommand(
      final BlockImporter blockImporter,
      final RunnerBuilder runnerBuilder,
      final PantheonControllerBuilder controllerBuilder,
      final Builder synchronizerConfigurationBuilder) {
    this.blockImporter = blockImporter;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilder = controllerBuilder;
    this.synchronizerConfigurationBuilder = synchronizerConfigurationBuilder;
  }

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String... args) {

    final CommandLine commandLine = new CommandLine(this);

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
            resultHandler, exceptionHandler, CONFIG_FILE_OPTION_NAME);
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
      System.out.println(
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled)"
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
      return;
    }
    if (ropsten && rinkeby) {
      System.out.println(
          "Unable to connect to multiple networks simultaneously. Remove one of --ropsten or --rinkeby");
      return;
    }
    final EthNetworkConfig ethNetworkConfig = ethNetworkConfig();
    synchronize(
        buildController(),
        noPeerDiscovery,
        ethNetworkConfig.getBootNodes(),
        maxPeers,
        p2pHostAndPort,
        jsonRpcConfiguration(),
        webSocketConfiguration(),
        permissioningConfiguration());
  }

  PantheonController<?> buildController() {
    try {
      return controllerBuilder
          .synchronizerConfiguration(buildSyncConfig(syncMode))
          .homePath(dataDir)
          .ethNetworkConfig(ethNetworkConfig())
          .syncWithOttoman(syncWithOttoman)
          .miningParameters(
              new MiningParameters(coinbase, minTransactionGasPrice, extraData, isMiningEnabled))
          .devMode(isDevMode)
          .nodePrivateKeyFile(getNodePrivateKeyFile())
          .build();
    } catch (final InvalidConfigurationException e) {
      throw new ExecutionException(new CommandLine(this), e.getMessage());
    } catch (final IOException e) {
      throw new ExecutionException(new CommandLine(this), "Invalid path", e);
    }
  }

  private File getNodePrivateKeyFile() {
    return nodePrivateKeyFile != null ? nodePrivateKeyFile : KeyPairUtil.getDefaultKeyFile(dataDir);
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(isJsonRpcEnabled);
    jsonRpcConfiguration.setHost(rpcHostAndPort.getHost());
    jsonRpcConfiguration.setPort(rpcHostAndPort.getPort());
    jsonRpcConfiguration.setCorsAllowedDomains(rpcCorsAllowedOrigins.getDomains());
    jsonRpcConfiguration.setRpcApis(rpcApis);
    return jsonRpcConfiguration;
  }

  private WebSocketConfiguration webSocketConfiguration() {
    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isWsRpcEnabled);
    webSocketConfiguration.setHost(wsHostAndPort.getHost());
    webSocketConfiguration.setPort(wsHostAndPort.getPort());
    webSocketConfiguration.setRpcApis(wsApis);
    return webSocketConfiguration;
  }

  private PermissioningConfiguration permissioningConfiguration() {
    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setNodeWhitelist(nodesWhitelist);
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
            .dataDir(dataDir)
            .bannedNodeIds(bannedNodeIds)
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

  private Path getDefaultPantheonDataDir() {
    // this property is retrieved from Gradle tasks or Pantheon running shell script.
    final String pantheonHomeProperty = System.getProperty(PANTHEON_HOME_PROPERTY_NAME);
    final Path pantheonHome;

    // If prop is found, then use it
    if (pantheonHomeProperty != null) {
      try {
        pantheonHome = Paths.get(pantheonHomeProperty);
      } catch (final InvalidPathException e) {
        throw new ParameterException(
            new CommandLine(this),
            String.format(
                "Unable to define default data directory from %s property.",
                PANTHEON_HOME_PROPERTY_NAME),
            e);
      }
    } else {
      // otherwise use a default path.
      // That may only be used when NOT run from distribution script and Gradle as they all define
      // the property.
      try {
        final String path = new File(DEFAULT_DATA_DIR_PATH).getCanonicalPath();
        pantheonHome = Paths.get(path);
      } catch (final IOException e) {
        throw new ParameterException(
            new CommandLine(this), "Unable to create default data directory.");
      }
    }

    // Try to create it, then verify if the provided path is not already existing and is not a
    // directory .Otherwise, if it doesn't exist or exists but is already a directory,
    // Runner will use it to store data.
    try {
      Files.createDirectories(pantheonHome);
    } catch (final FileAlreadyExistsException e) {
      // Only thrown if it exist but is not a directory
      throw new ParameterException(
          new CommandLine(this),
          String.format(
              "%s: already exists and is not a directory.", pantheonHome.toAbsolutePath()),
          e);
    } catch (final Exception e) {
      throw new ParameterException(
          new CommandLine(this),
          String.format("Error creating directory %s.", pantheonHome.toAbsolutePath()),
          e);
    }
    return pantheonHome;
  }

  private EthNetworkConfig ethNetworkConfig() {
    final EthNetworkConfig predefinedNetworkConfig;
    if (rinkeby) {
      predefinedNetworkConfig = EthNetworkConfig.rinkeby();
    } else if (ropsten) {
      predefinedNetworkConfig = EthNetworkConfig.ropsten();
    } else {
      predefinedNetworkConfig = EthNetworkConfig.mainnet();
    }
    return updateNetworkConfig(predefinedNetworkConfig);
  }

  private EthNetworkConfig updateNetworkConfig(final EthNetworkConfig ethNetworkConfig) {
    final EthNetworkConfig.Builder builder = new EthNetworkConfig.Builder(ethNetworkConfig);
    if (genesisFile != null) {
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
      return Resources.toString(genesisFile.toURI().toURL(), UTF_8);
    } catch (final IOException e) {
      throw new ParameterException(
          new CommandLine(this), String.format("Unable to load genesis file %s.", genesisFile), e);
    }
  }
}
