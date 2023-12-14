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
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPRECATION_WARNING_MSG;
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;
import static org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration.DEFAULT_GRAPHQL_HTTP_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration.DEFAULT_ENGINE_JSON_RPC_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration.DEFAULT_PRETTY_JSON_ENABLED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.VALID_APIS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.authentication.EngineAuthService.EPHEMERAL_JWT_FILE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT;
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
import org.hyperledger.besu.cli.custom.CorsAllowedOriginsProperty;
import org.hyperledger.besu.cli.custom.JsonRPCAllowlistHostsProperty;
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator;
import org.hyperledger.besu.cli.error.BesuExecutionExceptionHandler;
import org.hyperledger.besu.cli.error.BesuParameterExceptionHandler;
import org.hyperledger.besu.cli.options.MiningOptions;
import org.hyperledger.besu.cli.options.TransactionPoolOptions;
import org.hyperledger.besu.cli.options.stable.DataStorageOptions;
import org.hyperledger.besu.cli.options.stable.EthstatsOptions;
import org.hyperledger.besu.cli.options.stable.LoggingLevelOption;
import org.hyperledger.besu.cli.options.stable.NodePrivateKeyFileOption;
import org.hyperledger.besu.cli.options.stable.P2PTLSConfigOptions;
import org.hyperledger.besu.cli.options.unstable.ChainPruningOptions;
import org.hyperledger.besu.cli.options.unstable.DnsOptions;
import org.hyperledger.besu.cli.options.unstable.EthProtocolOptions;
import org.hyperledger.besu.cli.options.unstable.EvmOptions;
import org.hyperledger.besu.cli.options.unstable.IpcOptions;
import org.hyperledger.besu.cli.options.unstable.MetricsCLIOptions;
import org.hyperledger.besu.cli.options.unstable.NatOptions;
import org.hyperledger.besu.cli.options.unstable.NativeLibraryOptions;
import org.hyperledger.besu.cli.options.unstable.NetworkingOptions;
import org.hyperledger.besu.cli.options.unstable.PkiBlockCreationOptions;
import org.hyperledger.besu.cli.options.unstable.PrivacyPluginOptions;
import org.hyperledger.besu.cli.options.unstable.RPCOptions;
import org.hyperledger.besu.cli.options.unstable.SynchronizerOptions;
import org.hyperledger.besu.cli.presynctasks.PreSynchronizationTaskRunner;
import org.hyperledger.besu.cli.presynctasks.PrivateDatabaseMigrationPreSyncTask;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand;
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand;
import org.hyperledger.besu.cli.subcommands.RetestethSubCommand;
import org.hyperledger.besu.cli.subcommands.TxParseSubCommand;
import org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand;
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand;
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand;
import org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand;
import org.hyperledger.besu.cli.util.BesuCommandCustomFactory;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.cli.util.ConfigOptionSearchAndRunHandler;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.CheckpointConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.MergeConfigOptions;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfigurationProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.crypto.Blake2bfMessageDigest;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.crypto.SignatureAlgorithmType;
import org.hyperledger.besu.cryptoservices.KeyPairSecurityModule;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider;
import org.hyperledger.besu.ethereum.api.tls.TlsClientAuthConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.FrontierTargetingGasLimitCalculator;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.StaticNodesParser;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.trie.forest.pruner.PrunerConfiguration;
import org.hyperledger.besu.evm.precompile.AbstractAltBnPrecompiledContract;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.MetricCategoryRegistryImpl;
import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.metrics.MetricsSystemFactory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PluginTransactionValidatorService;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionValidatorFactory;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.PluginTransactionValidatorServiceImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.TraceServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryStoragePlugin;
import org.hyperledger.besu.util.InvalidConfigurationException;
import org.hyperledger.besu.util.LogConfigurator;
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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.metrics.MetricsOptions;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/** Represents the main Besu CLI command that runs the Besu Ethereum client full node. */
@SuppressWarnings("FieldCanBeLocal") // because Picocli injected fields report false positives
@Command(
    description = "This command runs the Besu Ethereum client full node.",
    abbreviateSynopsis = true,
    name = "besu",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    header = "@|bold,fg(cyan) Usage:|@",
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold,fg(cyan) Description:|@%n%n",
    optionListHeading = "%n@|bold,fg(cyan) Options:|@%n",
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
  private final DnsOptions unstableDnsOptions = DnsOptions.create();
  private final NatOptions unstableNatOptions = NatOptions.create();
  private final NativeLibraryOptions unstableNativeLibraryOptions = NativeLibraryOptions.create();
  private final RPCOptions unstableRPCOptions = RPCOptions.create();
  private final PrivacyPluginOptions unstablePrivacyPluginOptions = PrivacyPluginOptions.create();
  private final EvmOptions unstableEvmOptions = EvmOptions.create();
  private final IpcOptions unstableIpcOptions = IpcOptions.create();
  private final ChainPruningOptions unstableChainPruningOptions = ChainPruningOptions.create();

  // stable CLI options
  final DataStorageOptions dataStorageOptions = DataStorageOptions.create();
  private final EthstatsOptions ethstatsOptions = EthstatsOptions.create();
  private final NodePrivateKeyFileOption nodePrivateKeyFileOption =
      NodePrivateKeyFileOption.create();
  private final LoggingLevelOption loggingLevelOption = LoggingLevelOption.create();

  @CommandLine.ArgGroup(validate = false, heading = "@|bold Tx Pool Common Options|@%n")
  final TransactionPoolOptions transactionPoolOptions = TransactionPoolOptions.create();

  @CommandLine.ArgGroup(validate = false, heading = "@|bold Block Builder Options|@%n")
  final MiningOptions miningOptions = MiningOptions.create();

  private final RunnerBuilder runnerBuilder;
  private final BesuController.Builder controllerBuilderFactory;
  private final BesuPluginContextImpl besuPluginContext;
  private final StorageServiceImpl storageService;
  private final SecurityModuleServiceImpl securityModuleService;
  private final PermissioningServiceImpl permissioningService;
  private final PrivacyPluginServiceImpl privacyPluginService;
  private final RpcEndpointServiceImpl rpcEndpointServiceImpl;

  private final Map<String, String> environment;
  private final MetricCategoryRegistryImpl metricCategoryRegistry =
      new MetricCategoryRegistryImpl();
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  private final PreSynchronizationTaskRunner preSynchronizationTaskRunner =
      new PreSynchronizationTaskRunner();

  private final Set<Integer> allocatedPorts = new HashSet<>();
  private final PkiBlockCreationConfigurationProvider pkiBlockCreationConfigProvider;
  private GenesisConfigOptions genesisConfigOptions;

  private RocksDBPlugin rocksDBPlugin;

  private int maxPeers;
  private int maxRemoteInitiatedPeers;
  private int peersLowerBound;

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

  @Option(
      names = "--identity",
      paramLabel = "<String>",
      description = "Identification for this node in the Client ID",
      arity = "1")
  private final Optional<String> identityString = Optional.empty();
  // P2P Discovery Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold P2P Discovery Options|@%n")
  P2PDiscoveryOptionGroup p2PDiscoveryOptionGroup = new P2PDiscoveryOptionGroup();

  private final TransactionSelectionServiceImpl transactionSelectionServiceImpl;
  private final PluginTransactionValidatorServiceImpl transactionValidatorServiceImpl;

  static class P2PDiscoveryOptionGroup {

    // Public IP stored to prevent having to research it each time we need it.
    private InetAddress autoDiscoveredDefaultIP = null;

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

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(
        names = {"--p2p-host"},
        paramLabel = MANDATORY_HOST_FORMAT_HELP,
        description = "IP address this node advertises to its peers (default: ${DEFAULT-VALUE})",
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
    private final Integer p2pPort = EnodeURLImpl.DEFAULT_LISTENING_PORT;

    @Option(
        names = {"--max-peers", "--p2p-peer-upper-bound"},
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
    private final Percentage maxRemoteConnectionsPercentage =
        Fraction.fromFloat(DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED).toPercentage();

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = {"--discovery-dns-url"},
        description = "Specifies the URL to use for DNS discovery")
    private String discoveryDnsUrl = null;

    @Option(
        names = {"--random-peer-priority-enabled"},
        description =
            "Allow for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. (default: ${DEFAULT-VALUE})")
    private final Boolean randomPeerPriority = Boolean.FALSE;

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
                .map(EnodeURLImpl::parseNodeId)
                .collect(Collectors.toList());
      } catch (final IllegalArgumentException e) {
        throw new ParameterException(
            new CommandLine(this),
            "Invalid ids supplied to '--banned-node-ids'. " + e.getMessage());
      }
    }

    private Collection<Bytes> bannedNodeIds = new ArrayList<>();

    // Used to discover the default IP of the client.
    // Loopback IP is used by default as this is how smokeTests require it to be
    // and it's probably a good security behaviour to default only on the localhost.
    private InetAddress autoDiscoverDefaultIP() {
      autoDiscoveredDefaultIP =
          Optional.ofNullable(autoDiscoveredDefaultIP).orElseGet(InetAddress::getLoopbackAddress);

      return autoDiscoveredDefaultIP;
    }
  }

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
          "Minimum number of peers required before starting fast sync. Has only effect on PoW networks. (default: ${DEFAULT-VALUE})")
  private final Integer fastSyncMinPeerCount = FAST_SYNC_MIN_PEER_COUNT;

  @Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      defaultValue = "MAINNET",
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: ${DEFAULT-VALUE})")
  private final NetworkName network = null;

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
      names = {"--kzg-trusted-setup"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Path to file containing the KZG trusted setup, mandatory for custom networks that support data blobs, "
              + "optional for overriding named networks default.",
      arity = "1")
  private final Path kzgTrustedSetupFile = null;

  @CommandLine.ArgGroup(validate = false, heading = "@|bold GraphQL Options|@%n")
  GraphQlOptionGroup graphQlOptionGroup = new GraphQlOptionGroup();

  static class GraphQlOptionGroup {
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
    private String graphQLHttpHost;

    @Option(
        names = {"--graphql-http-port"},
        paramLabel = MANDATORY_PORT_FORMAT_HELP,
        description = "Port for GraphQL HTTP to listen on (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final Integer graphQLHttpPort = DEFAULT_GRAPHQL_HTTP_PORT;

    @Option(
        names = {"--graphql-http-cors-origins"},
        description = "Comma separated origin domain URLs for CORS validation (default: none)")
    protected final CorsAllowedOriginsProperty graphQLHttpCorsAllowedOrigins =
        new CorsAllowedOriginsProperty();
  }

  // Engine JSON-PRC Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Engine JSON-RPC Options|@%n")
  EngineRPCOptionGroup engineRPCOptionGroup = new EngineRPCOptionGroup();

  static class EngineRPCOptionGroup {
    @Option(
        names = {"--engine-rpc-enabled"},
        description =
            "enable the engine api, even in the absence of merge-specific configurations.")
    private final Boolean overrideEngineRpcEnabled = false;

    @Option(
        names = {"--engine-rpc-port", "--engine-rpc-http-port"},
        paramLabel = MANDATORY_PORT_FORMAT_HELP,
        description = "Port to provide consensus client APIS on (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final Integer engineRpcPort = DEFAULT_ENGINE_JSON_RPC_PORT;

    @Option(
        names = {"--engine-jwt-secret"},
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description = "Path to file containing shared secret key for JWT signature verification")
    private final Path engineJwtKeyFile = null;

    @Option(
        names = {"--engine-jwt-enabled"},
        description = "deprecated option, engine jwt auth is enabled by default",
        hidden = true)
    @SuppressWarnings({"FieldCanBeFinal", "UnusedVariable"})
    private final Boolean deprecatedIsEngineAuthEnabled = true;

    @Option(
        names = {"--engine-jwt-disabled"},
        description = "Disable authentication for Engine APIs (default: ${DEFAULT-VALUE})")
    private final Boolean isEngineAuthDisabled = false;

    @Option(
        names = {"--engine-host-allowlist"},
        paramLabel = "<hostname>[,<hostname>...]... or * or all",
        description =
            "Comma separated list of hostnames to allow for ENGINE API access (applies to both HTTP and websockets), or * to accept any host (default: ${DEFAULT-VALUE})",
        defaultValue = "localhost,127.0.0.1")
    private final JsonRPCAllowlistHostsProperty engineHostsAllowlist =
        new JsonRPCAllowlistHostsProperty();
  }

  // JSON-RPC HTTP Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC HTTP Options|@%n")
  JsonRPCHttpOptionGroup jsonRPCHttpOptionGroup = new JsonRPCHttpOptionGroup();

  static class JsonRPCHttpOptionGroup {
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
    private String rpcHttpHost;

    @Option(
        names = {"--rpc-http-port"},
        paramLabel = MANDATORY_PORT_FORMAT_HELP,
        description = "Port for JSON-RPC HTTP to listen on (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final Integer rpcHttpPort = DEFAULT_JSON_RPC_PORT;

    @Option(
        names = {"--rpc-http-max-active-connections"},
        description =
            "Maximum number of HTTP connections allowed for JSON-RPC (default: ${DEFAULT-VALUE}). Once this limit is reached, incoming connections will be rejected.",
        arity = "1")
    private final Integer rpcHttpMaxConnections = DEFAULT_HTTP_MAX_CONNECTIONS;

    // A list of origins URLs that are accepted by the JsonRpcHttpServer (CORS)
    @Option(
        names = {"--rpc-http-cors-origins"},
        description = "Comma separated origin domain URLs for CORS validation (default: none)")
    private final CorsAllowedOriginsProperty rpcHttpCorsAllowedOrigins =
        new CorsAllowedOriginsProperty();

    @Option(
        names = {"--rpc-http-api", "--rpc-http-apis"},
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description =
            "Comma separated list of APIs to enable on JSON-RPC HTTP service (default: ${DEFAULT-VALUE})")
    private final List<String> rpcHttpApis = DEFAULT_RPC_APIS;

    @Option(
        names = {"--rpc-http-api-method-no-auth", "--rpc-http-api-methods-no-auth"},
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description =
            "Comma separated list of API methods to exclude from RPC authentication services, RPC HTTP authentication must be enabled")
    private final List<String> rpcHttpApiMethodsNoAuth = new ArrayList<String>();

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
        names = {"--rpc-http-authentication-jwt-algorithm"},
        description =
            "Encryption algorithm used for HTTP JWT public key. Possible values are ${COMPLETION-CANDIDATES}"
                + " (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final JwtAlgorithm rpcHttpAuthenticationAlgorithm = DEFAULT_JWT_ALGORITHM;

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
        names = {"--rpc-http-tls-protocol", "--rpc-http-tls-protocols"},
        description =
            "Comma separated list of TLS protocols to support (default: ${DEFAULT-VALUE})",
        split = ",",
        arity = "1..*")
    private final List<String> rpcHttpTlsProtocols = new ArrayList<>(DEFAULT_TLS_PROTOCOLS);

    @Option(
        names = {"--rpc-http-tls-cipher-suite", "--rpc-http-tls-cipher-suites"},
        description = "Comma separated list of TLS cipher suites to support",
        split = ",",
        arity = "1..*")
    private final List<String> rpcHttpTlsCipherSuites = new ArrayList<>();

    @CommandLine.Option(
        names = {"--rpc-http-max-batch-size"},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Specifies the maximum number of requests in a single RPC batch request via RPC. -1 specifies no limit  (default: ${DEFAULT-VALUE})")
    private final Integer rpcHttpMaxBatchSize = DEFAULT_HTTP_MAX_BATCH_SIZE;

    @CommandLine.Option(
        names = {"--rpc-http-max-request-content-length"},
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description = "Specifies the maximum request content length. (default: ${DEFAULT-VALUE})")
    private final Long rpcHttpMaxRequestContentLength = DEFAULT_MAX_REQUEST_CONTENT_LENGTH;

    @Option(
        names = {"--json-pretty-print-enabled"},
        description = "Enable JSON pretty print format (default: ${DEFAULT-VALUE})")
    private final Boolean prettyJsonEnabled = DEFAULT_PRETTY_JSON_ENABLED;
  }

  // JSON-RPC Websocket Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC Websocket Options|@%n")
  JsonRPCWebsocketOptionGroup jsonRPCWebsocketOptionGroup = new JsonRPCWebsocketOptionGroup();

  static class JsonRPCWebsocketOptionGroup {
    @Option(
        names = {"--rpc-ws-authentication-jwt-algorithm"},
        description =
            "Encryption algorithm used for Websockets JWT public key. Possible values are ${COMPLETION-CANDIDATES}"
                + " (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final JwtAlgorithm rpcWebsocketsAuthenticationAlgorithm = DEFAULT_JWT_ALGORITHM;

    @Option(
        names = {"--rpc-ws-enabled"},
        description = "Set to start the JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
    private final Boolean isRpcWsEnabled = false;

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(
        names = {"--rpc-ws-host"},
        paramLabel = MANDATORY_HOST_FORMAT_HELP,
        description =
            "Host for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
        arity = "1")
    private String rpcWsHost;

    @Option(
        names = {"--rpc-ws-port"},
        paramLabel = MANDATORY_PORT_FORMAT_HELP,
        description =
            "Port for JSON-RPC WebSocket service to listen on (default: ${DEFAULT-VALUE})",
        arity = "1")
    private final Integer rpcWsPort = DEFAULT_WEBSOCKET_PORT;

    @Option(
        names = {"--rpc-ws-max-frame-size"},
        description =
            "Maximum size in bytes for JSON-RPC WebSocket frames (default: ${DEFAULT-VALUE}). If this limit is exceeded, the websocket will be disconnected.",
        arity = "1")
    private final Integer rpcWsMaxFrameSize = DEFAULT_WS_MAX_FRAME_SIZE;

    @Option(
        names = {"--rpc-ws-max-active-connections"},
        description =
            "Maximum number of WebSocket connections allowed for JSON-RPC (default: ${DEFAULT-VALUE}). Once this limit is reached, incoming connections will be rejected.",
        arity = "1")
    private final Integer rpcWsMaxConnections = DEFAULT_WS_MAX_CONNECTIONS;

    @Option(
        names = {"--rpc-ws-api", "--rpc-ws-apis"},
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description =
            "Comma separated list of APIs to enable on JSON-RPC WebSocket service (default: ${DEFAULT-VALUE})")
    private final List<String> rpcWsApis = DEFAULT_RPC_APIS;

    @Option(
        names = {"--rpc-ws-api-methods-no-auth", "--rpc-ws-api-method-no-auth"},
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description =
            "Comma separated list of RPC methods to exclude from RPC authentication services, RPC WebSocket authentication must be enabled")
    private final List<String> rpcWsApiMethodsNoAuth = new ArrayList<String>();

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
  }

  // Privacy Options Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Privacy Options|@%n")
  PrivacyOptionGroup privacyOptionGroup = new PrivacyOptionGroup();

  static class PrivacyOptionGroup {
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
        description =
            "Path to a file containing the fingerprints of the authorized privacy enclave.")
    private final Path privacyTlsKnownEnclaveFile = null;

    @Option(
        names = {"--privacy-enabled"},
        description = "Enable private transactions (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivacyEnabled = false;

    @Option(
        names = {"--privacy-multi-tenancy-enabled"},
        description = "Enable multi-tenant private transactions (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivacyMultiTenancyEnabled = false;

    @Option(
        names = {"--privacy-url"},
        description = "The URL on which the enclave is running")
    private final URI privacyUrl = PrivacyParameters.DEFAULT_ENCLAVE_URL;

    @Option(
        names = {"--privacy-public-key-file"},
        description = "The enclave's public key file")
    private final File privacyPublicKeyFile = null;

    @Option(
        names = {"--privacy-marker-transaction-signing-key-file"},
        description =
            "The name of a file containing the private key used to sign privacy marker transactions. If unset, each will be signed with a random key.")
    private final Path privateMarkerTransactionSigningKeyPath = null;

    @Option(
        names = {"--privacy-enable-database-migration"},
        description = "Enable private database metadata migration (default: ${DEFAULT-VALUE})")
    private final Boolean migratePrivateDatabase = false;

    @Option(
        names = {"--privacy-flexible-groups-enabled"},
        description = "Enable flexible privacy groups (default: ${DEFAULT-VALUE})")
    private final Boolean isFlexiblePrivacyGroupsEnabled = false;

    @Option(
        hidden = true,
        names = {"--privacy-onchain-groups-enabled"},
        description =
            "!!DEPRECATED!! Use `--privacy-flexible-groups-enabled` instead. Enable flexible (onchain) privacy groups (default: ${DEFAULT-VALUE})")
    private final Boolean isOnchainPrivacyGroupsEnabled = false;
  }

  // Metrics Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Metrics Options|@%n")
  MetricsOptionGroup metricsOptionGroup = new MetricsOptionGroup();

  static class MetricsOptionGroup {
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
    private String metricsHost;

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
        description =
            "Host of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
        arity = "1")
    private String metricsPushHost;

    @Option(
        names = {"--metrics-push-port"},
        paramLabel = MANDATORY_PORT_FORMAT_HELP,
        description =
            "Port of the Prometheus Push Gateway for push mode (default: ${DEFAULT-VALUE})",
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
  }

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
      names = {"--pruning-enabled"},
      description =
          "Enable disk-space saving optimization that removes old state that is unlikely to be required (default: ${DEFAULT-VALUE})")
  private final Boolean pruningEnabled = false;

  // Permission Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Permissions Options|@%n")
  PermissionsOptionGroup permissionsOptionGroup = new PermissionsOptionGroup();

  static class PermissionsOptionGroup {
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
        description =
            "Enable node level permissions via smart contract (default: ${DEFAULT-VALUE})")
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
  }

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
      description = "Number of blocks to consider for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Long apiGasPriceBlocks = 100L;

  @CommandLine.Option(
      names = {"--api-gas-price-percentile"},
      description = "Percentile value to measure for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Double apiGasPricePercentile = 50.0;

  @CommandLine.Option(
      names = {"--api-gas-price-max"},
      description = "Maximum gas price for eth_gasPrice (default: ${DEFAULT-VALUE})")
  private final Long apiGasPriceMax = 500_000_000_000L;

  @CommandLine.Option(
      names = {"--api-gas-and-priority-fee-limiting-enabled"},
      hidden = true,
      description =
          "Set to enable gas price and minimum priority fee limit in eth_getGasPrice and eth_feeHistory (default: ${DEFAULT-VALUE})")
  private final Boolean apiGasAndPriorityFeeLimitingEnabled = false;

  @CommandLine.Option(
      names = {"--api-gas-and-priority-fee-lower-bound-coefficient"},
      hidden = true,
      description =
          "Coefficient for setting the lower limit of gas price and minimum priority fee in eth_getGasPrice and eth_feeHistory (default: ${DEFAULT-VALUE})")
  private final Long apiGasAndPriorityFeeLowerBoundCoefficient =
      ApiConfiguration.DEFAULT_LOWER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;

  @CommandLine.Option(
      names = {"--api-gas-and-priority-fee-upper-bound-coefficient"},
      hidden = true,
      description =
          "Coefficient for setting the upper limit of gas price and minimum priority fee in eth_getGasPrice and eth_feeHistory (default: ${DEFAULT-VALUE})")
  private final Long apiGasAndPriorityFeeUpperBoundCoefficient =
      ApiConfiguration.DEFAULT_UPPER_BOUND_GAS_AND_PRIORITY_FEE_COEFFICIENT;

  @CommandLine.Option(
      names = {"--static-nodes-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Specifies the static node file containing the static nodes for this node to connect to")
  private final Path staticNodesFile = null;

  @CommandLine.Option(
      names = {"--rpc-max-logs-range"},
      description =
          "Specifies the maximum number of blocks to retrieve logs from via RPC. Must be >=0. 0 specifies no limit  (default: ${DEFAULT-VALUE})")
  private final Long rpcMaxLogsRange = 5000L;

  @CommandLine.Option(
      names = {"--rpc-gas-cap"},
      description =
          "Specifies the gasLimit cap for transaction simulation RPC methods. Must be >=0. 0 specifies no limit  (default: ${DEFAULT-VALUE})")
  private final Long rpcGasCap = 0L;

  @CommandLine.Option(
      names = {"--cache-last-blocks"},
      description = "Specifies the number of last blocks to cache  (default: ${DEFAULT-VALUE})")
  private final Integer numberOfblocksToCache = 0;

  @Mixin private P2PTLSConfigOptions p2pTLSConfigOptions;

  @Mixin private PkiBlockCreationOptions pkiBlockCreationOptions;

  private EthNetworkConfig ethNetworkConfig;
  private JsonRpcConfiguration jsonRpcConfiguration;
  private JsonRpcConfiguration engineJsonRpcConfiguration;
  private GraphQLConfiguration graphQLConfiguration;
  private WebSocketConfiguration webSocketConfiguration;
  private JsonRpcIpcConfiguration jsonRpcIpcConfiguration;
  private ApiConfiguration apiConfiguration;
  private MetricsConfiguration metricsConfiguration;
  private Optional<PermissioningConfiguration> permissioningConfiguration;
  private Optional<TLSConfiguration> p2pTLSConfiguration;
  private Collection<EnodeURL> staticNodes;
  private BesuController besuController;
  private BesuConfiguration pluginCommonConfiguration;
  private MiningParameters miningParameters;

  private BesuComponent besuComponent;
  private final Supplier<ObservableMetricsSystem> metricsSystem =
      Suppliers.memoize(
          () ->
              besuComponent == null || besuComponent.getObservableMetricsSystem() == null
                  ? MetricsSystemFactory.create(metricsConfiguration())
                  : besuComponent.getObservableMetricsSystem());
  private Vertx vertx;
  private EnodeDnsConfiguration enodeDnsConfiguration;
  private KeyValueStorageProvider keyValueStorageProvider;

  /**
   * Besu command constructor.
   *
   * @param besuComponent BesuComponent which acts as our application context
   * @param rlpBlockImporter RlpBlockImporter supplier
   * @param jsonBlockImporterFactory instance of {@code Function<BesuController, JsonBlockImporter>}
   * @param rlpBlockExporterFactory instance of {@code Function<Blockchain, RlpBlockExporter>}
   * @param runnerBuilder instance of RunnerBuilder
   * @param controllerBuilderFactory instance of BesuController.Builder
   * @param besuPluginContext instance of BesuPluginContextImpl
   * @param environment Environment variables map
   */
  public BesuCommand(
      final BesuComponent besuComponent,
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment) {
    this(
        besuComponent,
        rlpBlockImporter,
        jsonBlockImporterFactory,
        rlpBlockExporterFactory,
        runnerBuilder,
        controllerBuilderFactory,
        besuPluginContext,
        environment,
        new StorageServiceImpl(),
        new SecurityModuleServiceImpl(),
        new PermissioningServiceImpl(),
        new PrivacyPluginServiceImpl(),
        new PkiBlockCreationConfigurationProvider(),
        new RpcEndpointServiceImpl(),
        new TransactionSelectionServiceImpl(),
        new PluginTransactionValidatorServiceImpl());
  }

  /**
   * Overloaded Besu command constructor visible for testing.
   *
   * @param besuComponent BesuComponent which acts as our application context
   * @param rlpBlockImporter RlpBlockImporter supplier
   * @param jsonBlockImporterFactory instance of {@code Function<BesuController, JsonBlockImporter>}
   * @param rlpBlockExporterFactory instance of {@code Function<Blockchain, RlpBlockExporter>}
   * @param runnerBuilder instance of RunnerBuilder
   * @param controllerBuilderFactory instance of BesuController.Builder
   * @param besuPluginContext instance of BesuPluginContextImpl
   * @param environment Environment variables map
   * @param storageService instance of StorageServiceImpl
   * @param securityModuleService instance of SecurityModuleServiceImpl
   * @param permissioningService instance of PermissioningServiceImpl
   * @param privacyPluginService instance of PrivacyPluginServiceImpl
   * @param pkiBlockCreationConfigProvider instance of PkiBlockCreationConfigurationProvider
   * @param rpcEndpointServiceImpl instance of RpcEndpointServiceImpl
   * @param transactionSelectionServiceImpl instance of TransactionSelectionServiceImpl
   * @param transactionValidatorServiceImpl instance of TransactionValidatorServiceImpl
   */
  @VisibleForTesting
  protected BesuCommand(
      final BesuComponent besuComponent,
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilderFactory,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment,
      final StorageServiceImpl storageService,
      final SecurityModuleServiceImpl securityModuleService,
      final PermissioningServiceImpl permissioningService,
      final PrivacyPluginServiceImpl privacyPluginService,
      final PkiBlockCreationConfigurationProvider pkiBlockCreationConfigProvider,
      final RpcEndpointServiceImpl rpcEndpointServiceImpl,
      final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
      final PluginTransactionValidatorServiceImpl transactionValidatorServiceImpl) {
    this.besuComponent = besuComponent;
    this.logger = besuComponent.getBesuCommandLogger();
    this.rlpBlockImporter = rlpBlockImporter;
    this.rlpBlockExporterFactory = rlpBlockExporterFactory;
    this.jsonBlockImporterFactory = jsonBlockImporterFactory;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilderFactory = controllerBuilderFactory;
    this.besuPluginContext = besuPluginContext;
    this.environment = environment;
    this.storageService = storageService;
    this.securityModuleService = securityModuleService;
    this.permissioningService = permissioningService;
    this.privacyPluginService = privacyPluginService;
    pluginCommonConfiguration = new BesuCommandConfigurationService();
    besuPluginContext.addService(BesuConfiguration.class, pluginCommonConfiguration);
    this.pkiBlockCreationConfigProvider = pkiBlockCreationConfigProvider;
    this.rpcEndpointServiceImpl = rpcEndpointServiceImpl;
    this.transactionSelectionServiceImpl = transactionSelectionServiceImpl;
    this.transactionValidatorServiceImpl = transactionValidatorServiceImpl;
  }

  /**
   * Parse Besu command line arguments. Visible for testing.
   *
   * @param resultHandler execution strategy. See PicoCLI. Typical argument is RunLast.
   * @param parameterExceptionHandler Exception handler for handling parameters
   * @param executionExceptionHandler Exception handler for business logic
   * @param in Standard input stream
   * @param args arguments to Besu command
   * @return success or failure exit code.
   */
  public int parse(
      final IExecutionStrategy resultHandler,
      final BesuParameterExceptionHandler parameterExceptionHandler,
      final BesuExecutionExceptionHandler executionExceptionHandler,
      final InputStream in,
      final String... args) {

    toCommandLine();

    handleStableOptions();
    addSubCommands(in);
    registerConverters();
    handleUnstableOptions();
    preparePlugins();

    final int exitCode =
        parse(resultHandler, executionExceptionHandler, parameterExceptionHandler, args);

    return exitCode;
  }

  /** Used by Dagger to parse all options into a commandline instance. */
  public void toCommandLine() {
    commandLine =
        new CommandLine(this, new BesuCommandCustomFactory(besuPluginContext))
            .setCaseInsensitiveEnumValuesAllowed(true);
  }

  @Override
  public void run() {
    if (network != null && network.isDeprecated()) {
      logger.warn(NetworkDeprecationMessage.generate(network));
    }

    try {
      configureLogging(true);

      if (genesisFile != null) {
        genesisConfigOptions = readGenesisConfigOptions();
      }

      // set merge config on the basis of genesis config
      setMergeConfigOptions();

      setIgnorableStorageSegments();

      instantiateSignatureAlgorithmFactory();

      logger.info("Starting Besu");

      // Need to create vertx after cmdline has been parsed, such that metricsSystem is configurable
      vertx = createVertx(createVertxOptions(metricsSystem.get()));

      validateOptions();
      configure();
      configureNativeLibs();
      besuController = initController();

      besuPluginContext.beforeExternalServices();

      final var runner = buildRunner();
      runner.startExternalServices();

      startPlugins();
      validatePluginOptions();
      setReleaseMetrics();
      preSynchronization();

      runner.startEthereumMainLoop();
      runner.awaitStop();

    } catch (final Exception e) {
      logger.error("Failed to start Besu", e);
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  @VisibleForTesting
  void setBesuConfiguration(final BesuConfiguration pluginCommonConfiguration) {
    this.pluginCommonConfiguration = pluginCommonConfiguration;
  }

  private void addSubCommands(final InputStream in) {
    commandLine.addSubcommand(
        BlocksSubCommand.COMMAND_NAME,
        new BlocksSubCommand(
            rlpBlockImporter,
            jsonBlockImporterFactory,
            rlpBlockExporterFactory,
            commandLine.getOut()));
    commandLine.addSubcommand(
        TxParseSubCommand.COMMAND_NAME, new TxParseSubCommand(commandLine.getOut()));
    commandLine.addSubcommand(
        PublicKeySubCommand.COMMAND_NAME, new PublicKeySubCommand(commandLine.getOut()));
    commandLine.addSubcommand(
        PasswordSubCommand.COMMAND_NAME, new PasswordSubCommand(commandLine.getOut()));
    commandLine.addSubcommand(RetestethSubCommand.COMMAND_NAME, new RetestethSubCommand());
    commandLine.addSubcommand(
        RLPSubCommand.COMMAND_NAME, new RLPSubCommand(commandLine.getOut(), in));
    commandLine.addSubcommand(
        OperatorSubCommand.COMMAND_NAME, new OperatorSubCommand(commandLine.getOut()));
    commandLine.addSubcommand(
        ValidateConfigSubCommand.COMMAND_NAME,
        new ValidateConfigSubCommand(commandLine, commandLine.getOut()));
    commandLine.addSubcommand(
        StorageSubCommand.COMMAND_NAME, new StorageSubCommand(commandLine.getOut()));
    final String generateCompletionSubcommandName = "generate-completion";
    commandLine.addSubcommand(
        generateCompletionSubcommandName, AutoComplete.GenerateCompletion.class);
    final CommandLine generateCompletionSubcommand =
        commandLine.getSubcommands().get(generateCompletionSubcommandName);
    generateCompletionSubcommand.getCommandSpec().usageMessage().hidden(true);
  }

  private void registerConverters() {
    commandLine.registerConverter(Address.class, Address::fromHexStringStrict);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
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

  private void handleStableOptions() {
    commandLine.addMixin("Ethstats", ethstatsOptions);
    commandLine.addMixin("Private key file", nodePrivateKeyFileOption);
    commandLine.addMixin("Logging level", loggingLevelOption);
    commandLine.addMixin("Data Storage Options", dataStorageOptions);
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
            .put("Privacy Plugin Configuration", unstablePrivacyPluginOptions)
            .put("Synchronizer", unstableSynchronizerOptions)
            .put("Native Library", unstableNativeLibraryOptions)
            .put("EVM Options", unstableEvmOptions)
            .put("IPC Options", unstableIpcOptions)
            .put("Chain Data Pruning Options", unstableChainPruningOptions)
            .build();

    UnstableOptionsSubCommand.createUnstableOptions(commandLine, unstableOptions);
  }

  private void preparePlugins() {
    besuPluginContext.addService(PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine));
    besuPluginContext.addService(SecurityModuleService.class, securityModuleService);
    besuPluginContext.addService(StorageService.class, storageService);
    besuPluginContext.addService(MetricCategoryRegistry.class, metricCategoryRegistry);
    besuPluginContext.addService(PermissioningService.class, permissioningService);
    besuPluginContext.addService(PrivacyPluginService.class, privacyPluginService);
    besuPluginContext.addService(RpcEndpointService.class, rpcEndpointServiceImpl);
    besuPluginContext.addService(
        TransactionSelectionService.class, transactionSelectionServiceImpl);
    besuPluginContext.addService(
        PluginTransactionValidatorService.class, transactionValidatorServiceImpl);

    // register built-in plugins
    rocksDBPlugin = new RocksDBPlugin();
    rocksDBPlugin.register(besuPluginContext);
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
    return new KeyPairSecurityModule(loadKeyPair(nodePrivateKeyFileOption.getNodePrivateKeyFile()));
  }

  // loadKeyPair() is public because it is accessed by subcommands

  /**
   * Load key pair from private key. Visible to be accessed by subcommands.
   *
   * @param nodePrivateKeyFile File containing private key
   * @return KeyPair loaded from private key file
   */
  public KeyPair loadKeyPair(final File nodePrivateKeyFile) {
    return KeyPairUtil.loadKeyPair(resolveNodePrivateKeyFile(nodePrivateKeyFile));
  }

  private int parse(
      final CommandLine.IExecutionStrategy resultHandler,
      final BesuExecutionExceptionHandler besuExecutionExceptionHandler,
      final BesuParameterExceptionHandler besuParameterExceptionHandler,
      final String... args) {
    // Create a handler that will search for a config file option and use it for
    // default values
    // and eventually it will run regular parsing of the remaining options.

    final ConfigOptionSearchAndRunHandler configParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler, besuParameterExceptionHandler, environment);

    return commandLine
        .setExecutionStrategy(configParsingHandler)
        .setParameterExceptionHandler(besuParameterExceptionHandler)
        .setExecutionExceptionHandler(besuExecutionExceptionHandler)
        .execute(args);
  }

  private void preSynchronization() {
    preSynchronizationTaskRunner.runTasks(besuController);
  }

  private Runner buildRunner() {
    return synchronize(
        besuController,
        p2PDiscoveryOptionGroup.p2pEnabled,
        p2pTLSConfiguration,
        p2PDiscoveryOptionGroup.peerDiscoveryEnabled,
        ethNetworkConfig,
        p2PDiscoveryOptionGroup.p2pHost,
        p2PDiscoveryOptionGroup.p2pInterface,
        p2PDiscoveryOptionGroup.p2pPort,
        graphQLConfiguration,
        jsonRpcConfiguration,
        engineJsonRpcConfiguration,
        webSocketConfiguration,
        jsonRpcIpcConfiguration,
        apiConfiguration,
        metricsConfiguration,
        permissioningConfiguration,
        staticNodes,
        pidPath);
  }

  private void startPlugins() {
    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState()));
    besuPluginContext.addService(MetricsSystem.class, getMetricsSystem());

    besuPluginContext.addService(
        BlockchainService.class,
        new BlockchainServiceImpl(besuController.getProtocolContext().getBlockchain()));

    besuPluginContext.addService(
        TraceService.class,
        new TraceServiceImpl(
            new BlockchainQueries(
                besuController.getProtocolContext().getBlockchain(),
                besuController.getProtocolContext().getWorldStateArchive()),
            besuController.getProtocolSchedule()));

    besuController.getAdditionalPluginServices().appendPluginServices(besuPluginContext);
    besuPluginContext.startPlugins();
  }

  private void validatePluginOptions() {
    // plugins do not 'wire up' until start has been called
    // consequently you can only do some configuration checks
    // after start has been called on plugins

    if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyEnabled)) {

      if (privacyOptionGroup.privateMarkerTransactionSigningKeyPath != null
          && privacyPluginService != null
          && privacyPluginService.getPrivateMarkerTransactionFactory() != null) {
        throw new ParameterException(
            commandLine,
            "--privacy-marker-transaction-signing-key-file can not be used in conjunction with a plugin that specifies a PrivateMarkerTransactionFactory");
      }

      if (Wei.ZERO.compareTo(getMiningParameters().getMinTransactionGasPrice()) < 0
          && (privacyOptionGroup.privateMarkerTransactionSigningKeyPath == null
              && (privacyPluginService == null
                  || privacyPluginService.getPrivateMarkerTransactionFactory() == null))) {
        // if gas is required, cannot use random keys to sign private tx
        // ie --privacy-marker-transaction-signing-key-file must be set
        throw new ParameterException(
            commandLine,
            "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified and must be a funded account. Private transactions cannot be signed by random (non-funded) accounts in paid gas networks");
      }

      if (unstablePrivacyPluginOptions.isPrivacyPluginEnabled()
          && privacyPluginService != null
          && privacyPluginService.getPayloadProvider() == null) {
        throw new ParameterException(
            commandLine,
            "No Payload Provider has been provided. You must register one when enabling privacy plugin!");
      }

      if (unstablePrivacyPluginOptions.isPrivacyPluginEnabled()
          && (privacyOptionGroup.isFlexiblePrivacyGroupsEnabled
              || privacyOptionGroup.isOnchainPrivacyGroupsEnabled)) {
        throw new ParameterException(
            commandLine, "Privacy Plugin can not be used with flexible privacy groups");
      }
    }
  }

  private void setReleaseMetrics() {
    metricsSystem
        .get()
        .createLabelledGauge(
            StandardMetricCategory.PROCESS, "release", "Release information", "version")
        .labels(() -> 1, BesuInfo.version());
  }

  /**
   * Configure logging framework for Besu
   *
   * @param announce sets to true to print the logging level on standard output
   */
  public void configureLogging(final boolean announce) {
    // To change the configuration if color was enabled/disabled
    LogConfigurator.reconfigure();
    // set log level per CLI flags
    final String logLevel = loggingLevelOption.getLogLevel();
    if (logLevel != null) {
      if (announce) {
        System.out.println("Setting logging level to " + logLevel);
      }
      LogConfigurator.setLevel("", logLevel);
    }
  }

  /**
   * Logging in Color enabled or not.
   *
   * @return Optional true or false representing logging color is enabled. Empty if not set.
   */
  public static Optional<Boolean> getColorEnabled() {
    return Optional.ofNullable(colorEnabled);
  }

  private void configureNativeLibs() {
    if (unstableNativeLibraryOptions.getNativeAltbn128()
        && AbstractAltBnPrecompiledContract.isNative()) {
      logger.info("Using the native implementation of alt bn128");
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
      logger.info("Using the Java implementation of alt bn128");
    }

    if (unstableNativeLibraryOptions.getNativeModExp()
        && BigIntegerModularExponentiationPrecompiledContract.maybeEnableNative()) {
      logger.info("Using the native implementation of modexp");
    } else {
      BigIntegerModularExponentiationPrecompiledContract.disableNative();
      logger.info("Using the Java implementation of modexp");
    }

    if (unstableNativeLibraryOptions.getNativeSecp()
        && SignatureAlgorithmFactory.getInstance().isNative()) {
      logger.info("Using the native implementation of the signature algorithm");
    } else {
      SignatureAlgorithmFactory.getInstance().disableNative();
      logger.info("Using the Java implementation of the signature algorithm");
    }

    if (unstableNativeLibraryOptions.getNativeBlake2bf()
        && Blake2bfMessageDigest.Blake2bfDigest.isNative()) {
      logger.info("Using the native implementation of the blake2bf algorithm");
    } else {
      Blake2bfMessageDigest.Blake2bfDigest.disableNative();
      logger.info("Using the Java implementation of the blake2bf algorithm");
    }

    if (getActualGenesisConfigOptions().getCancunTime().isPresent()) {
      if (kzgTrustedSetupFile != null) {
        KZGPointEvalPrecompiledContract.init(kzgTrustedSetupFile);
      } else {
        KZGPointEvalPrecompiledContract.init(network.name());
      }
    } else if (kzgTrustedSetupFile != null) {
      throw new ParameterException(
          this.commandLine,
          "--kzg-trusted-setup can only be specified on networks with data blobs enabled");
    }
  }

  private void validateOptions() {
    validateRequiredOptions();
    issueOptionWarnings();
    validateP2PInterface(p2PDiscoveryOptionGroup.p2pInterface);
    validateMiningParams();
    validateNatParams();
    validateNetStatsParams();
    validateDnsOptionsParams();
    ensureValidPeerBoundParams();
    validateRpcOptionsParams();
    validateChainDataPruningParams();
    validatePostMergeCheckpointBlockRequirements();
    validateTransactionPoolOptions();
    validateDataStorageOptions();
    p2pTLSConfigOptions.checkP2PTLSOptionsDependencies(logger, commandLine);
    pkiBlockCreationOptions.checkPkiBlockCreationOptionsDependencies(logger, commandLine);
  }

  private void validateTransactionPoolOptions() {
    transactionPoolOptions.validate(commandLine, getActualGenesisConfigOptions());
  }

  private void validateDataStorageOptions() {
    dataStorageOptions.validate(commandLine);
  }

  private void validateRequiredOptions() {
    commandLine
        .getCommandSpec()
        .options()
        .forEach(
            option -> {
              if (option.required() && option.stringValues().isEmpty()) {
                throw new ParameterException(
                    this.commandLine, "Missing required option: " + option.longestName());
              }
            });
  }

  private void validateMiningParams() {
    miningOptions.validate(commandLine, getActualGenesisConfigOptions(), isMergeEnabled(), logger);
  }

  /**
   * Validates P2P interface IP address/host name. Visible for testing.
   *
   * @param p2pInterface IP Address/host name
   */
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
    if (Strings.isNullOrEmpty(ethstatsOptions.getEthstatsUrl())
        && !ethstatsOptions.getEthstatsContact().isEmpty()) {
      throw new ParameterException(
          this.commandLine,
          "The `--ethstats-contact` requires ethstats server URL to be provided. Either remove --ethstats-contact"
              + " or provide a URL (via --ethstats=nodename:secret@host:port)");
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

  private void checkApiOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--api-gas-and-priority-fee-limiting-enabled",
        !apiGasAndPriorityFeeLimitingEnabled,
        asList(
            "--api-gas-and-priority-fee-upper-bound-coefficient",
            "--api-gas-and-priority-fee-lower-bound-coefficient"));
  }

  private void ensureValidPeerBoundParams() {
    maxPeers = p2PDiscoveryOptionGroup.maxPeers;
    peersLowerBound = unstableNetworkingOptions.toDomainObject().getPeerLowerBound();
    if (peersLowerBound > maxPeers) {
      logger.warn(
          "`--Xp2p-peer-lower-bound` "
              + peersLowerBound
              + " must not exceed --max-peers "
              + maxPeers);
      logger.warn("setting --Xp2p-peer-lower-bound=" + maxPeers);
      peersLowerBound = maxPeers;
    }
    final Boolean isLimitRemoteWireConnectionsEnabled =
        p2PDiscoveryOptionGroup.isLimitRemoteWireConnectionsEnabled;
    if (isLimitRemoteWireConnectionsEnabled) {
      final float fraction =
          Fraction.fromPercentage(p2PDiscoveryOptionGroup.maxRemoteConnectionsPercentage)
              .getValue();
      checkState(
          fraction >= 0.0 && fraction <= 1.0,
          "Fraction of remote connections allowed must be between 0.0 and 1.0 (inclusive).");
      maxRemoteInitiatedPeers = (int) Math.floor(fraction * maxPeers);
    } else {
      maxRemoteInitiatedPeers = maxPeers;
    }
  }

  private void validateRpcOptionsParams() {
    final Predicate<String> configuredApis =
        apiName ->
            Arrays.stream(RpcApis.values())
                    .anyMatch(builtInApi -> apiName.equals(builtInApi.name()))
                || rpcEndpointServiceImpl.hasNamespace(apiName);

    if (!jsonRPCHttpOptionGroup.rpcHttpApis.stream().allMatch(configuredApis)) {
      final List<String> invalidHttpApis =
          new ArrayList<String>(jsonRPCHttpOptionGroup.rpcHttpApis);
      invalidHttpApis.removeAll(VALID_APIS);
      throw new ParameterException(
          this.commandLine,
          "Invalid value for option '--rpc-http-api': invalid entries found "
              + invalidHttpApis.toString());
    }

    if (!jsonRPCWebsocketOptionGroup.rpcWsApis.stream().allMatch(configuredApis)) {
      final List<String> invalidWsApis =
          new ArrayList<String>(jsonRPCWebsocketOptionGroup.rpcWsApis);
      invalidWsApis.removeAll(VALID_APIS);
      throw new ParameterException(
          this.commandLine,
          "Invalid value for option '--rpc-ws-api': invalid entries found " + invalidWsApis);
    }

    final boolean validHttpApiMethods =
        jsonRPCHttpOptionGroup.rpcHttpApiMethodsNoAuth.stream()
            .allMatch(RpcMethod::rpcMethodExists);

    if (!validHttpApiMethods) {
      throw new ParameterException(
          this.commandLine,
          "Invalid value for option '--rpc-http-api-methods-no-auth', options must be valid RPC methods");
    }

    final boolean validWsApiMethods =
        jsonRPCWebsocketOptionGroup.rpcWsApiMethodsNoAuth.stream()
            .allMatch(RpcMethod::rpcMethodExists);

    if (!validWsApiMethods) {
      throw new ParameterException(
          this.commandLine,
          "Invalid value for option '--rpc-ws-api-methods-no-auth', options must be valid RPC methods");
    }
  }

  private void validateChainDataPruningParams() {
    if (unstableChainPruningOptions.getChainDataPruningEnabled()
        && unstableChainPruningOptions.getChainDataPruningBlocksRetained()
            < ChainPruningOptions.DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED) {
      throw new ParameterException(
          this.commandLine,
          "--Xchain-pruning-blocks-retained must be >= "
              + ChainPruningOptions.DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED);
    }
  }

  private GenesisConfigOptions readGenesisConfigOptions() {

    try {
      final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(genesisConfig());
      genesisConfigOptions = genesisConfigFile.getConfigOptions(genesisConfigOverrides);
    } catch (final Exception e) {
      throw new ParameterException(
          this.commandLine, "Unable to load genesis file. " + e.getCause());
    }
    return genesisConfigOptions;
  }

  private void issueOptionWarnings() {

    // Check that P2P options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2PDiscoveryOptionGroup.p2pEnabled,
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

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "--fast-sync-min-peers can't be used with FULL sync-mode",
        !SyncMode.isFullSync(getDefaultSyncModeIfNotSet()),
        singletonList("--fast-sync-min-peers"));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "--Xcheckpoint-post-merge-enabled can only be used with X_CHECKPOINT sync-mode",
        SyncMode.X_CHECKPOINT.equals(getDefaultSyncModeIfNotSet()),
        singletonList("--Xcheckpoint-post-merge-enabled"));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "--Xsnapsync-synchronizer-flat option can only be used when -Xsnapsync-synchronizer-flat-db-healing-enabled is true",
        unstableSynchronizerOptions.isSnapsyncFlatDbHealingEnabled(),
        asList(
            "--Xsnapsync-synchronizer-flat-account-healed-count-per-request",
            "--Xsnapsync-synchronizer-flat-slot-healed-count-per-request"));

    if (!securityModuleName.equals(DEFAULT_SECURITY_MODULE)
        && nodePrivateKeyFileOption.getNodePrivateKeyFile() != null) {
      logger.warn(
          DEPENDENCY_WARNING_MSG,
          "--node-private-key-file",
          "--security-module=" + DEFAULT_SECURITY_MODULE);
    }

    if (Boolean.TRUE.equals(privacyOptionGroup.isOnchainPrivacyGroupsEnabled)) {
      logger.warn(
          DEPRECATION_WARNING_MSG,
          "--privacy-onchain-groups-enabled",
          "--privacy-flexible-groups-enabled");
    }

    if (isPruningEnabled()) {
      logger.warn(
          "Forest pruning is deprecated and will be removed soon. To save disk space consider switching to Bonsai data storage format.");
    }
  }

  private void configure() throws Exception {
    checkPortClash();
    checkIfRequiredPortsAreAvailable();
    syncMode = getDefaultSyncModeIfNotSet();

    ethNetworkConfig = updateNetworkConfig(network);

    jsonRpcConfiguration =
        jsonRpcConfiguration(
            jsonRPCHttpOptionGroup.rpcHttpPort, jsonRPCHttpOptionGroup.rpcHttpApis, hostsAllowlist);
    if (isEngineApiEnabled()) {
      engineJsonRpcConfiguration =
          createEngineJsonRpcConfiguration(
              engineRPCOptionGroup.engineRpcPort, engineRPCOptionGroup.engineHostsAllowlist);
    }
    p2pTLSConfiguration = p2pTLSConfigOptions.p2pTLSConfiguration(commandLine);
    graphQLConfiguration = graphQLConfiguration();
    webSocketConfiguration =
        webSocketConfiguration(
            jsonRPCWebsocketOptionGroup.rpcWsPort,
            jsonRPCWebsocketOptionGroup.rpcWsApis,
            hostsAllowlist);
    jsonRpcIpcConfiguration =
        jsonRpcIpcConfiguration(
            unstableIpcOptions.isEnabled(),
            unstableIpcOptions.getIpcPath(),
            unstableIpcOptions.getRpcIpcApis());
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

    final List<EnodeURL> enodeURIs = ethNetworkConfig.getBootNodes();
    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(p -> ensureAllNodesAreInAllowlist(enodeURIs, p));

    permissioningConfiguration
        .flatMap(PermissioningConfiguration::getLocalConfig)
        .ifPresent(p -> ensureAllNodesAreInAllowlist(staticNodes, p));
    metricsConfiguration = metricsConfiguration();

    instantiateSignatureAlgorithmFactory();

    logger.info(generateConfigurationOverview());
    logger.info("Security Module: {}", securityModuleName);
  }

  private JsonRpcIpcConfiguration jsonRpcIpcConfiguration(
      final Boolean enabled, final Path ipcPath, final List<String> rpcIpcApis) {
    final Path actualPath;
    if (ipcPath == null) {
      actualPath = IpcOptions.getDefaultPath(dataDir());
    } else {
      actualPath = ipcPath;
    }
    return new JsonRpcIpcConfiguration(
        vertx.isNativeTransportEnabled() && enabled, actualPath, rpcIpcApis);
  }

  private void ensureAllNodesAreInAllowlist(
      final Collection<EnodeURL> enodeAddresses,
      final LocalPermissioningConfiguration permissioningConfiguration) {
    try {
      PermissioningConfigurationValidator.areAllNodesInAllowlist(
          enodeAddresses, permissioningConfiguration);
    } catch (final Exception e) {
      throw new ParameterException(this.commandLine, e.getMessage());
    }
  }

  private BesuController initController() {
    return buildController();
  }

  /**
   * Builds BesuController
   *
   * @return instance of BesuController
   */
  public BesuController buildController() {
    try {
      return this.besuComponent == null
          ? getControllerBuilder().build()
          : getControllerBuilder().besuComponent(this.besuComponent).build();
    } catch (final Exception e) {
      throw new ExecutionException(this.commandLine, e.getMessage(), e);
    }
  }

  /**
   * Builds BesuControllerBuilder which can be used to build BesuController
   *
   * @return instance of BesuControllerBuilder
   */
  public BesuControllerBuilder getControllerBuilder() {
    final KeyValueStorageProvider storageProvider = keyValueStorageProvider(keyValueStorageName);
    return controllerBuilderFactory
        .fromEthNetworkConfig(
            updateNetworkConfig(network), genesisConfigOverrides, getDefaultSyncModeIfNotSet())
        .synchronizerConfiguration(buildSyncConfig())
        .ethProtocolConfiguration(unstableEthProtocolOptions.toDomainObject())
        .networkConfiguration(unstableNetworkingOptions.toDomainObject())
        .transactionSelectorFactory(getTransactionSelectorFactory())
        .pluginTransactionValidatorFactory(getPluginTransactionValidatorFactory())
        .dataDirectory(dataDir())
        .miningParameters(getMiningParameters())
        .transactionPoolConfiguration(buildTransactionPoolConfiguration())
        .nodeKey(new NodeKey(securityModule()))
        .metricsSystem(metricsSystem.get())
        .messagePermissioningProviders(permissioningService.getMessagePermissioningProviders())
        .privacyParameters(privacyParameters())
        .pkiBlockCreationConfiguration(maybePkiBlockCreationConfiguration())
        .clock(Clock.systemUTC())
        .isRevertReasonEnabled(isRevertReasonEnabled)
        .storageProvider(storageProvider)
        .isPruningEnabled(isPruningEnabled())
        .pruningConfiguration(
            new PrunerConfiguration(pruningBlockConfirmations, pruningBlocksRetained))
        .genesisConfigOverrides(genesisConfigOverrides)
        .gasLimitCalculator(
            getMiningParameters().getTargetGasLimit().isPresent()
                ? new FrontierTargetingGasLimitCalculator()
                : GasLimitCalculator.constant())
        .requiredBlocks(requiredBlocks)
        .reorgLoggingThreshold(reorgLoggingThreshold)
        .evmConfiguration(unstableEvmOptions.toDomainObject())
        .dataStorageConfiguration(dataStorageOptions.toDomainObject())
        .maxPeers(p2PDiscoveryOptionGroup.maxPeers)
        .lowerBoundPeers(peersLowerBound)
        .maxRemotelyInitiatedPeers(maxRemoteInitiatedPeers)
        .randomPeerPriority(p2PDiscoveryOptionGroup.randomPeerPriority)
        .chainPruningConfiguration(unstableChainPruningOptions.toDomainObject())
        .cacheLastBlocks(numberOfblocksToCache);
  }

  @NotNull
  private Optional<PluginTransactionSelectorFactory> getTransactionSelectorFactory() {
    final Optional<TransactionSelectionService> txSelectionService =
        besuPluginContext.getService(TransactionSelectionService.class);
    return txSelectionService.isPresent() ? txSelectionService.get().get() : Optional.empty();
  }

  private PluginTransactionValidatorFactory getPluginTransactionValidatorFactory() {
    final Optional<PluginTransactionValidatorService> txSValidatorService =
        besuPluginContext.getService(PluginTransactionValidatorService.class);
    return txSValidatorService.map(PluginTransactionValidatorService::get).orElse(null);
  }

  private GraphQLConfiguration graphQLConfiguration() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--graphql-http-enabled",
        !graphQlOptionGroup.isGraphQLHttpEnabled,
        asList("--graphql-http-cors-origins", "--graphql-http-host", "--graphql-http-port"));
    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();
    graphQLConfiguration.setEnabled(graphQlOptionGroup.isGraphQLHttpEnabled);
    graphQLConfiguration.setHost(
        Strings.isNullOrEmpty(graphQlOptionGroup.graphQLHttpHost)
            ? p2PDiscoveryOptionGroup.autoDiscoverDefaultIP().getHostAddress()
            : graphQlOptionGroup.graphQLHttpHost);
    graphQLConfiguration.setPort(graphQlOptionGroup.graphQLHttpPort);
    graphQLConfiguration.setHostsAllowlist(hostsAllowlist);
    graphQLConfiguration.setCorsAllowedDomains(graphQlOptionGroup.graphQLHttpCorsAllowedOrigins);
    graphQLConfiguration.setHttpTimeoutSec(unstableRPCOptions.getHttpTimeoutSec());

    return graphQLConfiguration;
  }

  private JsonRpcConfiguration createEngineJsonRpcConfiguration(
      final Integer listenPort, final List<String> allowCallsFrom) {
    final JsonRpcConfiguration engineConfig =
        jsonRpcConfiguration(listenPort, Arrays.asList("ENGINE", "ETH"), allowCallsFrom);
    engineConfig.setEnabled(isEngineApiEnabled());
    if (!engineRPCOptionGroup.isEngineAuthDisabled) {
      engineConfig.setAuthenticationEnabled(true);
      engineConfig.setAuthenticationAlgorithm(JwtAlgorithm.HS256);
      if (Objects.nonNull(engineRPCOptionGroup.engineJwtKeyFile)
          && java.nio.file.Files.exists(engineRPCOptionGroup.engineJwtKeyFile)) {
        engineConfig.setAuthenticationPublicKeyFile(engineRPCOptionGroup.engineJwtKeyFile.toFile());
      } else {
        logger.warn(
            "Engine API authentication enabled without key file. Expect ephemeral jwt.hex file in datadir");
      }
    }
    return engineConfig;
  }

  private JsonRpcConfiguration jsonRpcConfiguration(
      final Integer listenPort, final List<String> apiGroups, final List<String> allowCallsFrom) {
    checkRpcTlsClientAuthOptionsDependencies();
    checkRpcTlsOptionsDependencies();
    checkRpcHttpOptionsDependencies();

    if (jsonRPCHttpOptionGroup.isRpcHttpAuthenticationEnabled) {
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--rpc-http-authentication-public-key-file",
          jsonRPCHttpOptionGroup.rpcHttpAuthenticationPublicKeyFile == null,
          asList("--rpc-http-authentication-jwt-algorithm"));
    }

    if (jsonRPCHttpOptionGroup.isRpcHttpAuthenticationEnabled
        && rpcHttpAuthenticationCredentialsFile() == null
        && jsonRPCHttpOptionGroup.rpcHttpAuthenticationPublicKeyFile == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file or authentication public key file");
    }

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(jsonRPCHttpOptionGroup.isRpcHttpEnabled);
    jsonRpcConfiguration.setHost(
        Strings.isNullOrEmpty(jsonRPCHttpOptionGroup.rpcHttpHost)
            ? p2PDiscoveryOptionGroup.autoDiscoverDefaultIP().getHostAddress()
            : jsonRPCHttpOptionGroup.rpcHttpHost);
    jsonRpcConfiguration.setPort(listenPort);
    jsonRpcConfiguration.setMaxActiveConnections(jsonRPCHttpOptionGroup.rpcHttpMaxConnections);
    jsonRpcConfiguration.setCorsAllowedDomains(jsonRPCHttpOptionGroup.rpcHttpCorsAllowedOrigins);
    jsonRpcConfiguration.setRpcApis(apiGroups.stream().distinct().collect(Collectors.toList()));
    jsonRpcConfiguration.setNoAuthRpcApis(
        jsonRPCHttpOptionGroup.rpcHttpApiMethodsNoAuth.stream()
            .distinct()
            .collect(Collectors.toList()));
    jsonRpcConfiguration.setHostsAllowlist(allowCallsFrom);
    jsonRpcConfiguration.setAuthenticationEnabled(
        jsonRPCHttpOptionGroup.isRpcHttpAuthenticationEnabled);
    jsonRpcConfiguration.setAuthenticationCredentialsFile(rpcHttpAuthenticationCredentialsFile());
    jsonRpcConfiguration.setAuthenticationPublicKeyFile(
        jsonRPCHttpOptionGroup.rpcHttpAuthenticationPublicKeyFile);
    jsonRpcConfiguration.setAuthenticationAlgorithm(
        jsonRPCHttpOptionGroup.rpcHttpAuthenticationAlgorithm);
    jsonRpcConfiguration.setTlsConfiguration(rpcHttpTlsConfiguration());
    jsonRpcConfiguration.setHttpTimeoutSec(unstableRPCOptions.getHttpTimeoutSec());
    jsonRpcConfiguration.setMaxBatchSize(jsonRPCHttpOptionGroup.rpcHttpMaxBatchSize);
    jsonRpcConfiguration.setMaxRequestContentLength(
        jsonRPCHttpOptionGroup.rpcHttpMaxRequestContentLength);
    jsonRpcConfiguration.setPrettyJsonEnabled(jsonRPCHttpOptionGroup.prettyJsonEnabled);
    return jsonRpcConfiguration;
  }

  private void checkRpcHttpOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-enabled",
        !jsonRPCHttpOptionGroup.isRpcHttpEnabled,
        asList(
            "--rpc-http-api",
            "--rpc-http-apis",
            "--rpc-http-api-method-no-auth",
            "--rpc-http-api-methods-no-auth",
            "--rpc-http-cors-origins",
            "--rpc-http-host",
            "--rpc-http-port",
            "--rpc-http-max-active-connections",
            "--rpc-http-authentication-enabled",
            "--rpc-http-authentication-credentials-file",
            "--rpc-http-authentication-public-key-file",
            "--rpc-http-tls-enabled",
            "--rpc-http-tls-keystore-file",
            "--rpc-http-tls-keystore-password-file",
            "--rpc-http-tls-client-auth-enabled",
            "--rpc-http-tls-known-clients-file",
            "--rpc-http-tls-ca-clients-enabled",
            "--rpc-http-authentication-jwt-algorithm",
            "--rpc-http-tls-protocols",
            "--rpc-http-tls-cipher-suite",
            "--rpc-http-tls-cipher-suites"));
  }

  private void checkRpcTlsOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-tls-enabled",
        !jsonRPCHttpOptionGroup.isRpcHttpTlsEnabled,
        asList(
            "--rpc-http-tls-keystore-file",
            "--rpc-http-tls-keystore-password-file",
            "--rpc-http-tls-client-auth-enabled",
            "--rpc-http-tls-known-clients-file",
            "--rpc-http-tls-ca-clients-enabled",
            "--rpc-http-tls-protocols",
            "--rpc-http-tls-cipher-suite",
            "--rpc-http-tls-cipher-suites"));
  }

  private void checkRpcTlsClientAuthOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-http-tls-client-auth-enabled",
        !jsonRPCHttpOptionGroup.isRpcHttpTlsClientAuthEnabled,
        asList("--rpc-http-tls-known-clients-file", "--rpc-http-tls-ca-clients-enabled"));
  }

  private void checkPrivacyTlsOptionsDependencies() {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-tls-enabled",
        !privacyOptionGroup.isPrivacyTlsEnabled,
        asList(
            "--privacy-tls-keystore-file",
            "--privacy-tls-keystore-password-file",
            "--privacy-tls-known-enclave-file"));
  }

  private Optional<TlsConfiguration> rpcHttpTlsConfiguration() {
    if (!isRpcTlsConfigurationRequired()) {
      return Optional.empty();
    }

    if (jsonRPCHttpOptionGroup.rpcHttpTlsKeyStoreFile == null) {
      throw new ParameterException(
          commandLine, "Keystore file is required when TLS is enabled for JSON-RPC HTTP endpoint");
    }

    if (jsonRPCHttpOptionGroup.rpcHttpTlsKeyStorePasswordFile == null) {
      throw new ParameterException(
          commandLine,
          "File containing password to unlock keystore is required when TLS is enabled for JSON-RPC HTTP endpoint");
    }

    if (jsonRPCHttpOptionGroup.isRpcHttpTlsClientAuthEnabled
        && !jsonRPCHttpOptionGroup.isRpcHttpTlsCAClientsEnabled
        && jsonRPCHttpOptionGroup.rpcHttpTlsKnownClientsFile == null) {
      throw new ParameterException(
          commandLine,
          "Known-clients file must be specified or CA clients must be enabled when TLS client authentication is enabled for JSON-RPC HTTP endpoint");
    }

    jsonRPCHttpOptionGroup.rpcHttpTlsProtocols.retainAll(getJDKEnabledProtocols());
    if (jsonRPCHttpOptionGroup.rpcHttpTlsProtocols.isEmpty()) {
      throw new ParameterException(
          commandLine,
          "No valid TLS protocols specified (the following protocols are enabled: "
              + getJDKEnabledProtocols()
              + ")");
    }

    for (final String cipherSuite : jsonRPCHttpOptionGroup.rpcHttpTlsCipherSuites) {
      if (!getJDKEnabledCipherSuites().contains(cipherSuite)) {
        throw new ParameterException(
            commandLine, "Invalid TLS cipher suite specified " + cipherSuite);
      }
    }

    jsonRPCHttpOptionGroup.rpcHttpTlsCipherSuites.retainAll(getJDKEnabledCipherSuites());

    return Optional.of(
        TlsConfiguration.Builder.aTlsConfiguration()
            .withKeyStorePath(jsonRPCHttpOptionGroup.rpcHttpTlsKeyStoreFile)
            .withKeyStorePasswordSupplier(
                new FileBasedPasswordProvider(
                    jsonRPCHttpOptionGroup.rpcHttpTlsKeyStorePasswordFile))
            .withClientAuthConfiguration(rpcHttpTlsClientAuthConfiguration())
            .withSecureTransportProtocols(jsonRPCHttpOptionGroup.rpcHttpTlsProtocols)
            .withCipherSuites(jsonRPCHttpOptionGroup.rpcHttpTlsCipherSuites)
            .build());
  }

  private TlsClientAuthConfiguration rpcHttpTlsClientAuthConfiguration() {
    if (jsonRPCHttpOptionGroup.isRpcHttpTlsClientAuthEnabled) {
      return TlsClientAuthConfiguration.Builder.aTlsClientAuthConfiguration()
          .withKnownClientsFile(jsonRPCHttpOptionGroup.rpcHttpTlsKnownClientsFile)
          .withCaClientsEnabled(jsonRPCHttpOptionGroup.isRpcHttpTlsCAClientsEnabled)
          .build();
    }

    return null;
  }

  private boolean isRpcTlsConfigurationRequired() {
    return jsonRPCHttpOptionGroup.isRpcHttpEnabled && jsonRPCHttpOptionGroup.isRpcHttpTlsEnabled;
  }

  private WebSocketConfiguration webSocketConfiguration(
      final Integer listenPort, final List<String> apiGroups, final List<String> allowCallsFrom) {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--rpc-ws-enabled",
        !jsonRPCWebsocketOptionGroup.isRpcWsEnabled,
        asList(
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

    if (jsonRPCWebsocketOptionGroup.isRpcWsAuthenticationEnabled) {
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--rpc-ws-authentication-public-key-file",
          jsonRPCWebsocketOptionGroup.rpcWsAuthenticationPublicKeyFile == null,
          asList("--rpc-ws-authentication-jwt-algorithm"));
    }

    if (jsonRPCWebsocketOptionGroup.isRpcWsAuthenticationEnabled
        && rpcWsAuthenticationCredentialsFile() == null
        && jsonRPCWebsocketOptionGroup.rpcWsAuthenticationPublicKeyFile == null) {
      throw new ParameterException(
          commandLine,
          "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file or authentication public key file");
    }

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(jsonRPCWebsocketOptionGroup.isRpcWsEnabled);
    webSocketConfiguration.setHost(
        Strings.isNullOrEmpty(jsonRPCWebsocketOptionGroup.rpcWsHost)
            ? p2PDiscoveryOptionGroup.autoDiscoverDefaultIP().getHostAddress()
            : jsonRPCWebsocketOptionGroup.rpcWsHost);
    webSocketConfiguration.setPort(listenPort);
    webSocketConfiguration.setMaxFrameSize(jsonRPCWebsocketOptionGroup.rpcWsMaxFrameSize);
    webSocketConfiguration.setMaxActiveConnections(jsonRPCWebsocketOptionGroup.rpcWsMaxConnections);
    webSocketConfiguration.setRpcApis(apiGroups);
    webSocketConfiguration.setRpcApisNoAuth(
        jsonRPCWebsocketOptionGroup.rpcWsApiMethodsNoAuth.stream()
            .distinct()
            .collect(Collectors.toList()));
    webSocketConfiguration.setAuthenticationEnabled(
        jsonRPCWebsocketOptionGroup.isRpcWsAuthenticationEnabled);
    webSocketConfiguration.setAuthenticationCredentialsFile(rpcWsAuthenticationCredentialsFile());
    webSocketConfiguration.setHostsAllowlist(allowCallsFrom);
    webSocketConfiguration.setAuthenticationPublicKeyFile(
        jsonRPCWebsocketOptionGroup.rpcWsAuthenticationPublicKeyFile);
    webSocketConfiguration.setAuthenticationAlgorithm(
        jsonRPCWebsocketOptionGroup.rpcWebsocketsAuthenticationAlgorithm);
    webSocketConfiguration.setTimeoutSec(unstableRPCOptions.getWsTimeoutSec());
    return webSocketConfiguration;
  }

  private ApiConfiguration apiConfiguration() {
    checkApiOptionsDependencies();
    var builder =
        ImmutableApiConfiguration.builder()
            .gasPriceBlocks(apiGasPriceBlocks)
            .gasPricePercentile(apiGasPricePercentile)
            .gasPriceMinSupplier(
                getMiningParameters().getMinTransactionGasPrice().getAsBigInteger()::longValueExact)
            .gasPriceMax(apiGasPriceMax)
            .maxLogsRange(rpcMaxLogsRange)
            .gasCap(rpcGasCap)
            .isGasAndPriorityFeeLimitingEnabled(apiGasAndPriorityFeeLimitingEnabled);
    if (apiGasAndPriorityFeeLimitingEnabled) {
      if (apiGasAndPriorityFeeLowerBoundCoefficient > apiGasAndPriorityFeeUpperBoundCoefficient) {
        throw new ParameterException(
            this.commandLine,
            "--api-gas-and-priority-fee-lower-bound-coefficient cannot be greater than the value of --api-gas-and-priority-fee-upper-bound-coefficient");
      }
      builder
          .lowerBoundGasAndPriorityFeeCoefficient(apiGasAndPriorityFeeLowerBoundCoefficient)
          .upperBoundGasAndPriorityFeeCoefficient(apiGasAndPriorityFeeUpperBoundCoefficient);
    }
    return builder.build();
  }

  /**
   * Metrics Configuration for Besu
   *
   * @return instance of MetricsConfiguration.
   */
  public MetricsConfiguration metricsConfiguration() {
    if (metricsOptionGroup.isMetricsEnabled && metricsOptionGroup.isMetricsPushEnabled) {
      throw new ParameterException(
          this.commandLine,
          "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
              + "time.  Please refer to CLI reference for more details about this constraint.");
    }

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-enabled",
        !metricsOptionGroup.isMetricsEnabled,
        asList("--metrics-host", "--metrics-port"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !metricsOptionGroup.isMetricsPushEnabled,
        asList(
            "--metrics-push-host",
            "--metrics-push-port",
            "--metrics-push-interval",
            "--metrics-push-prometheus-job"));

    return unstableMetricsCLIOptions
        .toDomainObject()
        .enabled(metricsOptionGroup.isMetricsEnabled)
        .host(
            Strings.isNullOrEmpty(metricsOptionGroup.metricsHost)
                ? p2PDiscoveryOptionGroup.autoDiscoverDefaultIP().getHostAddress()
                : metricsOptionGroup.metricsHost)
        .port(metricsOptionGroup.metricsPort)
        .protocol(metricsOptionGroup.metricsProtocol)
        .metricCategories(metricsOptionGroup.metricCategories)
        .pushEnabled(metricsOptionGroup.isMetricsPushEnabled)
        .pushHost(
            Strings.isNullOrEmpty(metricsOptionGroup.metricsPushHost)
                ? p2PDiscoveryOptionGroup.autoDiscoverDefaultIP().getHostAddress()
                : metricsOptionGroup.metricsPushHost)
        .pushPort(metricsOptionGroup.metricsPushPort)
        .pushInterval(metricsOptionGroup.metricsPushInterval)
        .hostsAllowlist(hostsAllowlist)
        .prometheusJob(metricsOptionGroup.metricsPrometheusJob)
        .build();
  }

  private Optional<PermissioningConfiguration> permissioningConfiguration() throws Exception {
    if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
      if (jsonRPCHttpOptionGroup.rpcHttpApis.contains(RpcApis.PERM.name())
          || jsonRPCWebsocketOptionGroup.rpcWsApis.contains(RpcApis.PERM.name())) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    final Optional<LocalPermissioningConfiguration> localPermissioningConfigurationOptional;
    if (localPermissionsEnabled()) {
      final Optional<String> nodePermissioningConfigFile =
          Optional.ofNullable(permissionsOptionGroup.nodePermissionsConfigFile);
      final Optional<String> accountPermissioningConfigFile =
          Optional.ofNullable(permissionsOptionGroup.accountPermissionsConfigFile);

      final LocalPermissioningConfiguration localPermissioningConfiguration =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              permissionsOptionGroup.permissionsNodesEnabled,
              getEnodeDnsConfiguration(),
              nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath()),
              permissionsOptionGroup.permissionsAccountsEnabled,
              accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath()));

      localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration);
    } else {
      if (permissionsOptionGroup.nodePermissionsConfigFile != null
          && !permissionsOptionGroup.permissionsNodesEnabled) {
        logger.warn(
            "Node permissioning config file set {} but no permissions enabled",
            permissionsOptionGroup.nodePermissionsConfigFile);
      }

      if (permissionsOptionGroup.accountPermissionsConfigFile != null
          && !permissionsOptionGroup.permissionsAccountsEnabled) {
        logger.warn(
            "Account permissioning config file set {} but no permissions enabled",
            permissionsOptionGroup.accountPermissionsConfigFile);
      }
      localPermissioningConfigurationOptional = Optional.empty();
    }

    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        SmartContractPermissioningConfiguration.createDefault();

    if (Boolean.TRUE.equals(permissionsOptionGroup.permissionsNodesContractEnabled)) {
      if (permissionsOptionGroup.permissionsNodesContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No node permissioning contract address specified. Cannot enable smart contract based node permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractNodeAllowlistEnabled(
            permissionsOptionGroup.permissionsNodesContractEnabled);
        smartContractPermissioningConfiguration.setNodeSmartContractAddress(
            permissionsOptionGroup.permissionsNodesContractAddress);
        smartContractPermissioningConfiguration.setNodeSmartContractInterfaceVersion(
            permissionsOptionGroup.permissionsNodesContractVersion);
      }
    } else if (permissionsOptionGroup.permissionsNodesContractAddress != null) {
      logger.warn(
          "Node permissioning smart contract address set {} but smart contract node permissioning is disabled.",
          permissionsOptionGroup.permissionsNodesContractAddress);
    }

    if (Boolean.TRUE.equals(permissionsOptionGroup.permissionsAccountsContractEnabled)) {
      if (permissionsOptionGroup.permissionsAccountsContractAddress == null) {
        throw new ParameterException(
            this.commandLine,
            "No account permissioning contract address specified. Cannot enable smart contract based account permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractAccountAllowlistEnabled(
            permissionsOptionGroup.permissionsAccountsContractEnabled);
        smartContractPermissioningConfiguration.setAccountSmartContractAddress(
            permissionsOptionGroup.permissionsAccountsContractAddress);
      }
    } else if (permissionsOptionGroup.permissionsAccountsContractAddress != null) {
      logger.warn(
          "Account permissioning smart contract address set {} but smart contract account permissioning is disabled.",
          permissionsOptionGroup.permissionsAccountsContractAddress);
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            localPermissioningConfigurationOptional,
            Optional.of(smartContractPermissioningConfiguration));

    return Optional.of(permissioningConfiguration);
  }

  private boolean localPermissionsEnabled() {
    return permissionsOptionGroup.permissionsAccountsEnabled
        || permissionsOptionGroup.permissionsNodesEnabled;
  }

  private boolean contractPermissionsEnabled() {
    return permissionsOptionGroup.permissionsNodesContractEnabled
        || permissionsOptionGroup.permissionsAccountsContractEnabled;
  }

  private PrivacyParameters privacyParameters() {

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--privacy-enabled",
        !privacyOptionGroup.isPrivacyEnabled,
        asList("--privacy-multi-tenancy-enabled", "--privacy-tls-enabled"));

    CommandLineUtils.checkMultiOptionDependencies(
        logger,
        commandLine,
        "--privacy-url and/or --privacy-public-key-file ignored because none of --privacy-enabled was defined.",
        List.of(!privacyOptionGroup.isPrivacyEnabled),
        List.of("--privacy-url", "--privacy-public-key-file"));

    checkPrivacyTlsOptionsDependencies();

    final PrivacyParameters.Builder privacyParametersBuilder = new PrivacyParameters.Builder();
    if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyEnabled)) {
      final String errorSuffix = "cannot be enabled with privacy.";
      if (syncMode == SyncMode.FAST) {
        throw new ParameterException(commandLine, String.format("%s %s", "Fast sync", errorSuffix));
      }
      if (isPruningEnabled()) {
        throw new ParameterException(commandLine, String.format("%s %s", "Pruning", errorSuffix));
      }

      if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyMultiTenancyEnabled)
          && Boolean.FALSE.equals(jsonRpcConfiguration.isAuthenticationEnabled())
          && Boolean.FALSE.equals(webSocketConfiguration.isAuthenticationEnabled())) {
        throw new ParameterException(
            commandLine,
            "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled");
      }

      privacyParametersBuilder.setEnabled(true);
      privacyParametersBuilder.setEnclaveUrl(privacyOptionGroup.privacyUrl);
      privacyParametersBuilder.setMultiTenancyEnabled(
          privacyOptionGroup.isPrivacyMultiTenancyEnabled);
      privacyParametersBuilder.setFlexiblePrivacyGroupsEnabled(
          privacyOptionGroup.isFlexiblePrivacyGroupsEnabled
              || privacyOptionGroup.isOnchainPrivacyGroupsEnabled);
      privacyParametersBuilder.setPrivacyPluginEnabled(
          unstablePrivacyPluginOptions.isPrivacyPluginEnabled());

      final boolean hasPrivacyPublicKey = privacyOptionGroup.privacyPublicKeyFile != null;

      if (hasPrivacyPublicKey
          && Boolean.TRUE.equals(privacyOptionGroup.isPrivacyMultiTenancyEnabled)) {
        throw new ParameterException(
            commandLine, "Privacy multi-tenancy and privacy public key cannot be used together");
      }

      if (!hasPrivacyPublicKey
          && !privacyOptionGroup.isPrivacyMultiTenancyEnabled
          && !unstablePrivacyPluginOptions.isPrivacyPluginEnabled()) {
        throw new ParameterException(
            commandLine, "Please specify Enclave public key file path to enable privacy");
      }

      if (hasPrivacyPublicKey
          && Boolean.FALSE.equals(privacyOptionGroup.isPrivacyMultiTenancyEnabled)) {
        try {
          privacyParametersBuilder.setPrivacyUserIdUsingFile(
              privacyOptionGroup.privacyPublicKeyFile);
        } catch (final IOException e) {
          throw new ParameterException(
              commandLine, "Problem with privacy-public-key-file: " + e.getMessage(), e);
        } catch (final IllegalArgumentException e) {
          throw new ParameterException(
              commandLine, "Contents of privacy-public-key-file invalid: " + e.getMessage(), e);
        }
      }

      privacyParametersBuilder.setPrivateKeyPath(
          privacyOptionGroup.privateMarkerTransactionSigningKeyPath);
      privacyParametersBuilder.setStorageProvider(
          privacyKeyStorageProvider(keyValueStorageName + "-privacy"));
      if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyTlsEnabled)) {
        privacyParametersBuilder.setPrivacyKeyStoreFile(privacyOptionGroup.privacyKeyStoreFile);
        privacyParametersBuilder.setPrivacyKeyStorePasswordFile(
            privacyOptionGroup.privacyKeyStorePasswordFile);
        privacyParametersBuilder.setPrivacyTlsKnownEnclaveFile(
            privacyOptionGroup.privacyTlsKnownEnclaveFile);
      }
      privacyParametersBuilder.setEnclaveFactory(new EnclaveFactory(vertx));
    }

    if (Boolean.FALSE.equals(privacyOptionGroup.isPrivacyEnabled) && anyPrivacyApiEnabled()) {
      logger.warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");
    }

    privacyParametersBuilder.setPrivacyService(privacyPluginService);
    final PrivacyParameters privacyParameters = privacyParametersBuilder.build();

    if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyEnabled)) {
      preSynchronizationTaskRunner.addTask(
          new PrivateDatabaseMigrationPreSyncTask(
              privacyParameters, privacyOptionGroup.migratePrivateDatabase));
    }

    return privacyParameters;
  }

  private boolean anyPrivacyApiEnabled() {
    return jsonRPCHttpOptionGroup.rpcHttpApis.contains(RpcApis.EEA.name())
        || jsonRPCWebsocketOptionGroup.rpcWsApis.contains(RpcApis.EEA.name())
        || jsonRPCHttpOptionGroup.rpcHttpApis.contains(RpcApis.PRIV.name())
        || jsonRPCWebsocketOptionGroup.rpcWsApis.contains(RpcApis.PRIV.name());
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

  /**
   * Get the storage provider
   *
   * @return the storage provider
   */
  public StorageProvider getStorageProvider() {
    return keyValueStorageProvider(keyValueStorageName);
  }

  private Optional<PkiBlockCreationConfiguration> maybePkiBlockCreationConfiguration() {
    return pkiBlockCreationOptions
        .asDomainConfig(commandLine)
        .map(pkiBlockCreationConfigProvider::load);
  }

  private SynchronizerConfiguration buildSyncConfig() {
    return unstableSynchronizerOptions
        .toDomainObject()
        .syncMode(syncMode)
        .fastSyncMinimumPeerCount(fastSyncMinPeerCount)
        .build();
  }

  private TransactionPoolConfiguration buildTransactionPoolConfiguration() {
    final var txPoolConf = transactionPoolOptions.toDomainObject();
    final var txPoolConfBuilder =
        ImmutableTransactionPoolConfiguration.builder()
            .from(txPoolConf)
            .saveFile((dataPath.resolve(txPoolConf.getSaveFile().getPath()).toFile()));

    if (getActualGenesisConfigOptions().isZeroBaseFee()) {
      logger.info(
          "Forcing price bump for transaction replacement to 0, since we are on a zero basefee network");
      txPoolConfBuilder.priceBump(Percentage.ZERO);
    }

    if (getMiningParameters().getMinTransactionGasPrice().equals(Wei.ZERO)
        && !transactionPoolOptions.isPriceBumpSet(commandLine)) {
      logger.info(
          "Forcing price bump for transaction replacement to 0, since min-gas-price is set to 0");
      txPoolConfBuilder.priceBump(Percentage.ZERO);
    }

    if (getMiningParameters().getMinTransactionGasPrice().lessThan(txPoolConf.getMinGasPrice())) {
      if (transactionPoolOptions.isMinGasPriceSet(commandLine)) {
        throw new ParameterException(
            commandLine, "tx-pool-min-gas-price cannot be greater than the value of min-gas-price");

      } else {
        // for backward compatibility, if tx-pool-min-gas-price is not set, we adjust its value
        // to be the same as min-gas-price, so the behavior is as before this change, and we notify
        // the user of the change
        logger.warn(
            "Forcing tx-pool-min-gas-price="
                + getMiningParameters().getMinTransactionGasPrice().toDecimalString()
                + ", since it cannot be greater than the value of min-gas-price");
        txPoolConfBuilder.minGasPrice(getMiningParameters().getMinTransactionGasPrice());
      }
    }

    return txPoolConfBuilder.build();
  }

  private MiningParameters getMiningParameters() {
    if (miningParameters == null) {
      final var miningParametersBuilder =
          ImmutableMiningParameters.builder().from(miningOptions.toDomainObject());
      final var actualGenesisOptions = getActualGenesisConfigOptions();
      if (actualGenesisOptions.isPoa()) {
        miningParametersBuilder.unstable(
            ImmutableMiningParameters.Unstable.builder()
                .minBlockTime(getMinBlockTime(actualGenesisOptions))
                .build());
      }
      miningParameters = miningParametersBuilder.build();
    }
    return miningParameters;
  }

  private int getMinBlockTime(final GenesisConfigOptions genesisConfigOptions) {
    if (genesisConfigOptions.isClique()) {
      return genesisConfigOptions.getCliqueConfigOptions().getBlockPeriodSeconds();
    }

    if (genesisConfigOptions.isIbft2()) {
      return genesisConfigOptions.getBftConfigOptions().getBlockPeriodSeconds();
    }

    if (genesisConfigOptions.isQbft()) {
      return genesisConfigOptions.getQbftConfigOptions().getBlockPeriodSeconds();
    }

    throw new IllegalArgumentException("Should only be called for a PoA network");
  }

  private boolean isPruningEnabled() {
    return pruningEnabled;
  }

  // Blockchain synchronization from peers.
  private Runner synchronize(
      final BesuController controller,
      final boolean p2pEnabled,
      final Optional<TLSConfiguration> p2pTLSConfiguration,
      final boolean peerDiscoveryEnabled,
      final EthNetworkConfig ethNetworkConfig,
      final String p2pAdvertisedHost,
      final String p2pListenInterface,
      final int p2pListenPort,
      final GraphQLConfiguration graphQLConfiguration,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final JsonRpcConfiguration engineJsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final JsonRpcIpcConfiguration jsonRpcIpcConfiguration,
      final ApiConfiguration apiConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Collection<EnodeURL> staticNodes,
      final Path pidPath) {

    checkNotNull(runnerBuilder);

    p2pTLSConfiguration.ifPresent(runnerBuilder::p2pTLSConfiguration);

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
            .permissioningConfiguration(permissioningConfiguration)
            .p2pAdvertisedHost(p2pAdvertisedHost)
            .p2pListenInterface(p2pListenInterface)
            .p2pListenPort(p2pListenPort)
            .networkingConfiguration(unstableNetworkingOptions.toDomainObject())
            .legacyForkId(unstableEthProtocolOptions.toDomainObject().isLegacyEth64ForkIdEnabled())
            .graphQLConfiguration(graphQLConfiguration)
            .jsonRpcConfiguration(jsonRpcConfiguration)
            .engineJsonRpcConfiguration(engineJsonRpcConfiguration)
            .webSocketConfiguration(webSocketConfiguration)
            .jsonRpcIpcConfiguration(jsonRpcIpcConfiguration)
            .apiConfiguration(apiConfiguration)
            .pidPath(pidPath)
            .dataDir(dataDir())
            .bannedNodeIds(p2PDiscoveryOptionGroup.bannedNodeIds)
            .metricsSystem(metricsSystem)
            .permissioningService(permissioningService)
            .metricsConfiguration(metricsConfiguration)
            .staticNodes(staticNodes)
            .identityString(identityString)
            .besuPluginContext(besuPluginContext)
            .autoLogBloomCaching(autoLogBloomCachingEnabled)
            .ethstatsOptions(ethstatsOptions)
            .storageProvider(keyValueStorageProvider(keyValueStorageName))
            .rpcEndpointService(rpcEndpointServiceImpl)
            .enodeDnsConfiguration(getEnodeDnsConfiguration())
            .build();

    addShutdownHook(runner);

    return runner;
  }

  /**
   * Builds Vertx instance from VertxOptions. Visible for testing.
   *
   * @param vertxOptions Instance of VertxOptions
   * @return Instance of Vertx.
   */
  @VisibleForTesting
  protected Vertx createVertx(final VertxOptions vertxOptions) {
    return Vertx.vertx(vertxOptions);
  }

  private VertxOptions createVertxOptions(final MetricsSystem metricsSystem) {
    return new VertxOptions()
        .setPreferNativeTransport(true)
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
                    LogConfigurator.shutdown();
                  } catch (final Exception e) {
                    logger.error("Failed to stop Besu");
                  }
                },
                "BesuCommand-Shutdown-Hook"));
  }

  private EthNetworkConfig updateNetworkConfig(final NetworkName network) {
    final EthNetworkConfig.Builder builder =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network));

    if (genesisFile != null) {
      if (commandLine.getParseResult().hasMatchedOption("network")) {
        throw new ParameterException(
            this.commandLine,
            "--network option and --genesis-file option can't be used at the same time.  Please "
                + "refer to CLI reference for more details about this constraint.");
      }

      builder.setGenesisConfig(genesisConfig());

      if (networkId == null) {
        // If no chain id is found in the genesis, use mainnet network id
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

      if (p2PDiscoveryOptionGroup.bootNodes == null) {
        builder.setBootNodes(new ArrayList<>());
      }
      builder.setDnsDiscoveryUrl(null);
    }

    if (p2PDiscoveryOptionGroup.discoveryDnsUrl != null) {
      builder.setDnsDiscoveryUrl(p2PDiscoveryOptionGroup.discoveryDnsUrl);
    } else if (genesisConfigOptions != null) {
      final Optional<String> discoveryDnsUrlFromGenesis =
          genesisConfigOptions.getDiscoveryOptions().getDiscoveryDnsUrl();
      discoveryDnsUrlFromGenesis.ifPresent(builder::setDnsDiscoveryUrl);
    }

    if (networkId != null) {
      builder.setNetworkId(networkId);
    }

    List<EnodeURL> listBootNodes = null;
    if (p2PDiscoveryOptionGroup.bootNodes != null) {
      try {
        listBootNodes = buildEnodes(p2PDiscoveryOptionGroup.bootNodes, getEnodeDnsConfiguration());
      } catch (final IllegalArgumentException e) {
        throw new ParameterException(commandLine, e.getMessage());
      }
    } else if (genesisConfigOptions != null) {
      final Optional<List<String>> bootNodesFromGenesis =
          genesisConfigOptions.getDiscoveryOptions().getBootNodes();
      if (bootNodesFromGenesis.isPresent()) {
        listBootNodes = buildEnodes(bootNodesFromGenesis.get(), getEnodeDnsConfiguration());
      }
    }
    if (listBootNodes != null) {
      if (!p2PDiscoveryOptionGroup.peerDiscoveryEnabled) {
        logger.warn("Discovery disabled: bootnodes will be ignored.");
      } else {
        logger.info("Configured {} bootnodes.", listBootNodes.size());
        logger.debug("Bootnodes = {}", listBootNodes);
      }
      DiscoveryConfiguration.assertValidBootnodes(listBootNodes);
      builder.setBootNodes(listBootNodes);
    } else {
      logger.info("0 Bootnodes configured");
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
          this.commandLine, String.format("Unable to load genesis URL %s.", genesisFile), e);
    }
  }

  private static String genesisConfig(final NetworkName networkName) {
    try (final InputStream genesisFileInputStream =
        EthNetworkConfig.class.getResourceAsStream(networkName.getGenesisFile())) {
      return new String(genesisFileInputStream.readAllBytes(), UTF_8);
    } catch (final IOException | NullPointerException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns data directory used by Besu. Visible as it is accessed by other subcommands.
   *
   * @return Path representing data directory.
   */
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

  private SecurityModule securityModule() {
    return securityModuleService
        .getByName(securityModuleName)
        .orElseThrow(() -> new RuntimeException("Security Module not found: " + securityModuleName))
        .get();
  }

  private File resolveNodePrivateKeyFile(final File nodePrivateKeyFile) {
    return Optional.ofNullable(nodePrivateKeyFile)
        .orElseGet(() -> KeyPairUtil.getDefaultKeyFile(dataDir()));
  }

  private String rpcHttpAuthenticationCredentialsFile() {
    final String filename = jsonRPCHttpOptionGroup.rpcHttpAuthenticationCredentialsFile;

    if (filename != null) {
      RpcAuthFileValidator.validate(commandLine, filename, "HTTP");
    }
    return filename;
  }

  private String rpcWsAuthenticationCredentialsFile() {
    final String filename = jsonRPCWebsocketOptionGroup.rpcWsAuthenticationCredentialsFile;

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

  /**
   * Metrics System used by Besu
   *
   * @return Instance of MetricsSystem
   */
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
    logger.debug("Static Nodes file: {}", staticNodesPath);
    final Set<EnodeURL> staticNodes =
        StaticNodesParser.fromPath(staticNodesPath, getEnodeDnsConfiguration());
    logger.info("Connecting to {} static nodes.", staticNodes.size());
    logger.debug("Static Nodes = {}", staticNodes);
    return staticNodes;
  }

  private List<EnodeURL> buildEnodes(
      final List<String> bootNodes, final EnodeDnsConfiguration enodeDnsConfiguration) {
    return bootNodes.stream()
        .filter(bootNode -> !bootNode.isEmpty())
        .map(bootNode -> EnodeURLImpl.fromString(bootNode, enodeDnsConfiguration))
        .collect(Collectors.toList());
  }

  /**
   * Besu CLI Parameters exception handler used by VertX. Visible for testing.
   *
   * @return instance of BesuParameterExceptionHandler
   */
  public BesuParameterExceptionHandler parameterExceptionHandler() {
    return new BesuParameterExceptionHandler(this::getLogLevel);
  }

  /**
   * Returns BesuExecutionExceptionHandler. Visible as it is used in testing.
   *
   * @return instance of BesuExecutionExceptionHandler used by Vertx.
   */
  public BesuExecutionExceptionHandler executionExceptionHandler() {
    return new BesuExecutionExceptionHandler();
  }

  /**
   * Represents Enode DNS Configuration. Visible for testing.
   *
   * @return instance of EnodeDnsConfiguration
   */
  @VisibleForTesting
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
   * Check if required ports are available
   *
   * @throws InvalidConfigurationException if ports are not available.
   */
  protected void checkIfRequiredPortsAreAvailable() {
    final List<Integer> unavailablePorts = new ArrayList<>();
    getEffectivePorts().stream()
        .filter(Objects::nonNull)
        .filter(port -> port > 0)
        .forEach(
            port -> {
              if (port.equals(p2PDiscoveryOptionGroup.p2pPort)
                  && !NetworkUtility.isPortAvailable(port)) {
                unavailablePorts.add(port);
              }
              if (!port.equals(p2PDiscoveryOptionGroup.p2pPort)
                  && !NetworkUtility.isPortAvailableForTcp(port)) {
                unavailablePorts.add(port);
              }
            });
    if (!unavailablePorts.isEmpty()) {
      throw new InvalidConfigurationException(
          "Port(s) '"
              + unavailablePorts
              + "' already in use. Check for other processes using the port(s).");
    }
  }

  /**
   * * Gets the list of effective ports (ports that are enabled).
   *
   * @return The list of effective ports
   */
  private List<Integer> getEffectivePorts() {
    final List<Integer> effectivePorts = new ArrayList<>();
    addPortIfEnabled(
        effectivePorts, p2PDiscoveryOptionGroup.p2pPort, p2PDiscoveryOptionGroup.p2pEnabled);
    addPortIfEnabled(
        effectivePorts,
        graphQlOptionGroup.graphQLHttpPort,
        graphQlOptionGroup.isGraphQLHttpEnabled);
    addPortIfEnabled(
        effectivePorts,
        jsonRPCHttpOptionGroup.rpcHttpPort,
        jsonRPCHttpOptionGroup.isRpcHttpEnabled);
    addPortIfEnabled(
        effectivePorts,
        jsonRPCWebsocketOptionGroup.rpcWsPort,
        jsonRPCWebsocketOptionGroup.isRpcWsEnabled);
    addPortIfEnabled(effectivePorts, engineRPCOptionGroup.engineRpcPort, isEngineApiEnabled());
    addPortIfEnabled(
        effectivePorts, metricsOptionGroup.metricsPort, metricsOptionGroup.isMetricsEnabled);
    addPortIfEnabled(
        effectivePorts,
        getMiningParameters().getStratumPort(),
        getMiningParameters().isStratumMiningEnabled());
    return effectivePorts;
  }

  /**
   * Adds port to the specified list only if enabled.
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

  @VisibleForTesting
  String getLogLevel() {
    return loggingLevelOption.getLogLevel();
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
      return dataStorageOptions.toDomainObject().getDataStorageFormat().getDatabaseVersion();
    }
  }

  private void instantiateSignatureAlgorithmFactory() {
    if (SignatureAlgorithmFactory.isInstanceSet()) {
      return;
    }

    final Optional<String> ecCurve = getEcCurveFromGenesisFile();

    if (ecCurve.isEmpty()) {
      SignatureAlgorithmFactory.setDefaultInstance();
      return;
    }

    try {
      SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create(ecCurve.get()));
    } catch (final IllegalArgumentException e) {
      throw new CommandLine.InitializationException(
          new StringBuilder()
              .append("Invalid genesis file configuration for ecCurve. ")
              .append(e.getMessage())
              .toString());
    }
  }

  private Optional<String> getEcCurveFromGenesisFile() {
    if (genesisFile == null) {
      return Optional.empty();
    }
    return genesisConfigOptions.getEcCurve();
  }

  private GenesisConfigOptions getActualGenesisConfigOptions() {
    return Optional.ofNullable(genesisConfigOptions)
        .orElseGet(
            () ->
                GenesisConfigFile.fromConfig(
                        genesisConfig(Optional.ofNullable(network).orElse(MAINNET)))
                    .getConfigOptions(genesisConfigOverrides));
  }

  private void setMergeConfigOptions() {
    MergeConfigOptions.setMergeEnabled(
        getActualGenesisConfigOptions().getTerminalTotalDifficulty().isPresent());
  }

  /** Set ignorable segments in RocksDB Storage Provider plugin. */
  public void setIgnorableStorageSegments() {
    if (!unstableChainPruningOptions.getChainDataPruningEnabled()) {
      rocksDBPlugin.addIgnorableSegmentIdentifier(KeyValueSegmentIdentifier.CHAIN_PRUNER_STATE);
    }
  }

  private void validatePostMergeCheckpointBlockRequirements() {
    final GenesisConfigOptions genesisOptions = getActualGenesisConfigOptions();
    final SynchronizerConfiguration synchronizerConfiguration =
        unstableSynchronizerOptions.toDomainObject().build();
    final Optional<UInt256> terminalTotalDifficulty = genesisOptions.getTerminalTotalDifficulty();
    final CheckpointConfigOptions checkpointConfigOptions = genesisOptions.getCheckpointOptions();
    if (synchronizerConfiguration.isCheckpointPostMergeEnabled()) {
      if (!checkpointConfigOptions.isValid()) {
        throw new InvalidConfigurationException(
            "PoS checkpoint sync requires a checkpoint block configured in the genesis file");
      }
      terminalTotalDifficulty.ifPresentOrElse(
          ttd -> {
            if (UInt256.fromHexString(
                        genesisOptions.getCheckpointOptions().getTotalDifficulty().get())
                    .equals(UInt256.ZERO)
                && ttd.equals(UInt256.ZERO)) {
              throw new InvalidConfigurationException(
                  "PoS checkpoint sync can't be used with TTD = 0 and checkpoint totalDifficulty = 0");
            }
            if (UInt256.fromHexString(
                    genesisOptions.getCheckpointOptions().getTotalDifficulty().get())
                .lessThan(ttd)) {
              throw new InvalidConfigurationException(
                  "PoS checkpoint sync requires a block with total difficulty greater or equal than the TTD");
            }
          },
          () -> {
            throw new InvalidConfigurationException(
                "PoS checkpoint sync requires TTD in the genesis file");
          });
    }
  }

  private boolean isMergeEnabled() {
    return MergeConfigOptions.isMergeEnabled();
  }

  private boolean isEngineApiEnabled() {
    return engineRPCOptionGroup.overrideEngineRpcEnabled || isMergeEnabled();
  }

  private static List<String> getJDKEnabledCipherSuites() {
    try {
      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      final SSLEngine engine = context.createSSLEngine();
      return Arrays.asList(engine.getEnabledCipherSuites());
    } catch (final KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> getJDKEnabledProtocols() {
    try {
      final SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      final SSLEngine engine = context.createSSLEngine();
      return Arrays.asList(engine.getEnabledProtocols());
    } catch (final KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private SyncMode getDefaultSyncModeIfNotSet() {
    return Optional.ofNullable(syncMode)
        .orElse(
            genesisFile == null
                    && !privacyOptionGroup.isPrivacyEnabled
                    && Optional.ofNullable(network).map(NetworkName::canFastSync).orElse(false)
                ? SyncMode.FAST
                : SyncMode.FULL);
  }

  private String generateConfigurationOverview() {
    final ConfigurationOverviewBuilder builder = new ConfigurationOverviewBuilder(logger);

    if (environment != null) {
      builder.setEnvironment(environment);
    }

    if (network != null) {
      builder.setNetwork(network.normalize());
    }

    builder.setHasCustomGenesis(genesisFile != null);
    if (genesisFile != null) {
      builder.setCustomGenesis(genesisFile.getAbsolutePath());
    }
    builder.setNetworkId(ethNetworkConfig.getNetworkId());

    builder
        .setDataStorage(dataStorageOptions.normalizeDataStorageFormat())
        .setSyncMode(syncMode.normalize());

    if (jsonRpcConfiguration != null && jsonRpcConfiguration.isEnabled()) {
      builder
          .setRpcPort(jsonRpcConfiguration.getPort())
          .setRpcHttpApis(jsonRpcConfiguration.getRpcApis());
    }

    if (engineJsonRpcConfiguration != null && engineJsonRpcConfiguration.isEnabled()) {
      builder
          .setEnginePort(engineJsonRpcConfiguration.getPort())
          .setEngineApis(engineJsonRpcConfiguration.getRpcApis());
      if (engineJsonRpcConfiguration.isAuthenticationEnabled()) {
        if (engineJsonRpcConfiguration.getAuthenticationPublicKeyFile() != null) {
          builder.setEngineJwtFile(
              engineJsonRpcConfiguration.getAuthenticationPublicKeyFile().getAbsolutePath());
        } else {
          // default ephemeral jwt created later
          builder.setEngineJwtFile(dataDir().toAbsolutePath() + "/" + EPHEMERAL_JWT_FILE);
        }
      }
    }

    if (rocksDBPlugin.isHighSpecEnabled()) {
      builder.setHighSpecEnabled();
    }

    if (dataStorageOptions.toDomainObject().getUnstable().getBonsaiTrieLogPruningEnabled()) {
      builder.setTrieLogPruningEnabled();
      builder.setTrieLogRetentionThreshold(
          dataStorageOptions.toDomainObject().getUnstable().getBonsaiTrieLogRetentionThreshold());
      builder.setTrieLogPruningLimit(
          dataStorageOptions.toDomainObject().getUnstable().getBonsaiTrieLogPruningLimit());
    }

    builder.setTxPoolImplementation(buildTransactionPoolConfiguration().getTxPoolImplementation());
    builder.setWorldStateUpdateMode(unstableEvmOptions.toDomainObject().worldUpdaterMode());

    builder.setPluginContext(besuComponent.getBesuPluginContext());

    return builder.build();
  }
}
