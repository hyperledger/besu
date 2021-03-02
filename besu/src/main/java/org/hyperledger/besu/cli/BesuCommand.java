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
package org.hyperledger.besu.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;
import static org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration.DEFAULT_GRAPHQL_HTTP_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_JSON_RPC_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT;
import static org.hyperledger.besu.ethereum.permissioning.GoQuorumPermissioningConfiguration.QIP714_DEFAULT_BLOCK;
import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.MetricsProtocol.PROMETHEUS;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PORT;
import static org.hyperledger.besu.metrics.prometheus.MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.converter.MetricCategoryConverter;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.converter.RpcApisConverter;
import org.hyperledger.besu.cli.custom.CorsAllowedOriginsProperty;
import org.hyperledger.besu.cli.custom.JsonRPCAllowlistHostsProperty;
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator;
import org.hyperledger.besu.cli.error.BesuExceptionHandler;
import org.hyperledger.besu.cli.options.unstable.DataStorageOptions;
import org.hyperledger.besu.cli.options.unstable.DnsOptions;
import org.hyperledger.besu.cli.options.unstable.EthProtocolOptions;
import org.hyperledger.besu.cli.options.unstable.EthstatsOptions;
import org.hyperledger.besu.cli.options.unstable.LauncherOptions;
import org.hyperledger.besu.cli.options.unstable.MetricsCLIOptions;
import org.hyperledger.besu.cli.options.unstable.MiningOptions;
import org.hyperledger.besu.cli.options.unstable.NatOptions;
import org.hyperledger.besu.cli.options.unstable.NativeLibraryOptions;
import org.hyperledger.besu.cli.options.unstable.NetworkingOptions;
import org.hyperledger.besu.cli.options.unstable.RPCOptions;
import org.hyperledger.besu.cli.options.unstable.SynchronizerOptions;
import org.hyperledger.besu.cli.options.unstable.TransactionPoolOptions;
import org.hyperledger.besu.cli.presynctasks.PreSynchronizationTaskRunner;
import org.hyperledger.besu.cli.presynctasks.PrivateDatabaseMigrationPreSyncTask;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand;
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand;
import org.hyperledger.besu.cli.subcommands.RetestethSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand;
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand;
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand;
import org.hyperledger.besu.cli.util.BesuCommandCustomFactory;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.cli.util.ConfigOptionSearchAndRunHandler;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.controller.TargetingGasLimitCalculator;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairSecurityModule;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider;
import org.hyperledger.besu.ethereum.api.tls.TlsClientAuthConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.precompiles.AbstractAltBnPrecompiledContract;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.StaticNodesParser;
import org.hyperledger.besu.ethereum.permissioning.GoQuorumPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.MetricCategoryRegistryImpl;
import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.metrics.MetricsSystemFactory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryStoragePlugin;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.PermissioningConfigurationValidator;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.metrics.MetricsOptions;
import net.consensys.quorum.mainnet.launcher.LauncherManager;
import net.consensys.quorum.mainnet.launcher.config.ImmutableLauncherConfig;
import net.consensys.quorum.mainnet.launcher.exception.LauncherException;
import net.consensys.quorum.mainnet.launcher.util.ParseArgsHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@SuppressWarnings("FieldCanBeLocal") // because Picocli injected fields report false positives
@Command(
    description = "This command runs the Besu Ethereum client full node.",
    abbreviateSynopsis = true,
    name = "besu",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Besu is licensed under the Apache License 2.0")
public class BesuCommand implements DefaultCommandValues, Runnable {

  @SuppressWarnings("PrivateStaticFinalLoggers")
  // non-static for testing
  private final Logger logger;

  private CommandLine commandLine;

  private final Supplier<RlpBlockImporter> rlpBlockImporter;
  private final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory;
  private final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory;

  // Unstable CLI options
  final NetworkingOptions unstableNetworkingOptions = NetworkingOptions.create();
  final SynchronizerOptions unstableSynchronizerOptions = SynchronizerOptions.create();
  final EthProtocolOptions unstableEthProtocolOptions = EthProtocolOptions.create();
  final MetricsCLIOptions unstableMetricsCLIOptions = MetricsCLIOptions.create();
  final TransactionPoolOptions unstableTransactionPoolOptions = TransactionPoolOptions.create();
  private final EthstatsOptions unstableEthstatsOptions = EthstatsOptions.create();
  private final DataStorageOptions unstableDataStorageOptions = DataStorageOptions.create();
  private final DnsOptions unstableDnsOptions = DnsOptions.create();
  private final MiningOptions unstableMiningOptions = MiningOptions.create();
  private final NatOptions unstableNatOptions = NatOptions.create();
  private final NativeLibraryOptions unstableNativeLibraryOptions = NativeLibraryOptions.create();
  private final RPCOptions unstableRPCOptions = RPCOptions.create();
  final LauncherOptions unstableLauncherOptions = LauncherOptions.create();

  private final RunnerBuilder runnerBuilder;
  private final BesuController.Builder controllerBuilderFactory;
  private final BesuPluginContextImpl besuPluginContext;
  private final StorageServiceImpl storageService;
  private final SecurityModuleServiceImpl securityModuleService;
  private final Map<String, String> environment;
  private final MetricCategoryRegistryImpl metricCategoryRegistry =
      new MetricCategoryRegistryImpl();
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  private final PreSynchronizationTaskRunner preSynchronizationTaskRunner =
      new PreSynchronizationTaskRunner();

  private final Set<Integer> allocatedPorts = new HashSet<>();

  // CLI options defined by user at runtime.
  // Options parsing is done with CLI library Picocli https://picocli.info/

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
  private final File genesisFile = null;

  @CommandLine.Option(
      names = {"--node-private-key-file"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description =
          "The node's private key file (default: a file named \"key\" in the Besu data folder)")
  private final File nodePrivateKeyFile = null;

  @Option(
      names = "--identity",
      paramLabel = "<String>",
      description = "Identification for this node in the Client ID",
      arity = "1")
  private final Optional<String> identityString = Optional.empty();

  // Completely disables P2P within Besu.
  @Option(
      names = {"--p2p-enabled"},
      description = "Enable P2P functionality (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean p2pEnabled = true;

  // Boolean option to indicate if peers should NOT be discovered, default to
  // false indicates that
  // the peers should be discovered by default.
  //
  // This negative option is required because of the nature of the option that is
  // true when
  // added on the command line. You can't do --option=false, so false is set as
  // default
  // and you have not to set the option at all if you want it false.
  // This seems to be the only way it works with Picocli.
  // Also many other software use the same negative option scheme for false
  // defaults
  // meaning that it's probably the right way to handle disabling options.
  @Option(
      names = {"--discovery-enabled"},
      description = "Enable P2P discovery (default: ${DEFAULT-VALUE})",
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
  private final List<String> bootNodes = null;

  @Option(
      names = {"--max-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description = "Maximum P2P connections that can be established (default: ${DEFAULT-VALUE})")
  private final Integer maxPeers = DEFAULT_MAX_PEERS;

  @Option(
      names = {"--remote-connections-limit-enabled"},
      description =
          "Whether to limit the number of P2P connections initiated remotely. (default: ${DEFAULT-VALUE})")
  private final Boolean isLimitRemoteWireConnectionsEnabled = true;

  @Option(
      names = {"--remote-connections-max-percentage"},
      paramLabel = MANDATORY_DOUBLE_FORMAT_HELP,
      description =
          "The maximum percentage of P2P connections that can be initiated remotely. Must be between 0 and 100 inclusive. (default: ${DEFAULT-VALUE})",
      arity = "1",
      converter = PercentageConverter.class)
  private final Integer maxRemoteConnectionsPercentage =
      Fraction.fromFloat(DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED)
          .toPercentage()
          .getValue();

  @Option(
      names = {"--random-peer-priority-enabled"},
      description =
          "Allow for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. (default: ${DEFAULT-VALUE})")
  private final Boolean randomPeerPriority = false;

  @Option(
      names = {"--banned-node-ids", "--banned-node-id"},
      paramLabel = MANDATORY_NODE_ID_FORMAT_HELP,
      description = "A list of node IDs to ban from the P2P network.",
      split = ",",
      arity = "1..*")
  void setBannedNodeIds(final List<String> values) {
    try {
      bannedNodeIds =
          values.stream()
              .filter(value -> !value.isEmpty())
              .map(EnodeURL::parseNodeId)
              .collect(Collectors.toList());
    } catch (final IllegalArgumentException e) {
      throw new ParameterException(
          commandLine, "Invalid ids supplied to '--banned-node-ids'. " + e.getMessage());
    }
  }

  private Collection<Bytes> bannedNodeIds = new ArrayList<>();

  @Option(
      names = {"--sync-mode"},
      paramLabel = MANDATORY_MODE_FORMAT_HELP,
      description =
          "Synchronization mode, possible values are ${COMPLETION-CANDIDATES} (default: FAST if a --network is supplied and privacy isn't enabled. FULL otherwise.)")
  private SyncMode syncMode = null;

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

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--p2p-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Ip address this node advertises to its peers (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--p2p-interface"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description =
          "The network interface address on which this node listens for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pInterface = NetworkUtility.INADDR_ANY;

  @Option(
      names = {"--p2p-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port on which to listen for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer p2pPort = EnodeURL.DEFAULT_LISTENING_PORT;

  @Option(
      names = {"--nat-method"},
      description =
          "Specify the NAT circumvention method to be used, possible values are ${COMPLETION-CANDIDATES}."
              + " NONE disables NAT functionality. (default: ${DEFAULT-VALUE})")
  private final NatMethod natMethod = DEFAULT_NAT_METHOD;

  @Option(
      names = {"--network-id"},
      paramLabel = "<BIG INTEGER>",
      description =
          "P2P network identifier. (default: the selected network chain ID or custom genesis chain ID)",
      arity = "1")
  private final BigInteger networkId = null;

  @Option(
      names = {"--graphql-http-enabled"},
      description = "Set to start the GraphQL HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isGraphQLHttpEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--graphql-http-host"},
      paramLabel = MANDATORY_HOST_FORMAT_HELP,
      description = "Host for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String graphQLHttpHost = autoDiscoverDefaultIP().getHostAddress();

  @Option(
      names = {"--graphql-http-port"},
      paramLabel = MANDATORY_PORT_FORMAT_HELP,
      description = "Port for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer graphQLHttpPort = DEFAULT_GRAPHQL_HTTP_PORT;

  @Option(
      names = {"--graphql-http-cors-origins"},
      description = "Comma separated origin domain URLs for CORS validation (default: none)")
  private final CorsAllowedOriginsProperty graphQLHttpCorsAllowedOrigins =
      new CorsAllowedOriginsProperty();

  @Option(
      names = {"--rpc-http-enabled"},
      description = "Set to start the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
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

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-http-authentication-credentials-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Storage file for JSON-RPC HTTP authentication credentials (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcHttpAuthenticationCredentialsFile = null;

  @CommandLine.Option(
      names = {"--rpc-http-authentication-jwt-public-key-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "JWT public key file for JSON-RPC HTTP authentication",
      arity = "1")
  private final File rpcHttpAuthenticationPublicKeyFile = null;

  @Option(
      names = {"--rpc-http-tls-enabled"},
      description = "Enable TLS for the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpTlsEnabled = false;

  @Option(
      names = {"--rpc-http-tls-keystore-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Keystore (PKCS#12) containing key/certificate for the JSON-RPC HTTP service. Required if TLS is enabled.")
  private final Path rpcHttpTlsKeyStoreFile = null;

  @Option(
      names = {"--rpc-http-tls-keystore-password-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "File containing password to unlock keystore for the JSON-RPC HTTP service. Required if TLS is enabled.")
  private final Path rpcHttpTlsKeyStorePasswordFile = null;

  @Option(
      names = {"--rpc-http-tls-client-auth-enabled"},
      description =
          "Enable TLS client authentication for the JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpTlsClientAuthEnabled = false;

  @Option(
      names = {"--rpc-http-tls-known-clients-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Path to file containing clients certificate common name and fingerprint for client authentication")
  private final Path rpcHttpTlsKnownClientsFile = null;

  @Option(
      names = {"--rpc-http-tls-ca-clients-enabled"},
      description =
          "Enable to accept clients certificate signed by a valid CA for client authentication (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcHttpTlsCAClientsEnabled = false;

  @Option(
      names = {"--rpc-ws-enabled"},
      description = "Set to start the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
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
  private final List<RpcApi> rpcWsApis = DEFAULT_JSON_RPC_APIS;

  @Option(
      names = {"--rpc-ws-authentication-enabled"},
      description =
          "Require authentication for the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
  private final Boolean isRpcWsAuthenticationEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--rpc-ws-authentication-credentials-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Storage file for JSON-RPC WebSocket authentication credentials (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String rpcWsAuthenticationCredentialsFile = null;

  @CommandLine.Option(
      names = {"--rpc-ws-authentication-jwt-public-key-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "JWT public key file for JSON-RPC WebSocket authentication",
      arity = "1")
  private final File rpcWsAuthenticationPublicKeyFile = null;

  @Option(
      names = {"--privacy-tls-enabled"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Enable TLS for connecting to privacy enclave (default: ${DEFAULT-VALUE})")
  private final Boolean isPrivacyTlsEnabled = false;

  @Option(
      names = "--privacy-tls-keystore-file",
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Path to a PKCS#12 formatted keystore; used to enable TLS on inbound connections.")
  private final Path privacyKeyStoreFile = null;

  @Option(
      names = "--privacy-tls-keystore-password-file",
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Path to a file containing the password used to decrypt the keystore.")
  private final Path privacyKeyStorePasswordFile = null;

  @Option(
      names = "--privacy-tls-known-enclave-file",
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "Path to a file containing the fingerprints of the authorized privacy enclave.")
  private final Path privacyTlsKnownEnclaveFile = null;

  @Option(
      names = {"--metrics-enabled"},
      description = "Set to start the metrics exporter (default: ${DEFAULT-VALUE})")
  private final Boolean isMetricsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--metrics-protocol"},
      description =
          "Metrics protocol, one of PROMETHEUS, OPENTELEMETRY or NONE. (default: ${DEFAULT-VALUE})")
  private MetricsProtocol metricsProtocol = PROMETHEUS;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
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

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
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

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--metrics-push-prometheus-job"},
      description = "Job name to use when in push mode (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String metricsPrometheusJob = "besu-client";

  @Option(
      names = {"--host-allowlist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to allow for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCAllowlistHostsProperty hostsAllowlist = new JsonRPCAllowlistHostsProperty();

  @Option(
      names = {"--host-whitelist"},
      hidden = true,
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Deprecated in favor of --host-allowlist. Comma separated list of hostnames to allow for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})")
  private final JsonRPCAllowlistHostsProperty hostsWhitelist = new JsonRPCAllowlistHostsProperty();

  @Option(
      names = {"--logging", "-l"},
      paramLabel = "<LOG VERBOSITY LEVEL>",
      description = "Logging verbosity levels: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL")
  private final Level logLevel = null;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @Option(
      names = {"--color-enabled"},
      description =
          "Force color output to be enabled/disabled (default: colorized only if printing to console)")
  private static Boolean colorEnabled = null;

  @Option(
      names = {"--reorg-logging-threshold"},
      description =
          "How deep a chain reorganization must be in order for it to be logged (default: ${DEFAULT-VALUE})")
  private final Long reorgLoggingThreshold = 6L;

  @Option(
      names = {"--miner-enabled"},
      description = "Set if node will perform mining (default: ${DEFAULT-VALUE})")
  private final Boolean isMiningEnabled = false;

  @Option(
      names = {"--miner-stratum-enabled"},
      description = "Set if node will perform Stratum mining (default: ${DEFAULT-VALUE})")
  private final Boolean iStratumMiningEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--miner-stratum-host"},
      description = "Host for Stratum network mining service (default: ${DEFAULT-VALUE})")
  private String stratumNetworkInterface = "0.0.0.0";

  @Option(
      names = {"--miner-stratum-port"},
      description = "Stratum port binding (default: ${DEFAULT-VALUE})")
  private final Integer stratumPort = 8008;

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
      names = {"--rpc-tx-feecap"},
      description =
          "Maximum transaction fees (in Wei) accepted for transaction submitted through RPC (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Wei txFeeCap = DEFAULT_RPC_TX_FEE_CAP;

  @Option(
      names = {"--min-block-occupancy-ratio"},
      description = "Minimum occupancy ratio for a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Double minBlockOccupancyRatio = DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;

  @Option(
      names = {"--miner-extra-data"},
      description =
          "A hex string representing the (32) bytes to be included in the extra data "
              + "field of a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Bytes extraData = DEFAULT_EXTRA_DATA;

  @Option(
      names = {"--pruning-enabled"},
      description =
          "Enable disk-space saving optimization that removes old state that is unlikely to be required (default: ${DEFAULT-VALUE})")
  private final Boolean pruningEnabled = false;

  @Option(
      names = {"--permissions-nodes-config-file-enabled"},
      description = "Enable node level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--permissions-nodes-config-file"},
      description =
          "Node permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  private String nodePermissionsConfigFile = null;

  @Option(
      names = {"--permissions-accounts-config-file-enabled"},
      description = "Enable account level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--permissions-accounts-config-file"},
      description =
          "Account permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  private String accountPermissionsConfigFile = null;

  @Option(
      names = {"--permissions-nodes-contract-address"},
      description = "Address of the node permissioning smart contract",
      arity = "1")
  private final Address permissionsNodesContractAddress = null;

  @Option(
      names = {"--permissions-nodes-contract-version"},
      description = "Version of the EEA Node Permissioning interface (default: ${DEFAULT-VALUE})")
  private final Integer permissionsNodesContractVersion = 1;

  @Option(
      names = {"--permissions-nodes-contract-enabled"},
      description = "Enable node level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesContractEnabled = false;

  @Option(
      names = {"--permissions-accounts-contract-address"},
      description = "Address of the account permissioning smart contract",
      arity = "1")
  private final Address permissionsAccountsContractAddress = null;

  @Option(
      names = {"--permissions-accounts-contract-enabled"},
      description =
          "Enable account level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsContractEnabled = false;

  @Option(
      names = {"--privacy-enabled"},
      description = "Enable private transactions (default: ${DEFAULT-VALUE})")
  private final Boolean isPrivacyEnabled = false;

  @Option(
      names = {"--privacy-multi-tenancy-enabled"},
      description = "Enable multi-tenant private transactions (default: ${DEFAULT-VALUE})")
  private final Boolean isPrivacyMultiTenancyEnabled = false;

  @Option(
      names = {"--revert-reason-enabled"},
      description =
          "Enable passing the revert reason back through TransactionReceipts (default: ${DEFAULT-VALUE})")
  private final Boolean isRevertReasonEnabled = false;

  @Option(
      names = {"--required-blocks", "--required-block"},
      paramLabel = "BLOCK=HASH",
      description = "Block number and hash peers are required to have.",
      arity = "*",
      split = ",")
  private final Map<Long, Hash> requiredBlocks = new HashMap<>();

  @Option(
      names = {"--privacy-url"},
      description = "The URL on which the enclave is running")
  private final URI privacyUrl = PrivacyParameters.DEFAULT_ENCLAVE_URL;

  @Option(
      names = {"--privacy-public-key-file"},
      description = "The enclave's public key file")
  private final File privacyPublicKeyFile = null;

  @Option(
      names = {"--privacy-precompiled-address"},
      description =
          "The address to which the privacy pre-compiled contract will be mapped (default: ${DEFAULT-VALUE})",
      hidden = true)
  private final Integer privacyPrecompiledAddress = Address.PRIVACY;

  @Option(
      names = {"--privacy-marker-transaction-signing-key-file"},
      description =
          "The name of a file containing the private key used to sign privacy marker transactions. If unset, each will be signed with a random key.")
  private final Path privacyMarkerTransactionSigningKeyPath = null;

  @Option(
      names = {"--privacy-enable-database-migration"},
      description = "Enable private database metadata migration (default: ${DEFAULT-VALUE})")
  private final Boolean migratePrivateDatabase = false;

  @Option(
      names = {"--privacy-flexible-groups-enabled", "--privacy-onchain-groups-enabled"},
      description = "Enable flexible (onchain) privacy groups (default: ${DEFAULT-VALUE})")
  private final Boolean isFlexiblePrivacyGroupsEnabled = false;

  @Option(
      names = {"--target-gas-limit"},
      description =
          "Sets target gas limit per block. If set each block's gas limit will approach this setting over time if the current gas limit is different.")
  private final Long targetGasLimit = null;

  @Option(
      names = {"--tx-pool-max-size"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum number of pending transactions that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer txPoolMaxSize = TransactionPoolConfiguration.MAX_PENDING_TRANSACTIONS;

  @Option(
      names = {"--tx-pool-hashes-max-size"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum number of pending transaction hashes that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pooledTransactionHashesSize =
      TransactionPoolConfiguration.MAX_PENDING_TRANSACTIONS_HASHES;

  @Option(
      names = {"--tx-pool-retention-hours"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum retention period of pending transactions in hours (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pendingTxRetentionPeriod =
      TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS;

  @Option(
      names = {"--tx-pool-price-bump"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      converter = PercentageConverter.class,
      description =
          "Price bump percentage to replace an already existing transaction  (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer priceBump = TransactionPoolConfiguration.DEFAULT_PRICE_BUMP.getValue();

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--key-value-storage"},
      description = "Identity for the key-value storage to be used.",
      arity = "1")
  private String keyValueStorageName = DEFAULT_KEY_VALUE_STORAGE_NAME;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @Option(
      names = {"--security-module"},
      paramLabel = "<NAME>",
      description = "Identity for the Security Module to be used.",
      arity = "1")
  private String securityModuleName = DEFAULT_SECURITY_MODULE;

  @Option(
      names = {"--auto-log-bloom-caching-enabled"},
      description = "Enable automatic log bloom caching (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean autoLogBloomCachingEnabled = true;

  @Option(
      names = {"--override-genesis-config"},
      paramLabel = "NAME=VALUE",
      description = "Overrides configuration values in the genesis file.  Use with care.",
      arity = "*",
      hidden = true,
      split = ",")
  private final Map<String, String> genesisConfigOverrides =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  @Option(
      names = {"--pruning-blocks-retained"},
      defaultValue = "1024",
      paramLabel = "<INTEGER>",
      description =
          "Minimum number of recent blocks for which to keep entire world state (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pruningBlocksRetained = PrunerConfiguration.DEFAULT_PRUNING_BLOCKS_RETAINED;

  @Option(
      names = {"--pruning-block-confirmations"},
      defaultValue = "10",
      paramLabel = "<INTEGER>",
      description =
          "Minimum number of confirmations on a block before marking begins (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pruningBlockConfirmations =
      PrunerConfiguration.DEFAULT_PRUNING_BLOCK_CONFIRMATIONS;

  @CommandLine.Option(
      names = {"--pid-path"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "Path to PID file (optional)")
  private final Path pidPath = null;

  @CommandLine.Option(
      names = {"--api-gas-price-blocks"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "Number of blocks to consider for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Long apiGasPriceBlocks = 100L;

  @CommandLine.Option(
      names = {"--api-gas-price-percentile"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "Percentile value to measure for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Double apiGasPricePercentile = 50.0;

  @CommandLine.Option(
      names = {"--api-gas-price-max"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "Maximum gas price for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Long apiGasPriceMax = 500_000_000_000L;

  @Option(
      names = {"--goquorum-compatibility-enabled"},
      hidden = true,
      description = "Start Besu in GoQuorum compatibility mode (default: ${DEFAULT-VALUE})")
  private final Boolean isGoQuorumCompatibilityMode = false;

  @CommandLine.Option(
      names = {"--static-nodes-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Specifies the static node file containing the static nodes for this node to connect to")
  private final Path staticNodesFile = null;

  private EthNetworkConfig ethNetworkConfig;
  private JsonRpcConfiguration jsonRpcConfiguration;
  private GraphQLConfiguration graphQLConfiguration;
  private WebSocketConfiguration webSocketConfiguration;
  private ApiConfiguration apiConfiguration;
  private MetricsConfiguration metricsConfiguration;
  private Optional<PermissioningConfiguration> permissioningConfiguration;
  private Collection<EnodeURL> staticNodes;
  private BesuController besuController;
  private BesuConfiguration pluginCommonConfiguration;
  private final Supplier<ObservableMetricsSystem> metricsSystem =
      Suppliers.memoize(() -> MetricsSystemFactory.create(metricsConfiguration()));
  private Vertx vertx;
  private EnodeDnsConfiguration enodeDnsConfiguration;
  private KeyValueStorageProvider keyValueStorageProvider;

  public BesuCommand(
      final Logger logger,
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment) {
    this(
        logger,
        rlpBlockImporter,
        jsonBlockImporterFactory,
        rlpBlockExporterFactory,
        runnerBuilder,
        controllerBuilderFactory,
        besuPluginContext,
        environment,
        new StorageServiceImpl(),
        new SecurityModuleServiceImpl());
  }

  @VisibleForTesting
  protected BesuCommand(
      final Logger logger,
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment,
      final StorageServiceImpl storageService,
      final SecurityModuleServiceImpl securityModuleService) {
    this.logger = logger;
    this.rlpBlockImporter = rlpBlockImporter;
    this.rlpBlockExporterFactory = rlpBlockExporterFactory;
    this.jsonBlockImporterFactory = jsonBlockImporterFactory;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilderFactory = controllerBuilderFactory;
    this.besuPluginContext = besuPluginContext;
    this.environment = environment;
    this.storageService = storageService;
    this.securityModuleService = securityModuleService;

    pluginCommonConfiguration = new BesuCommandConfigurationService();
    besuPluginContext.addService(BesuConfiguration.class, pluginCommonConfiguration);
  }

  public void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final BesuExceptionHandler exceptionHandler,
      final InputStream in,
      final String... args) {

    commandLine =
        new CommandLine(this, new BesuCommandCustomFactory(besuPluginContext))
            .setCaseInsensitiveEnumValuesAllowed(true);

    enableExperimentalEIPs();
    addSubCommands(resultHandler, in);
    registerConverters();
    handleUnstableOptions();
    preparePlugins();
    parse(resultHandler, exceptionHandler, args);
  }

  @Override
  public void run() {

    try {
      configureLogging(true);
      configureNativeLibs();
      logger.info("Starting Besu version: {}", BesuInfo.nodeName(identityString));
      // Need to create vertx after cmdline has been parsed, such that metricsSystem is configurable
      vertx = createVertx(createVertxOptions(metricsSystem.get()));

      final BesuCommand controller = validateOptions().configure().controller();

      preSynchronizationTaskRunner.runTasks(controller.besuController);

      controller.startPlugins().startSynchronization();
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  @VisibleForTesting
  void setBesuConfiguration(final BesuConfiguration pluginCommonConfiguration) {
    this.pluginCommonConfiguration = pluginCommonConfiguration;
  }

  private void enableExperimentalEIPs() {
    // Usage of static command line flags is strictly reserved for experimental EIPs
    commandLine.addMixin("experimentalEIPs", ExperimentalEIPs.class);
  }

  private void addSubCommands(
      final AbstractParseResultHandler<List<Object>> resultHandler, final InputStream in) {
    commandLine.addSubcommand(
        BlocksSubCommand.COMMAND_NAME,
        new BlocksSubCommand(
            rlpBlockImporter,
            jsonBlockImporterFactory,
            rlpBlockExporterFactory,
            resultHandler.out()));
    commandLine.addSubcommand(
        PublicKeySubCommand.COMMAND_NAME,
        new PublicKeySubCommand(resultHandler.out(), this::buildNodeKey));
    commandLine.addSubcommand(
        PasswordSubCommand.COMMAND_NAME, new PasswordSubCommand(resultHandler.out()));
    commandLine.addSubcommand(RetestethSubCommand.COMMAND_NAME, new RetestethSubCommand());
    commandLine.addSubcommand(
        RLPSubCommand.COMMAND_NAME, new RLPSubCommand(resultHandler.out(), in));
    commandLine.addSubcommand(
        OperatorSubCommand.COMMAND_NAME, new OperatorSubCommand(resultHandler.out()));
  }

  private void registerConverters() {
    commandLine.registerConverter(Address.class, Address::fromHexStringStrict);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
    commandLine.registerConverter(Level.class, Level::valueOf);
    commandLine.registerConverter(SyncMode.class, SyncMode::fromString);
    commandLine.registerConverter(MetricsProtocol.class, MetricsProtocol::fromString);
    commandLine.registerConverter(UInt256.class, (arg) -> UInt256.valueOf(new BigInteger(arg)));
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));
    commandLine.registerConverter(PositiveNumber.class, PositiveNumber::fromString);
    commandLine.registerConverter(Hash.class, Hash::fromHexString);
    commandLine.registerConverter(Optional.class, Optional::of);
    commandLine.registerConverter(Double.class, Double::parseDouble);

    metricCategoryConverter.addCategories(BesuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
    commandLine.registerConverter(MetricCategory.class, metricCategoryConverter);
  }

  private void handleUnstableOptions() {
    // Add unstable options
    final ImmutableMap.Builder<String, Object> unstableOptionsBuild = ImmutableMap.builder();
    final ImmutableMap<String, Object> unstableOptions =
        unstableOptionsBuild
            .put("Ethereum Wire Protocol", unstableEthProtocolOptions)
            .put("Metrics", unstableMetricsCLIOptions)
            .put("P2P Network", unstableNetworkingOptions)
            .put("RPC", unstableRPCOptions)
            .put("DNS Configuration", unstableDnsOptions)
            .put("NAT Configuration", unstableNatOptions)
            .put("Synchronizer", unstableSynchronizerOptions)
            .put("TransactionPool", unstableTransactionPoolOptions)
            .put("Ethstats", unstableEthstatsOptions)
            .put("Mining", unstableMiningOptions)
            .put("Native Library", unstableNativeLibraryOptions)
            .put("Data Storage Options", unstableDataStorageOptions)
            .put("Launcher", unstableLauncherOptions)
            .build();

    UnstableOptionsSubCommand.createUnstableOptions(commandLine, unstableOptions);
  }

  private void preparePlugins() {
    besuPluginContext.addService(PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine));
    besuPluginContext.addService(SecurityModuleService.class, securityModuleService);
    besuPluginContext.addService(StorageService.class, storageService);
    besuPluginContext.addService(MetricCategoryRegistry.class, metricCategoryRegistry);

    // register built-in plugins
    new RocksDBPlugin().register(besuPluginContext);
    new InMemoryStoragePlugin().register(besuPluginContext);

    besuPluginContext.registerPlugins(pluginsDir());

    metricCategoryRegistry
        .getMetricCategories()
        .forEach(metricCategoryConverter::addRegistryCategory);

    // register default security module
    securityModuleService.register(
        DEFAULT_SECURITY_MODULE, Suppliers.memoize(this::defaultSecurityModule));
  }

  private SecurityModule defaultSecurityModule() {
    return new KeyPairSecurityModule(loadKeyPair());
  }

  @VisibleForTesting
  KeyPair loadKeyPair() {
    return KeyPairUtil.loadKeyPair(nodePrivateKeyFile());
  }

  private void parse(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final BesuExceptionHandler exceptionHandler,
      final String... args) {
    // Create a handler that will search for a config file option and use it for
    // default values
    // and eventually it will run regular parsing of the remaining options.

    final ConfigOptionSearchAndRunHandler configParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler, exceptionHandler, CONFIG_FILE_OPTION_NAME, environment);

    ParseArgsHelper.getLauncherOptions(unstableLauncherOptions, args);
    if (unstableLauncherOptions.isLauncherMode()
        || unstableLauncherOptions.isLauncherModeForced()) {
      try {
        final ImmutableLauncherConfig launcherConfig =
            ImmutableLauncherConfig.builder()
                .launcherScript(BesuCommand.class.getResourceAsStream("launcher.json"))
                .addCommandClasses(
                    this, unstableNatOptions, unstableEthstatsOptions, unstableMiningOptions)
                .isLauncherForced(unstableLauncherOptions.isLauncherModeForced())
                .build();
        final File file = new LauncherManager(launcherConfig).run();
        logger.info("Config file location : {}", file.getAbsolutePath());
        commandLine.parseWithHandlers(
            configParsingHandler,
            exceptionHandler,
            String.format("%s=%s", CONFIG_FILE_OPTION_NAME, file.getAbsolutePath()));
      } catch (LauncherException e) {
        logger.warn("Unable to run the launcher {}", e.getMessage());
      }
    } else {
      commandLine.parseWithHandlers(configParsingHandler, exceptionHandler, args);
    }
  }

  private void startSynchronization() {
    synchronize(
        besuController,
        p2pEnabled,
        peerDiscoveryEnabled,
        ethNetworkConfig,
        maxPeers,
        p2pHost,
        p2pInterface,
        p2pPort,
        graphQLConfiguration,
        jsonRpcConfiguration,
        webSocketConfiguration,
        apiConfiguration,
        metricsConfiguration,
        permissioningConfiguration,
        staticNodes,
        pidPath);
  }

  private BesuCommand startPlugins() {
    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState()));
    besuPluginContext.addService(MetricsSystem.class, getMetricsSystem());
    besuController.getAdditionalPluginServices().appendPluginServices(besuPluginContext);
    besuPluginContext.startPlugins();
    return this;
  }

  public void configureLogging(final boolean announce) {
    // To change the configuration if color was enabled/disabled
    Configurator.reconfigure();
    // set log level per CLI flags
    if (logLevel != null) {
      if (announce) {
        System.out.println("Setting logging level to " + logLevel.name());
      }
      Configurator.setAllLevels("", logLevel);
    }
  }

  public static Optional<Boolean> getColorEnabled() {
    return Optional.ofNullable(colorEnabled);
  }

  private void configureNativeLibs() {
    if (unstableNativeLibraryOptions.getNativeAltbn128()) {
      AbstractAltBnPrecompiledContract.enableNative();
    }
    if (unstableNativeLibraryOptions.getNativeSecp256k1()) {
      SignatureAlgorithmFactory.getInstance().enableNative();
    }
  }

  private BesuCommand validateOptions() {
    issueOptionWarnings();

    validateP2PInterface(p2pInterface);
    validateMiningParams();
    validateNatParams();
    validateNetStatsParams();
    validateDnsOptionsParams();

    return this;
  }

  @SuppressWarnings("ConstantConditions")
  private void validateMiningParams() {
    if (isMiningEnabled && coinbase == null) {
      throw new ParameterException(
          this.commandLine,
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled) "
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }
    if (!isMiningEnabled && iStratumMiningEnabled) {
      throw new ParameterException(
          this.commandLine,
          "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) "
              + "or specify mining is enabled (--miner-enabled)");
    }
  }

  protected void validateP2PInterface(final String p2pInterface) {
    final String failMessage = "The provided --p2p-interface is not available: " + p2pInterface;
    try {
      if (!NetworkUtility.isNetworkInterfaceAvailable(p2pInterface)) {
        throw new ParameterException(commandLine, failMessage);
      }
    } catch (final UnknownHostException | SocketException e) {
      throw new ParameterException(commandLine, failMessage, e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private void validateNatParams() {
    if (!(natMethod.equals(NatMethod.AUTO) || natMethod.equals(NatMethod.KUBERNETES))
        && !unstableNatOptions
            .getNatManagerServiceName()
            .equals(DEFAULT_BESU_SERVICE_NAME_FILTER)) {
      throw new ParameterException(
          this.commandLine,
          "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name"
              + " or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
    }
    if (natMethod.equals(NatMethod.AUTO) && !unstableNatOptions.getNatMethodFallbackEnabled()) {
      throw new ParameterException(
          this.commandLine,
          "The `--Xnat-method-fallback-enabled` parameter cannot be used in AUTO mode. Either remove --Xnat-method-fallback-enabled"
              + " or select another mode (via --nat--method=XXXX)");
    }
  }

  private void validateNetStatsParams() {
    if (Strings.isNullOrEmpty(unstableEthstatsOptions.getEthstatsUrl())
        && !unstableEthstatsOptions.getEthstatsContact().isEmpty()) {
      throw new ParameterException(
          this.commandLine,
          "The `--Xethstats-contact` requires ethstats server URL to be provided. Either remove --Xethstats-contact"
              + " or provide an url (via --Xethstats=nodename:secret@host:port)");
    }
  }

  private void validateDnsOptionsParams() {
    if (!unstableDnsOptions.getDnsEnabled() && unstableDnsOptions.getDnsUpdateEnabled()) {
      throw new ParameterException(
          this.commandLine,
          "The `--Xdns-update-enabled` requires dns to be enabled. Either remove --Xdns-update-enabled"
              + " or specify dns is enabled (--Xdns-enabled)");
    }
  }

  private GenesisConfigOptions readGenesisConfigOptions() {
    final GenesisConfigOptions genesisConfigOptions;
    try {
      final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(genesisConfig());
      genesisConfigOptions = genesisConfigFile.getConfigOptions(genesisConfigOverrides);
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to read genesis file for GoQuorum options", e);
    }
    return genesisConfigOptions;
  }

  private void issueOptionWarnings() {
    // Check that P2P options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2pEnabled,
        asList(
            "--bootnodes",
            "--discovery-enabled",
            "--max-peers",
            "--banned-node-id",
            "--banned-node-ids",
            "--p2p-host",
            "--p2p-interface",
            "--p2p-port",
            "--remote-connections-max-percentage"));
    // Check that mining options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--miner-enabled",
        !isMiningEnabled,
        asList(
            "--miner-coinbase",
            "--min-gas-price",
            "--min-block-occupancy-ratio",
            "--miner-extra-data",
            "--miner-stratum-enabled",
            "--Xminer-remote-sealers-limit",
            "--Xminer-remote-sealers-hashrate-ttl"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--sync-mode",
        !SyncMode.FAST.equals(syncMode),
        singletonList("--fast-sync-min-peers"));

    if (!securityModuleName.equals(DEFAULT_SECURITY_MODULE) && nodePrivateKeyFile != null) {
      logger.warn(
          DEPENDENCY_WARNING_MSG,
          "--node-private-key-file",
          "--security-module=" + DEFAULT_SECURITY_MODULE);
    }
  }

  private BesuCommand configure() throws Exception {
    checkPortClash();

    syncMode =
        Optional.ofNullable(syncMode)
            .orElse(
                genesisFile == null && !isPrivacyEnabled && network != NetworkName.DEV
                    ? SyncMode.FAST
                    : SyncMode.FULL);

    ethNetworkConfig = updateNetworkConfig(getNetwork());
    if (isGoQuorumCompatibilityMode) {
      checkGoQuorumCompatibilityConfig(ethNetworkConfig);
    }
    jsonRpcConfiguration = jsonRpcConfiguration();
    graphQLConfiguration = graphQLConfiguration();
    webSocketConfiguration = webSocketConfiguration();
    apiConfiguration = apiConfiguration();
    // hostsWhitelist is a hidden option. If it is specified, add the list to hostAllowlist
    if (!hostsWhitelist.isEmpty()) {
      // if allowlist == default values, remove the default values
      if (hostsAllowlist.size() == 2
          && hostsAllowlist.containsAll(List.of("localhost", "127.0.0.1"))) {
        hostsAllowlist.removeAll(List.of("localhost", "127.0.0.1"));
      }
      hostsAllowlist.addAll(hostsWhitelist);
    }

    permissioningConfiguration = permissioningConfiguration();
    staticNodes = loadStaticNodes();

    logger.info("Connecting to {} static nodes.", staticNodes.size());
    logger.trace("Static Nodes = {}", staticNodes);
    final List<EnodeURL> enodeURIs = ethNetworkConfig.getBootNodes();
    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(p -> ensureAllNodesAreInAllowlist(enodeURIs, p));

    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(p -> ensureAllNodesAreInAllowlist(staticNodes, p));
    metricsConfiguration = metricsConfiguration();

    logger.info("Security Module: {}", securityModuleName);
    return this;
  }

  private GoQuorumPrivacyParameters configureGoQuorumPrivacy(
      final KeyValueStorageProvider storageProvider) {
    return new GoQuorumPrivacyParameters(
        createGoQuorumEnclave(),
        readEnclaveKey(),
        storageProvider.createGoQuorumPrivateStorage(),
        createPrivateWorldStateArchive(storageProvider));
  }

  private GoQuorumEnclave createGoQuorumEnclave() {
    final EnclaveFactory enclaveFactory = new EnclaveFactory(Vertx.vertx());
    if (privacyKeyStoreFile != null) {
      return enclaveFactory.createGoQuorumEnclave(
          privacyUrl, privacyKeyStoreFile, privacyKeyStorePasswordFile, privacyTlsKnownEnclaveFile);
    } else {
      return enclaveFactory.createGoQuorumEnclave(privacyUrl);
    }
  }

  private String readEnclaveKey() {
    final String key;
    try {
      key = Files.asCharSource(privacyPublicKeyFile, UTF_8).read();
    } catch (final Exception e) {
      throw new ParameterException(
          this.commandLine,
          "--privacy-public-key-file must be set when --goquorum-compatibility-enabled is set to true.",
          e);
    }
    if (key.length() != 44) {
      throw new IllegalArgumentException(
          "Contents of enclave public key file needs to be 44 characters long to decode to a valid 32 byte public key.");
    }
    // throws exception if invalid base 64
    Base64.getDecoder().decode(key);

    return key;
  }

  private NetworkName getNetwork() {
    // noinspection ConstantConditions network is not always null but injected by
    // PicoCLI if used
    return network == null ? MAINNET : network;
  }

  private void ensureAllNodesAreInAllowlist(
      final Collection<EnodeURL> enodeAddresses,
      final LocalPermissioningConfiguration permissioningConfiguration) {
    try {
      PermissioningConfigurationValidator.areAllNodesAreInAllowlist(
          enodeAddresses, permissioningConfiguration);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage());
    }
  }

  private BesuCommand controller() {
    besuController = buildController();
    return this;
  }

  public BesuController buildController() {
    try {
      return getControllerBuilder().build();
    } catch (final Exception e) {
      throw new ExecutionException(this.commandLine, e.getMessage(), e);
    }
  }

  public BesuControllerBuilder getControllerBuilder() {
    final KeyValueStorageProvider storageProvider = keyValueStorageProvider(keyValueStorageName);
    return controllerBuilderFactory
        .fromEthNetworkConfig(updateNetworkConfig(getNetwork()), genesisConfigOverrides)
        .synchronizerConfiguration(buildSyncConfig())
        .ethProtocolConfiguration(unstableEthProtocolOptions.toDomainObject())
        .dataDirectory(dataDir())
        .miningParameters(
            new MiningParameters(
                coinbase,
                minTransactionGasPrice,
                extraData,
                isMiningEnabled,
                iStratumMiningEnabled,
                stratumNetworkInterface,
                stratumPort,
                unstableMiningOptions.getStratumExtranonce(),
                Optional.empty(),
                minBlockOccupancyRatio,
                unstableMiningOptions.getRemoteSealersLimit(),
                unstableMiningOptions.getRemoteSealersTimeToLive()))
        .transactionPoolConfiguration(buildTransactionPoolConfiguration())
        .nodeKey(buildNodeKey())
        .metricsSystem(metricsSystem.get())
        .privacyParameters(privacyParameters(storageProvider))
        .clock(Clock.systemUTC())
        .isRevertReasonEnabled(isRevertReasonEnabled)
        .storageProvider(storageProvider)
        .isPruningEnabled(isPruningEnabled())
        .pruningConfiguration(
            new PrunerConfiguration(pruningBlockConfirmations, pruningBlocksRetained))
        .genesisConfigOverrides(genesisConfigOverrides)
        .gasLimitCalculator(
            Optional.ofNullable(targetGasLimit)
                .<GasLimitCalculator>map(TargetingGasLimitCalculator::new)
                .orElse(GasLimitCalculator.constant()))
        .requiredBlocks(requiredBlocks)
        .reorgLoggingThreshold(reorgLoggingThreshold)
        .dataStorageConfiguration(unstableDataStorageOptions.toDomainObject());
  }

  private GraphQLConfiguration graphQLConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-http-enabled",
        !isGraphQLHttpEnabled,
        asList("--graphql-http-cors-origins", "--graphql-http-host", "--graphql-http-port"));

    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();
    graphQLConfiguration.setEnabled(isGraphQLHttpEnabled);
    graphQLConfiguration.setHost(graphQLHttpHost);
    graphQLConfiguration.setPort(graphQLHttpPort);
    graphQLConfiguration.setHostsAllowlist(hostsAllowlist);
    graphQLConfiguration.setCorsAllowedDomains(graphQLHttpCorsAllowedOrigins);
    graphQLConfiguration.setHttpTimeoutSec(unstableRPCOptions.getHttpTimeoutSec());

    return graphQLConfiguration;
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    checkRpcTlsClientAuthOptionsDependencies();
    checkRpcTlsOptionsDependencies();

    CommandLineUtils.checkOptionDependencies(
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
            "--rpc-http-authentication-credentials-file",
            "--rpc-http-authentication-public-key-file",
            "--rpc-http-tls-enabled",
            "--rpc-http-tls-keystore-file",
            "--rpc-http-tls-keystore-password-file",
            "--rpc-http-tls-client-auth-enabled",
            "--rpc-http-tls-known-clients-file",
            "--rpc-http-tls-ca-clients-enabled"));

    if (isRpcHttpAuthenticationEnabled
        && rpcHttpAuthenticationCredentialsFile() == null
        && rpcHttpAuthenticationPublicKeyFile == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file or authentication public key file");
    }

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(isRpcHttpEnabled);
    jsonRpcConfiguration.setHost(rpcHttpHost);
    jsonRpcConfiguration.setPort(rpcHttpPort);
    jsonRpcConfiguration.setCorsAllowedDomains(rpcHttpCorsAllowedOrigins);
    jsonRpcConfiguration.setRpcApis(rpcHttpApis.stream().distinct().collect(Collectors.toList()));
    jsonRpcConfiguration.setHostsAllowlist(hostsAllowlist);
    jsonRpcConfiguration.setAuthenticationEnabled(isRpcHttpAuthenticationEnabled);
    jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile());
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(rpcHttpAuthenticationPublicKeyFile);
    jsonRpcConfiguration.setTlsConfiguration(rpcHttpTlsConfiguration());
    jsonRpcConfiguration.setHttpTimeoutSec(unstableRPCOptions.getHttpTimeoutSec());
    return jsonRpcConfiguration;
  }

  private void checkRpcTlsOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-tls-enabled",
        !isRpcHttpTlsEnabled,
        asList(
            "--rpc-http-tls-keystore-file",
            "--rpc-http-tls-keystore-password-file",
            "--rpc-http-tls-client-auth-enabled",
            "--rpc-http-tls-known-clients-file",
            "--rpc-http-tls-ca-clients-enabled"));
  }

  private void checkRpcTlsClientAuthOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-tls-client-auth-enabled",
        !isRpcHttpTlsClientAuthEnabled,
        asList("--rpc-http-tls-known-clients-file", "--rpc-http-tls-ca-clients-enabled"));
  }

  private void checkPrivacyTlsOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-tls-enabled",
        !isPrivacyTlsEnabled,
        asList(
            "--privacy-tls-keystore-file",
            "--privacy-tls-keystore-password-file",
            "--privacy-tls-known-enclave-file"));
  }

  private Optional<TlsConfiguration> rpcHttpTlsConfiguration() {
    if (!isRpcTlsConfigurationRequired()) {
      return Optional.empty();
    }

    if (rpcHttpTlsKeyStoreFile == null) {
      throw new ParameterException(
          commandLine, "Keystore file is required when TLS is enabled for JSON-RPC HTTP endpoint");
    }

    if (rpcHttpTlsKeyStorePasswordFile == null) {
      throw new ParameterException(
          commandLine,
          "File containing password to unlock keystore is required when TLS is enabled for JSON-RPC HTTP endpoint");
    }

    if (isRpcHttpTlsClientAuthEnabled
        && !isRpcHttpTlsCAClientsEnabled
        && rpcHttpTlsKnownClientsFile == null) {
      throw new ParameterException(
          commandLine,
          "Known-clients file must be specified or CA clients must be enabled when TLS client authentication is enabled for JSON-RPC HTTP endpoint");
    }

    return Optional.of(
        TlsConfiguration.Builder.aTlsConfiguration()
            .withKeyStorePath(rpcHttpTlsKeyStoreFile)
            .withKeyStorePasswordSupplier(
                new FileBasedPasswordProvider(rpcHttpTlsKeyStorePasswordFile))
            .withClientAuthConfiguration(rpcHttpTlsClientAuthConfiguration())
            .build());
  }

  private TlsClientAuthConfiguration rpcHttpTlsClientAuthConfiguration() {
    if (isRpcHttpTlsClientAuthEnabled) {
      return TlsClientAuthConfiguration.Builder.aTlsClientAuthConfiguration()
          .withKnownClientsFile(rpcHttpTlsKnownClientsFile)
          .withCaClientsEnabled(isRpcHttpTlsCAClientsEnabled)
          .build();
    }

    return null;
  }

  private boolean isRpcTlsConfigurationRequired() {
    return isRpcHttpEnabled && isRpcHttpTlsEnabled;
  }

  private WebSocketConfiguration webSocketConfiguration() {

    CommandLineUtils.checkOptionDependencies(
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
            "--rpc-ws-authentication-credentials-file",
            "--rpc-ws-authentication-public-key-file"));

    if (isRpcWsAuthenticationEnabled
        && rpcWsAuthenticationCredentialsFile() == null
        && rpcWsAuthenticationPublicKeyFile == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file or authentication public key file");
    }

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(isRpcWsEnabled);
    webSocketConfiguration.setHost(rpcWsHost);
    webSocketConfiguration.setPort(rpcWsPort);
    webSocketConfiguration.setRpcApis(rpcWsApis);
    webSocketConfiguration.setAuthenticationEnabled(isRpcWsAuthenticationEnabled);
    webSocketConfiguration.setAuthenticationCredentialsFile(rpcWsAuthenticationCredentialsFile());
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    webSocketConfiguration.setAuthenticationPublicKeyFile(rpcWsAuthenticationPublicKeyFile);
    webSocketConfiguration.setTimeoutSec(unstableRPCOptions.getWsTimeoutSec());
    return webSocketConfiguration;
  }

  private ApiConfiguration apiConfiguration() {
    return ImmutableApiConfiguration.builder()
        .gasPriceBlocks(apiGasPriceBlocks)
        .gasPricePercentile(apiGasPricePercentile)
        .gasPriceMin(minTransactionGasPrice.toLong())
        .gasPriceMax(apiGasPriceMax)
        .build();
  }

  public MetricsConfiguration metricsConfiguration() {
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
        asList("--metrics-host", "--metrics-port"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !isMetricsPushEnabled,
        asList(
            "--metrics-push-host",
            "--metrics-push-port",
            "--metrics-push-interval",
            "--metrics-push-prometheus-job"));

    return unstableMetricsCLIOptions
        .toDomainObject()
        .enabled(isMetricsEnabled)
        .host(metricsHost)
        .port(metricsPort)
        .protocol(metricsProtocol)
        .metricCategories(metricCategories)
        .pushEnabled(isMetricsPushEnabled)
        .pushHost(metricsPushHost)
        .pushPort(metricsPushPort)
        .pushInterval(metricsPushInterval)
        .hostsAllowlist(hostsAllowlist)
        .prometheusJob(metricsPrometheusJob)
        .build();
  }

  private Optional<PermissioningConfiguration> permissioningConfiguration() throws Exception {
    if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
      if (rpcHttpApis.contains(RpcApis.PERM) || rpcWsApis.contains(RpcApis.PERM)) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    final Optional<LocalPermissioningConfiguration> localPermissioningConfigurationOptional;
    if (localPermissionsEnabled()) {
      final Optional<String> nodePermissioningConfigFile =
          Optional.ofNullable(nodePermissionsConfigFile);
      final Optional<String> accountPermissioningConfigFile =
          Optional.ofNullable(accountPermissionsConfigFile);

      final LocalPermissioningConfiguration localPermissioningConfiguration =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              permissionsNodesEnabled,
              getEnodeDnsConfiguration(),
              nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath()),
              permissionsAccountsEnabled,
              accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath()));

      localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration);
    } else {
      if (nodePermissionsConfigFile != null && !permissionsNodesEnabled) {
        logger.warn(
            "Node permissioning config file set {} but no permissions enabled",
            nodePermissionsConfigFile);
      }

      if (accountPermissionsConfigFile != null && !permissionsAccountsEnabled) {
        logger.warn(
            "Account permissioning config file set {} but no permissions enabled",
            accountPermissionsConfigFile);
      }
      localPermissioningConfigurationOptional = Optional.empty();
    }

    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        SmartContractPermissioningConfiguration.createDefault();

    if (permissionsNodesContractEnabled) {
      if (permissionsNodesContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No node permissioning contract address specified. Cannot enable smart contract based node permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractNodeAllowlistEnabled(
            permissionsNodesContractEnabled);
        smartContractPermissioningConfiguration.setNodeSmartContractAddress(
            permissionsNodesContractAddress);
        smartContractPermissioningConfiguration.setNodeSmartContractInterfaceVersion(
            permissionsNodesContractVersion);
      }
    } else if (permissionsNodesContractAddress != null) {
      logger.warn(
          "Node permissioning smart contract address set {} but smart contract node permissioning is disabled.",
          permissionsNodesContractAddress);
    }

    if (permissionsAccountsContractEnabled) {
      if (permissionsAccountsContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No account permissioning contract address specified. Cannot enable smart contract based account permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractAccountAllowlistEnabled(
            permissionsAccountsContractEnabled);
        smartContractPermissioningConfiguration.setAccountSmartContractAddress(
            permissionsAccountsContractAddress);
      }
    } else if (permissionsAccountsContractAddress != null) {
      logger.warn(
          "Account permissioning smart contract address set {} but smart contract account permissioning is disabled.",
          permissionsAccountsContractAddress);
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            localPermissioningConfigurationOptional,
            Optional.of(smartContractPermissioningConfiguration),
            quorumPermissioningConfig());

    return Optional.of(permissioningConfiguration);
  }

  private Optional<GoQuorumPermissioningConfiguration> quorumPermissioningConfig() {
    if (!isGoQuorumCompatibilityMode) {
      return Optional.empty();
    }

    try {
      final GenesisConfigOptions genesisConfigOptions = readGenesisConfigOptions();
      final OptionalLong qip714BlockNumber = genesisConfigOptions.getQip714BlockNumber();
      return Optional.of(
          GoQuorumPermissioningConfiguration.enabled(
              qip714BlockNumber.orElse(QIP714_DEFAULT_BLOCK)));
    } catch (final Exception e) {
      throw new IllegalStateException("Error reading GoQuorum permissioning options", e);
    }
  }

  private boolean localPermissionsEnabled() {
    return permissionsAccountsEnabled || permissionsNodesEnabled;
  }

  private boolean contractPermissionsEnabled() {
    return permissionsNodesContractEnabled || permissionsAccountsContractEnabled;
  }

  private PrivacyParameters privacyParameters(final KeyValueStorageProvider storageProvider) {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-enabled",
        !isPrivacyEnabled,
        asList(
            "--privacy-url",
            "--privacy-public-key-file",
            "--privacy-multi-tenancy-enabled",
            "--privacy-tls-enabled"));

    checkPrivacyTlsOptionsDependencies();

    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    if (isPrivacyEnabled) {
      final String errorSuffix = "cannot be enabled with privacy.";
      if (syncMode == SyncMode.FAST) {
        throw new ParameterException(commandLine, String.format("%s %s", "Fast sync", errorSuffix));
      }
      if (isPruningEnabled()) {
        throw new ParameterException(commandLine, String.format("%s %s", "Pruning", errorSuffix));
      }
      if (isGoQuorumCompatibilityMode) {
        throw new ParameterException(
            commandLine, String.format("%s %s", "GoQuorum mode", errorSuffix));
      }

      if (isPrivacyMultiTenancyEnabled
          && !jsonRpcConfiguration.isAuthenticationEnabled()
          && !webSocketConfiguration.isAuthenticationEnabled()) {
        throw new ParameterException(
            commandLine,
            "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled");
      }

      privacyParametersBuilder.setEnabled(true);
      privacyParametersBuilder.setEnclaveUrl(privacyUrl);
      privacyParametersBuilder.setMultiTenancyEnabled(isPrivacyMultiTenancyEnabled);
      privacyParametersBuilder.setOnchainPrivacyGroupsEnabled(isFlexiblePrivacyGroupsEnabled);

      final boolean hasPrivacyPublicKey = privacyPublicKeyFile != null;
      if (hasPrivacyPublicKey && !isPrivacyMultiTenancyEnabled) {
        try {
          privacyParametersBuilder.setEnclavePublicKeyUsingFile(privacyPublicKeyFile);
        } catch (final IOException e) {
          throw new ParameterException(
              commandLine, "Problem with privacy-public-key-file: " + e.getMessage(), e);
        } catch (final IllegalArgumentException e) {
          throw new ParameterException(
              commandLine, "Contents of privacy-public-key-file invalid: " + e.getMessage(), e);
        }
      } else if (hasPrivacyPublicKey) {
        throw new ParameterException(
            commandLine, "Privacy multi-tenancy and privacy public key cannot be used together");
      } else if (!isPrivacyMultiTenancyEnabled) {
        throw new ParameterException(
            commandLine, "Please specify Enclave public key file path to enable privacy");
      }

      if (Wei.ZERO.compareTo(minTransactionGasPrice) < 0) {
        // if gas is required, cannot use random keys to sign private tx
        // ie --privacy-marker-transaction-signing-key-file must be set
        if (privacyMarkerTransactionSigningKeyPath == null) {
          throw new ParameterException(
              commandLine,
              "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified and must be a funded account. Private transactions cannot be signed by random (non-funded) accounts in paid gas networks");
        }
      }

      if (!Address.PRIVACY.equals(privacyPrecompiledAddress)) {
        logger.warn(
            "--privacy-precompiled-address option is deprecated. This address is derived, based on --privacy-onchain-groups-enabled.");
      }

      privacyParametersBuilder.setPrivateKeyPath(privacyMarkerTransactionSigningKeyPath);
      privacyParametersBuilder.setStorageProvider(
          privacyKeyStorageProvider(keyValueStorageName + "-privacy"));
      if (isPrivacyTlsEnabled) {
        privacyParametersBuilder.setPrivacyKeyStoreFile(privacyKeyStoreFile);
        privacyParametersBuilder.setPrivacyKeyStorePasswordFile(privacyKeyStorePasswordFile);
        privacyParametersBuilder.setPrivacyTlsKnownEnclaveFile(privacyTlsKnownEnclaveFile);
      }
      privacyParametersBuilder.setEnclaveFactory(new EnclaveFactory(vertx));
    } else if (isGoQuorumCompatibilityMode) {
      privacyParametersBuilder.setGoQuorumPrivacyParameters(
          Optional.of(configureGoQuorumPrivacy(storageProvider)));
    }

    if (!isPrivacyEnabled && anyPrivacyApiEnabled()) {
      logger.warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");
    }

    if (!isGoQuorumCompatibilityMode
        && (rpcHttpApis.contains(RpcApis.GOQUORUM) || rpcWsApis.contains(RpcApis.GOQUORUM))) {
      logger.warn("Cannot use GOQUORUM API methods when not in GoQuorum mode.");
    }

    final PrivacyParameters privacyParameters = privacyParametersBuilder.build();

    if (isPrivacyEnabled) {
      preSynchronizationTaskRunner.addTask(
          new PrivateDatabaseMigrationPreSyncTask(privacyParameters, migratePrivateDatabase));
    }

    return privacyParameters;
  }

  public WorldStateArchive createPrivateWorldStateArchive(final StorageProvider storageProvider) {
    final WorldStateStorage privateWorldStateStorage =
        storageProvider.createPrivateWorldStateStorage();
    final WorldStatePreimageStorage preimageStorage =
        storageProvider.createPrivateWorldStatePreimageStorage();
    return new DefaultWorldStateArchive(privateWorldStateStorage, preimageStorage);
  }

  private boolean anyPrivacyApiEnabled() {
    return rpcHttpApis.contains(RpcApis.EEA)
        || rpcWsApis.contains(RpcApis.EEA)
        || rpcHttpApis.contains(RpcApis.PRIV)
        || rpcWsApis.contains(RpcApis.PRIV);
  }

  private PrivacyKeyValueStorageProvider privacyKeyStorageProvider(final String name) {
    return new PrivacyKeyValueStorageProviderBuilder()
        .withStorageFactory(privacyKeyValueStorageFactory(name))
        .withCommonConfiguration(pluginCommonConfiguration)
        .withMetricsSystem(getMetricsSystem())
        .build();
  }

  private PrivacyKeyValueStorageFactory privacyKeyValueStorageFactory(final String name) {
    return (PrivacyKeyValueStorageFactory)
        storageService
            .getByName(name)
            .orElseThrow(
                () -> new StorageException("No KeyValueStorageFactory found for key: " + name));
  }

  private KeyValueStorageProvider keyValueStorageProvider(final String name) {
    if (this.keyValueStorageProvider == null) {
      this.keyValueStorageProvider =
          new KeyValueStorageProviderBuilder()
              .withStorageFactory(
                  storageService
                      .getByName(name)
                      .orElseThrow(
                          () ->
                              new StorageException(
                                  "No KeyValueStorageFactory found for key: " + name)))
              .withCommonConfiguration(pluginCommonConfiguration)
              .withMetricsSystem(getMetricsSystem())
              .build();
    }
    return this.keyValueStorageProvider;
  }

  private SynchronizerConfiguration buildSyncConfig() {
    return unstableSynchronizerOptions
        .toDomainObject()
        .syncMode(syncMode)
        .fastSyncMinimumPeerCount(fastSyncMinPeerCount)
        .build();
  }

  private TransactionPoolConfiguration buildTransactionPoolConfiguration() {
    return unstableTransactionPoolOptions
        .toDomainObject()
        .txPoolMaxSize(txPoolMaxSize)
        .pooledTransactionHashesSize(pooledTransactionHashesSize)
        .pendingTxRetentionPeriod(pendingTxRetentionPeriod)
        .priceBump(Percentage.fromInt(priceBump))
        .txFeeCap(txFeeCap)
        .build();
  }

  private boolean isPruningEnabled() {
    return pruningEnabled;
  }

  // Blockchain synchronisation from peers.
  private void synchronize(
      final BesuController controller,
      final boolean p2pEnabled,
      final boolean peerDiscoveryEnabled,
      final EthNetworkConfig ethNetworkConfig,
      final int maxPeers,
      final String p2pAdvertisedHost,
      final String p2pListenInterface,
      final int p2pListenPort,
      final GraphQLConfiguration graphQLConfiguration,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final ApiConfiguration apiConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Collection<EnodeURL> staticNodes,
      final Path pidPath) {

    checkNotNull(runnerBuilder);

    permissioningConfiguration.ifPresent(runnerBuilder::permissioningConfiguration);

    final ObservableMetricsSystem metricsSystem = this.metricsSystem.get();
    final Runner runner =
        runnerBuilder
            .vertx(vertx)
            .besuController(controller)
            .p2pEnabled(p2pEnabled)
            .natMethod(natMethod)
            .natManagerServiceName(unstableNatOptions.getNatManagerServiceName())
            .natMethodFallbackEnabled(unstableNatOptions.getNatMethodFallbackEnabled())
            .discovery(peerDiscoveryEnabled)
            .ethNetworkConfig(ethNetworkConfig)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pListenInterface(p2pListenInterface)
            .p2pListenPort(p2pListenPort)
            .maxPeers(maxPeers)
            .limitRemoteWireConnectionsEnabled(isLimitRemoteWireConnectionsEnabled)
            .fractionRemoteConnectionsAllowed(
                Fraction.fromPercentage(maxRemoteConnectionsPercentage).getValue())
            .randomPeerPriority(randomPeerPriority)
            .networkingConfiguration(unstableNetworkingOptions.toDomainObject())
            .graphQLConfiguration(graphQLConfiguration)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .apiConfiguration(apiConfiguration)
            .pidPath(pidPath)
            .dataDir(dataDir())
            .bannedNodeIds(bannedNodeIds)
            .metricsSystem(metricsSystem)
            .metricsConfiguration(metricsConfiguration)
            .staticNodes(staticNodes)
            .identityString(identityString)
            .besuPluginContext(besuPluginContext)
            .autoLogBloomCaching(autoLogBloomCachingEnabled)
            .ethstatsUrl(unstableEthstatsOptions.getEthstatsUrl())
            .ethstatsContact(unstableEthstatsOptions.getEthstatsContact())
            .storageProvider(keyValueStorageProvider(keyValueStorageName))
            .forkIdSupplier(() -> besuController.getProtocolManager().getForkIdAsBytesList())
            .build();

    addShutdownHook(runner);
    runner.start();
    runner.awaitStop();
  }

  protected Vertx createVertx(final VertxOptions vertxOptions) {
    return Vertx.vertx(vertxOptions);
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
                    besuPluginContext.stopPlugins();
                    runner.close();
                    LogManager.shutdown();
                  } catch (final Exception e) {
                    logger.error("Failed to stop Besu");
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

    // custom genesis file use comes with specific default values for the genesis
    // file itself
    // but also for the network id and the bootnodes list.
    if (genesisFile != null) {

      // noinspection ConstantConditions network is not always null but injected by
      // PicoCLI if used
      if (this.network != null) {
        // We check if network option was really provided by user and not only looking
        // at the
        // default value.
        // if user provided it and provided the genesis file option at the same time, it
        // raises a
        // conflict error
        throw new ParameterException(
            this.commandLine,
            "--network option and --genesis-file option can't be used at the same time.  Please "
                + "refer to CLI reference for more details about this constraint.");
      }

      builder.setGenesisConfig(genesisConfig());

      if (networkId == null) {
        // if no network id option is defined on the CLI we have to set a default value
        // from the
        // genesis file.
        // We do the genesis parsing only in this case as we already have network id
        // constants
        // for known networks to speed up the process.
        // Also we have to parse the genesis as we don't already have a parsed version
        // at this
        // stage.
        // If no chain id is found in the genesis as it's an optional, we use mainnet
        // network id.
        try {
          builder.setNetworkId(
              getGenesisConfigFile()
                  .getConfigOptions(genesisConfigOverrides)
                  .getChainId()
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
        // We default to an empty bootnodes list if the option is not provided on CLI
        // because
        // mainnet bootnodes won't work as the default value for a custom genesis,
        // so it's better to have an empty list as default value that forces to create a
        // custom one
        // than a useless one that may make user think that it can work when it can't.
        builder.setBootNodes(new ArrayList<>());
      }
      builder.setDnsDiscoveryUrl(null);
    }

    if (networkId != null) {
      builder.setNetworkId(networkId);
    }

    if (bootNodes != null) {
      if (!peerDiscoveryEnabled) {
        logger.warn("Discovery disabled: bootnodes will be ignored.");
      }
      try {
        final List<EnodeURL> listBootNodes =
            bootNodes.stream()
                .filter(value -> !value.isEmpty())
                .map(url -> EnodeURL.fromString(url, getEnodeDnsConfiguration()))
                .collect(Collectors.toList());
        DiscoveryConfiguration.assertValidBootnodes(listBootNodes);
        builder.setBootNodes(listBootNodes);
      } catch (final IllegalArgumentException e) {
        throw new ParameterException(commandLine, e.getMessage());
      }
    }
    return builder.build();
  }

  private GenesisConfigFile getGenesisConfigFile() {
    return GenesisConfigFile.fromConfig(genesisConfig());
  }

  private String genesisConfig() {
    try {
      return Resources.toString(genesisFile.toURI().toURL(), UTF_8);
    } catch (final IOException e) {
      throw new ParameterException(
          this.commandLine, String.format("Unable to load genesis file %s.", genesisFile), e);
    }
  }

  // dataDir() is public because it is accessed by subcommands
  public Path dataDir() {
    return dataPath.toAbsolutePath();
  }

  private Path pluginsDir() {
    final String pluginsDir = System.getProperty("besu.plugins.dir");
    if (pluginsDir == null) {
      return new File(System.getProperty("besu.home", "."), "plugins").toPath();
    } else {
      return new File(pluginsDir).toPath();
    }
  }

  @VisibleForTesting
  NodeKey buildNodeKey() {
    return new NodeKey(securityModule());
  }

  private SecurityModule securityModule() {
    return securityModuleService
        .getByName(securityModuleName)
        .orElseThrow(() -> new RuntimeException("Security Module not found: " + securityModuleName))
        .get();
  }

  private File nodePrivateKeyFile() {
    return Optional.ofNullable(nodePrivateKeyFile)
        .orElseGet(() -> KeyPairUtil.getDefaultKeyFile(dataDir()));
  }

  private String rpcHttpAuthenticationCredentialsFile() {
    final String filename = rpcHttpAuthenticationCredentialsFile;

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "HTTP");
    }
    return filename;
  }

  private String rpcWsAuthenticationCredentialsFile() {
    final String filename = rpcWsAuthenticationCredentialsFile;

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "WS");
    }
    return filename;
  }

  private String getDefaultPermissioningFilePath() {
    return dataDir()
        + System.getProperty("file.separator")
        + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem.get();
  }

  private Set<EnodeURL> loadStaticNodes() throws IOException {
    final Path staticNodesPath;
    if (staticNodesFile != null) {
      staticNodesPath = staticNodesFile.toAbsolutePath();
      if (!staticNodesPath.toFile().exists()) {
        throw new ParameterException(
            commandLine, String.format("Static nodes file %s does not exist", staticNodesPath));
      }
    } else {
      final String staticNodesFilename = "static-nodes.json";
      staticNodesPath = dataDir().resolve(staticNodesFilename);
    }
    logger.info("Static Nodes file = {}", staticNodesPath);
    return StaticNodesParser.fromPath(staticNodesPath, getEnodeDnsConfiguration());
  }

  public BesuExceptionHandler exceptionHandler() {
    return new BesuExceptionHandler(this::getLogLevel);
  }

  public EnodeDnsConfiguration getEnodeDnsConfiguration() {
    if (enodeDnsConfiguration == null) {
      enodeDnsConfiguration = unstableDnsOptions.toDomainObject();
    }
    return enodeDnsConfiguration;
  }

  private void checkPortClash() {
    getEffectivePorts().stream()
        .filter(Objects::nonNull)
        .filter(port -> port > 0)
        .forEach(
            port -> {
              if (!allocatedPorts.add(port)) {
                throw new ParameterException(
                    commandLine,
                    "Port number '"
                        + port
                        + "' has been specified multiple times. Please review the supplied configuration.");
              }
            });
  }

  /**
   * * Gets the list of effective ports (ports that are enabled).
   *
   * @return The list of effective ports
   */
  private List<Integer> getEffectivePorts() {
    final List<Integer> effectivePorts = new ArrayList<>();
    addPortIfEnabled(effectivePorts, p2pPort, p2pEnabled);
    addPortIfEnabled(effectivePorts, graphQLHttpPort, isGraphQLHttpEnabled);
    addPortIfEnabled(effectivePorts, rpcHttpPort, isRpcHttpEnabled);
    addPortIfEnabled(effectivePorts, rpcWsPort, isRpcWsEnabled);
    addPortIfEnabled(effectivePorts, metricsPort, isMetricsEnabled);
    addPortIfEnabled(effectivePorts, metricsPushPort, isMetricsPushEnabled);
    addPortIfEnabled(effectivePorts, stratumPort, iStratumMiningEnabled);
    return effectivePorts;
  }

  /**
   * Adds port in the passed list only if enabled.
   *
   * @param ports The list of ports
   * @param port The port value
   * @param enabled true if enabled, false otherwise
   */
  private void addPortIfEnabled(
      final List<Integer> ports, final Integer port, final boolean enabled) {
    if (enabled) {
      ports.add(port);
    }
  }

  private void checkGoQuorumCompatibilityConfig(final EthNetworkConfig ethNetworkConfig) {
    if (isGoQuorumCompatibilityMode) {
      final GenesisConfigOptions genesisConfigOptions = readGenesisConfigOptions();
      // this static flag is read by the RLP decoder
      GoQuorumOptions.goQuorumCompatibilityMode = true;

      if (!genesisConfigOptions.isQuorum()) {
        throw new IllegalStateException(
            "GoQuorum compatibility mode (enabled) can only be used if genesis file has 'isQuorum' flag set to true.");
      }
      genesisConfigOptions
          .getChainId()
          .ifPresent(
              chainId ->
                  ensureGoQuorumCompatibilityModeNotUsedOnMainnet(
                      chainId, isGoQuorumCompatibilityMode));

      if (genesisFile != null
          && getGenesisConfigFile().getConfigOptions().isQuorum()
          && !minTransactionGasPrice.isZero()) {
        throw new ParameterException(
            this.commandLine,
            "--min-gas-price must be set to zero if GoQuorum compatibility is enabled in the genesis config.");
      }
      if (ethNetworkConfig.getNetworkId().equals(EthNetworkConfig.MAINNET_NETWORK_ID)) {
        throw new ParameterException(
            this.commandLine, "GoQuorum compatibility mode (enabled) cannot be used on Mainnet.");
      }
    }
  }

  private void ensureGoQuorumCompatibilityModeNotUsedOnMainnet(
      final BigInteger chainId, final boolean isGoQuorumCompatibilityMode) {
    if (isGoQuorumCompatibilityMode && chainId.equals(EthNetworkConfig.MAINNET_NETWORK_ID)) {
      throw new IllegalStateException(
          "GoQuorum compatibility mode (enabled) cannot be used on Mainnet.");
    }
  }

  @VisibleForTesting
  Level getLogLevel() {
    return logLevel;
  }

  private class BesuCommandConfigurationService implements BesuConfiguration {

    @Override
    public Path getStoragePath() {
      return dataDir().resolve(DATABASE_PATH);
    }

    @Override
    public Path getDataPath() {
      return dataDir();
    }

    @Override
    public int getDatabaseVersion() {
      return unstableDataStorageOptions
          .toDomainObject()
          .getDataStorageFormat()
          .getDatabaseVersion();
    }
  }
}
