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
import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.hyperledger.besu.cli.util.CommandLineUtils.isOptionSet;
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.authentication.EngineAuthService.EPHEMERAL_JWT_FILE;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.config.ProfilesCompletionCandidates;
import org.hyperledger.besu.cli.custom.JsonRPCAllowlistHostsProperty;
import org.hyperledger.besu.cli.error.BesuExecutionExceptionHandler;
import org.hyperledger.besu.cli.error.BesuParameterExceptionHandler;
import org.hyperledger.besu.cli.options.ApiConfigurationOptions;
import org.hyperledger.besu.cli.options.ChainPruningOptions;
import org.hyperledger.besu.cli.options.DnsOptions;
import org.hyperledger.besu.cli.options.EngineRPCConfiguration;
import org.hyperledger.besu.cli.options.EngineRPCOptions;
import org.hyperledger.besu.cli.options.EthProtocolOptions;
import org.hyperledger.besu.cli.options.EthstatsOptions;
import org.hyperledger.besu.cli.options.EvmOptions;
import org.hyperledger.besu.cli.options.GraphQlOptions;
import org.hyperledger.besu.cli.options.InProcessRpcOptions;
import org.hyperledger.besu.cli.options.IpcOptions;
import org.hyperledger.besu.cli.options.JsonRpcHttpOptions;
import org.hyperledger.besu.cli.options.LoggingLevelOption;
import org.hyperledger.besu.cli.options.MetricsOptions;
import org.hyperledger.besu.cli.options.MiningOptions;
import org.hyperledger.besu.cli.options.NatOptions;
import org.hyperledger.besu.cli.options.NativeLibraryOptions;
import org.hyperledger.besu.cli.options.NetworkingOptions;
import org.hyperledger.besu.cli.options.NodePrivateKeyFileOption;
import org.hyperledger.besu.cli.options.P2PDiscoveryOptions;
import org.hyperledger.besu.cli.options.PermissionsOptions;
import org.hyperledger.besu.cli.options.PluginsConfigurationOptions;
import org.hyperledger.besu.cli.options.PrivacyPluginOptions;
import org.hyperledger.besu.cli.options.RPCOptions;
import org.hyperledger.besu.cli.options.RpcWebsocketOptions;
import org.hyperledger.besu.cli.options.SynchronizerOptions;
import org.hyperledger.besu.cli.options.TransactionPoolOptions;
import org.hyperledger.besu.cli.options.storage.DataStorageOptions;
import org.hyperledger.besu.cli.options.storage.DiffBasedSubStorageOptions;
import org.hyperledger.besu.cli.presynctasks.PreSynchronizationTaskRunner;
import org.hyperledger.besu.cli.presynctasks.PrivateDatabaseMigrationPreSyncTask;
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand;
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand;
import org.hyperledger.besu.cli.subcommands.TxParseSubCommand;
import org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand;
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand;
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand;
import org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand;
import org.hyperledger.besu.cli.util.BesuCommandCustomFactory;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.cli.util.ConfigDefaultValueProviderStrategy;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.CheckpointConfigOptions;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.MergeConfiguration;
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
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MiningParametersMetrics;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.VersionMetadata;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.FrontierTargetingGasLimitCalculator;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.P2PDiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.StaticNodesParser;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDiffBasedSubStorageConfiguration;
import org.hyperledger.besu.evm.precompile.AbstractAltBnPrecompiledContract;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.MetricCategoryRegistryImpl;
import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockSimulationService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;
import org.hyperledger.besu.plugin.services.mining.MiningService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockSimulatorServiceImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.MiningServiceImpl;
import org.hyperledger.besu.services.P2PServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RlpConverterServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.SynchronizationServiceImpl;
import org.hyperledger.besu.services.TraceServiceImpl;
import org.hyperledger.besu.services.TransactionPoolServiceImpl;
import org.hyperledger.besu.services.TransactionPoolValidatorServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.TransactionSimulationServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryStoragePlugin;
import org.hyperledger.besu.util.EphemeryGenesisUpdater;
import org.hyperledger.besu.util.InvalidConfigurationException;
import org.hyperledger.besu.util.LogConfigurator;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.PermissioningConfigurationValidator;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.DecodeException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExecutionStrategy;
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
    footerHeading = "%nBesu is licensed under the Apache License 2.0%n",
    footer = {
      "%n%n@|fg(cyan) To get started quickly, just choose a network to sync and a profile to run with suggested defaults:|@",
      "%n@|fg(cyan) for Mainnet|@ --network=mainnet --profile=[minimalist_staker|staker]",
      "%nMore info and other profiles at https://besu.hyperledger.org%n"
    })
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
  private final BesuController.Builder controllerBuilder;
  private final BesuPluginContextImpl besuPluginContext;
  private final StorageServiceImpl storageService;
  private final SecurityModuleServiceImpl securityModuleService;
  private final PermissioningServiceImpl permissioningService;
  private final PrivacyPluginServiceImpl privacyPluginService;
  private final RpcEndpointServiceImpl rpcEndpointServiceImpl;

  private final Map<String, String> environment;
  private final MetricCategoryRegistryImpl metricCategoryRegistry =
      new MetricCategoryRegistryImpl();

  private final PreSynchronizationTaskRunner preSynchronizationTaskRunner =
      new PreSynchronizationTaskRunner();

  private final Set<Integer> allocatedPorts = new HashSet<>();
  private final Supplier<GenesisConfig> genesisConfigSupplier =
      Suppliers.memoize(this::readGenesisConfig);
  private final Supplier<GenesisConfigOptions> genesisConfigOptionsSupplier =
      Suppliers.memoize(this::readGenesisConfigOptions);
  private final Supplier<MiningConfiguration> miningParametersSupplier =
      Suppliers.memoize(this::getMiningParameters);
  private final Supplier<ApiConfiguration> apiConfigurationSupplier =
      Suppliers.memoize(this::getApiConfiguration);

  private RocksDBPlugin rocksDBPlugin;

  private int maxPeers;
  private int maxRemoteInitiatedPeers;

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

  // Genesis file path with null default option.
  // This default is handled by Runner
  // to use mainnet json file from resources as indicated in the
  // default network option
  // Then we ignore genesis default value here.
  @CommandLine.Option(
      names = {"--genesis-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Genesis file for your custom network. Setting this option requires --network-id to be set. (Cannot be used with --network)")
  private final File genesisFile = null;

  @Option(
      names = {"--genesis-state-hash-cache-enabled"},
      description =
          "Use genesis state hash from data on startup if specified (default: ${DEFAULT-VALUE})")
  private final Boolean genesisStateHashCacheEnabled = false;

  @Option(
      names = "--identity",
      paramLabel = "<String>",
      description = "Identification for this node in the Client ID",
      arity = "1")
  private final Optional<String> identityString = Optional.empty();

  private Boolean printPathsAndExit = Boolean.FALSE;
  private String besuUserName = "besu";

  @Option(
      names = "--print-paths-and-exit",
      paramLabel = "<username>",
      description = "Print the configured paths and exit without starting the node.",
      arity = "0..1")
  void setUserName(final String userName) {
    PlatformEnum currentPlatform = SystemInfo.getCurrentPlatform();
    // Only allow on Linux and macOS
    if (currentPlatform == PlatformEnum.LINUX || currentPlatform == PlatformEnum.MACOS) {
      if (userName != null) {
        besuUserName = userName;
      }
      printPathsAndExit = Boolean.TRUE;
    } else {
      throw new UnsupportedOperationException(
          "--print-paths-and-exit is only supported on Linux and macOS.");
    }
  }

  // P2P Discovery Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold P2P Discovery Options|@%n")
  P2PDiscoveryOptions p2PDiscoveryOptions = new P2PDiscoveryOptions();

  P2PDiscoveryConfiguration p2PDiscoveryConfig;

  private final TransactionSelectionServiceImpl transactionSelectionServiceImpl;
  private final TransactionPoolValidatorServiceImpl transactionValidatorServiceImpl;
  private final TransactionSimulationServiceImpl transactionSimulationServiceImpl;
  private final BlockchainServiceImpl blockchainServiceImpl;
  private BesuComponent besuComponent;

  @Option(
      names = {"--sync-mode"},
      paramLabel = MANDATORY_MODE_FORMAT_HELP,
      description =
          "Synchronization mode, possible values are ${COMPLETION-CANDIDATES} (default: SNAP if a --network is supplied and privacy isn't enabled. FULL otherwise.)")
  private SyncMode syncMode = null;

  @Option(
      names = {"--sync-min-peers", "--fast-sync-min-peers"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Minimum number of peers required before starting sync. Has effect only on non-PoS networks. (default: ${DEFAULT-VALUE})")
  private final Integer syncMinPeerCount = SYNC_MIN_PEER_COUNT;

  @Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      defaultValue = "MAINNET",
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: ${DEFAULT-VALUE})")
  private final NetworkName network = null;

  @Option(
      names = {PROFILE_OPTION_NAME},
      paramLabel = PROFILE_FORMAT_HELP,
      completionCandidates = ProfilesCompletionCandidates.class,
      description =
          "Overwrite default settings. Possible values are ${COMPLETION-CANDIDATES}. (default: none)")
  private String profile = null; // don't set it as final due to picocli completion candidates

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

  @Option(
      names = {"--version-compatibility-protection"},
      description =
          "Perform compatibility checks between the version of Besu being started and the version of Besu that last started with this data directory. (default: ${DEFAULT-VALUE})")
  private Boolean versionCompatibilityProtection = null;

  @CommandLine.ArgGroup(validate = false, heading = "@|bold GraphQL Options|@%n")
  GraphQlOptions graphQlOptions = new GraphQlOptions();

  // Engine JSON-PRC Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Engine JSON-RPC Options|@%n")
  EngineRPCOptions engineRPCOptions = new EngineRPCOptions();

  EngineRPCConfiguration engineRPCConfig = engineRPCOptions.toDomainObject();

  // JSON-RPC HTTP Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC HTTP Options|@%n")
  JsonRpcHttpOptions jsonRpcHttpOptions = new JsonRpcHttpOptions();

  // JSON-RPC Websocket Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC Websocket Options|@%n")
  RpcWebsocketOptions rpcWebsocketOptions = new RpcWebsocketOptions();

  // In-Process RPC Options
  @CommandLine.ArgGroup(validate = false, heading = "@|bold In-Process RPC Options|@%n")
  InProcessRpcOptions inProcessRpcOptions = InProcessRpcOptions.create();

  private static final String PRIVACY_DEPRECATION_PREFIX =
      "Deprecated. Tessera-based privacy is deprecated. See CHANGELOG for alternative options. ";

  // Privacy Options Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold (Deprecated) Privacy Options |@%n")
  PrivacyOptionGroup privacyOptionGroup = new PrivacyOptionGroup();

  static class PrivacyOptionGroup {
    @Option(
        names = {"--privacy-tls-enabled"},
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Enable TLS for connecting to privacy enclave (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivacyTlsEnabled = false;

    @Option(
        names = "--privacy-tls-keystore-file",
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Path to a PKCS#12 formatted keystore; used to enable TLS on inbound connections.")
    private final Path privacyKeyStoreFile = null;

    @Option(
        names = "--privacy-tls-keystore-password-file",
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Path to a file containing the password used to decrypt the keystore.")
    private final Path privacyKeyStorePasswordFile = null;

    @Option(
        names = "--privacy-tls-known-enclave-file",
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Path to a file containing the fingerprints of the authorized privacy enclave.")
    private final Path privacyTlsKnownEnclaveFile = null;

    @Option(
        names = {"--privacy-enabled"},
        description =
            PRIVACY_DEPRECATION_PREFIX + "Enable private transactions (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivacyEnabled = false;

    @Option(
        names = {"--privacy-multi-tenancy-enabled"},
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Enable multi-tenant private transactions (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivacyMultiTenancyEnabled = false;

    @Option(
        names = {"--privacy-url"},
        description = PRIVACY_DEPRECATION_PREFIX + "The URL on which the enclave is running")
    private final URI privacyUrl = PrivacyParameters.DEFAULT_ENCLAVE_URL;

    @Option(
        names = {"--privacy-public-key-file"},
        description = PRIVACY_DEPRECATION_PREFIX + "The enclave's public key file")
    private final File privacyPublicKeyFile = null;

    @Option(
        names = {"--privacy-marker-transaction-signing-key-file"},
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "The name of a file containing the private key used to sign privacy marker transactions. If unset, each will be signed with a random key.")
    private final Path privateMarkerTransactionSigningKeyPath = null;

    @Option(
        names = {"--privacy-enable-database-migration"},
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Enable private database metadata migration (default: ${DEFAULT-VALUE})")
    private final Boolean migratePrivateDatabase = false;

    @Option(
        names = {"--privacy-flexible-groups-enabled"},
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Enable flexible privacy groups (default: ${DEFAULT-VALUE})")
    private final Boolean isFlexiblePrivacyGroupsEnabled = false;

    @Option(
        names = {"--privacy-nonce-always-increments"},
        description =
            PRIVACY_DEPRECATION_PREFIX
                + "Enable private nonce "
                + "incrementation even if the transaction didn't succeeded (default: ${DEFAULT-VALUE})")
    private final Boolean isPrivateNonceAlwaysIncrementsEnabled = false;
  }

  // Metrics Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Metrics Options|@%n")
  MetricsOptions metricsOptions = MetricsOptions.create();

  @Option(
      names = {"--host-allowlist"},
      paramLabel = "<hostname>[,<hostname>...]... or * or all",
      description =
          "Comma separated list of hostnames to allow for RPC access, or * to accept any host (default: ${DEFAULT-VALUE})",
      defaultValue = "localhost,127.0.0.1")
  private final JsonRPCAllowlistHostsProperty hostsAllowlist = new JsonRPCAllowlistHostsProperty();

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

  // Permission Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold Permissions Options|@%n")
  PermissionsOptions permissionsOptions = new PermissionsOptions();

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

  @CommandLine.Option(
      names = {"--pid-path"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "Path to PID file (optional)")
  private final Path pidPath = null;

  // API Configuration Option Group
  @CommandLine.ArgGroup(validate = false, heading = "@|bold API Configuration Options|@%n")
  ApiConfigurationOptions apiConfigurationOptions = new ApiConfigurationOptions();

  @CommandLine.Option(
      names = {"--static-nodes-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Specifies the static node file containing the static nodes for this node to connect to")
  private final Path staticNodesFile = null;

  @CommandLine.Option(
      names = {"--cache-last-blocks"},
      description = "Specifies the number of last blocks to cache  (default: ${DEFAULT-VALUE})")
  private final Integer numberOfblocksToCache = 0;

  // Plugins Configuration Option Group
  @CommandLine.ArgGroup(validate = false)
  PluginsConfigurationOptions pluginsConfigurationOptions = new PluginsConfigurationOptions();

  private EthNetworkConfig ethNetworkConfig;
  private JsonRpcConfiguration jsonRpcConfiguration;
  private JsonRpcConfiguration engineJsonRpcConfiguration;
  private GraphQLConfiguration graphQLConfiguration;
  private WebSocketConfiguration webSocketConfiguration;
  private JsonRpcIpcConfiguration jsonRpcIpcConfiguration;
  private InProcessRpcConfiguration inProcessRpcConfiguration;
  private MetricsConfiguration metricsConfiguration;
  private Optional<PermissioningConfiguration> permissioningConfiguration;
  private DataStorageConfiguration dataStorageConfiguration;
  private Collection<EnodeURL> staticNodes;
  private BesuController besuController;
  private BesuConfigurationImpl pluginCommonConfiguration;

  private Vertx vertx;
  private EnodeDnsConfiguration enodeDnsConfiguration;
  private KeyValueStorageProvider keyValueStorageProvider;

  /**
   * Besu command constructor.
   *
   * @param rlpBlockImporter RlpBlockImporter supplier
   * @param jsonBlockImporterFactory instance of {@code Function<BesuController, JsonBlockImporter>}
   * @param rlpBlockExporterFactory instance of {@code Function<Blockchain, RlpBlockExporter>}
   * @param runnerBuilder instance of RunnerBuilder
   * @param controllerBuilder instance of BesuController.Builder
   * @param besuPluginContext instance of BesuPluginContextImpl
   * @param environment Environment variables map
   * @param commandLogger instance of Logger for outputting to the CLI
   */
  public BesuCommand(
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilder,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment,
      final Logger commandLogger) {
    this(
        rlpBlockImporter,
        jsonBlockImporterFactory,
        rlpBlockExporterFactory,
        runnerBuilder,
        controllerBuilder,
        besuPluginContext,
        environment,
        new StorageServiceImpl(),
        new SecurityModuleServiceImpl(),
        new PermissioningServiceImpl(),
        new PrivacyPluginServiceImpl(),
        new RpcEndpointServiceImpl(),
        new TransactionSelectionServiceImpl(),
        new TransactionPoolValidatorServiceImpl(),
        new TransactionSimulationServiceImpl(),
        new BlockchainServiceImpl(),
        commandLogger);
  }

  /**
   * Overloaded Besu command constructor visible for testing.
   *
   * @param rlpBlockImporter RlpBlockImporter supplier
   * @param jsonBlockImporterFactory instance of {@code Function<BesuController, JsonBlockImporter>}
   * @param rlpBlockExporterFactory instance of {@code Function<Blockchain, RlpBlockExporter>}
   * @param runnerBuilder instance of RunnerBuilder
   * @param controllerBuilder instance of BesuController.Builder
   * @param besuPluginContext instance of BesuPluginContextImpl
   * @param environment Environment variables map
   * @param storageService instance of StorageServiceImpl
   * @param securityModuleService instance of SecurityModuleServiceImpl
   * @param permissioningService instance of PermissioningServiceImpl
   * @param privacyPluginService instance of PrivacyPluginServiceImpl
   * @param rpcEndpointServiceImpl instance of RpcEndpointServiceImpl
   * @param transactionSelectionServiceImpl instance of TransactionSelectionServiceImpl
   * @param transactionValidatorServiceImpl instance of TransactionValidatorServiceImpl
   * @param transactionSimulationServiceImpl instance of TransactionSimulationServiceImpl
   * @param blockchainServiceImpl instance of BlockchainServiceImpl
   * @param commandLogger instance of Logger for outputting to the CLI
   */
  @VisibleForTesting
  protected BesuCommand(
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
      final RunnerBuilder runnerBuilder,
      final BesuController.Builder controllerBuilder,
      final BesuPluginContextImpl besuPluginContext,
      final Map<String, String> environment,
      final StorageServiceImpl storageService,
      final SecurityModuleServiceImpl securityModuleService,
      final PermissioningServiceImpl permissioningService,
      final PrivacyPluginServiceImpl privacyPluginService,
      final RpcEndpointServiceImpl rpcEndpointServiceImpl,
      final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
      final TransactionPoolValidatorServiceImpl transactionValidatorServiceImpl,
      final TransactionSimulationServiceImpl transactionSimulationServiceImpl,
      final BlockchainServiceImpl blockchainServiceImpl,
      final Logger commandLogger) {

    this.logger = commandLogger;
    this.rlpBlockImporter = rlpBlockImporter;
    this.rlpBlockExporterFactory = rlpBlockExporterFactory;
    this.jsonBlockImporterFactory = jsonBlockImporterFactory;
    this.runnerBuilder = runnerBuilder;
    this.controllerBuilder = controllerBuilder;
    this.besuPluginContext = besuPluginContext;
    this.environment = environment;
    this.storageService = storageService;
    this.securityModuleService = securityModuleService;
    this.permissioningService = permissioningService;
    this.privacyPluginService = privacyPluginService;
    if (besuPluginContext.getService(BesuConfigurationImpl.class).isPresent()) {
      this.pluginCommonConfiguration =
          besuPluginContext.getService(BesuConfigurationImpl.class).get();
    } else {
      this.pluginCommonConfiguration = new BesuConfigurationImpl();
      besuPluginContext.addService(BesuConfiguration.class, this.pluginCommonConfiguration);
    }
    this.rpcEndpointServiceImpl = rpcEndpointServiceImpl;
    this.transactionSelectionServiceImpl = transactionSelectionServiceImpl;
    this.transactionValidatorServiceImpl = transactionValidatorServiceImpl;
    this.transactionSimulationServiceImpl = transactionSimulationServiceImpl;
    this.blockchainServiceImpl = blockchainServiceImpl;
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
  /**
   * Parses command line arguments and configures the application accordingly.
   *
   * @param resultHandler The strategy to handle the execution result.
   * @param parameterExceptionHandler Handler for exceptions related to command line parameters.
   * @param executionExceptionHandler Handler for exceptions during command execution.
   * @param in The input stream for commands.
   * @param besuComponent The Besu component.
   * @param args The command line arguments.
   * @return The execution result status code.
   */
  public int parse(
      final IExecutionStrategy resultHandler,
      final BesuParameterExceptionHandler parameterExceptionHandler,
      final BesuExecutionExceptionHandler executionExceptionHandler,
      final InputStream in,
      final BesuComponent besuComponent,
      final String... args) {
    if (besuComponent == null) {
      throw new IllegalArgumentException("BesuComponent must be provided");
    }
    this.besuComponent = besuComponent;
    initializeCommandLineSettings(in);

    // Create the execution strategy chain.
    final IExecutionStrategy executeTask = createExecuteTask(resultHandler);
    final IExecutionStrategy pluginRegistrationTask = createPluginRegistrationTask(executeTask);
    final IExecutionStrategy setDefaultValueProviderTask =
        createDefaultValueProviderTask(pluginRegistrationTask);

    // 1- Config default value provider
    // 2- Register plugins
    // 3- Execute command
    return executeCommandLine(
        setDefaultValueProviderTask, parameterExceptionHandler, executionExceptionHandler, args);
  }

  private void initializeCommandLineSettings(final InputStream in) {
    toCommandLine();
    // Automatically adjust the width of usage messages to the terminal width.
    commandLine.getCommandSpec().usageMessage().autoWidth(true);

    handleStableOptions();
    addSubCommands(in);
    registerConverters();
    handleUnstableOptions();
    preparePlugins();
  }

  private IExecutionStrategy createExecuteTask(final IExecutionStrategy nextStep) {
    return parseResult -> {
      commandLine.setExecutionStrategy(nextStep);
      // At this point we don't allow unmatched options since plugins were already registered
      commandLine.setUnmatchedArgumentsAllowed(false);
      return commandLine.execute(parseResult.originalArgs().toArray(new String[0]));
    };
  }

  private IExecutionStrategy createPluginRegistrationTask(final IExecutionStrategy nextStep) {
    return parseResult -> {
      besuPluginContext.initialize(PluginsConfigurationOptions.fromCommandLine(commandLine));
      besuPluginContext.registerPlugins();
      commandLine.setExecutionStrategy(nextStep);
      return commandLine.execute(parseResult.originalArgs().toArray(new String[0]));
    };
  }

  private IExecutionStrategy createDefaultValueProviderTask(final IExecutionStrategy nextStep) {
    return new ConfigDefaultValueProviderStrategy(nextStep, environment);
  }

  /**
   * Executes the command line with the provided execution strategy and exception handlers.
   *
   * @param executionStrategy The execution strategy to use.
   * @param args The command line arguments.
   * @return The execution result status code.
   */
  private int executeCommandLine(
      final IExecutionStrategy executionStrategy,
      final BesuParameterExceptionHandler parameterExceptionHandler,
      final BesuExecutionExceptionHandler executionExceptionHandler,
      final String... args) {
    return commandLine
        .setExecutionStrategy(executionStrategy)
        .setParameterExceptionHandler(parameterExceptionHandler)
        .setExecutionExceptionHandler(executionExceptionHandler)
        // As this happens before the plugins registration and plugins can add options, we must
        // allow unmatched options
        .setUnmatchedArgumentsAllowed(true)
        .execute(args);
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

      if (printPathsAndExit) {
        // Print configured paths requiring read/write permissions to be adjusted
        checkPermissionsAndPrintPaths(besuUserName);
        System.exit(0); // Exit before any services are started
      }

      // set merge config on the basis of genesis config
      setMergeConfigOptions();

      setIgnorableStorageSegments();

      instantiateSignatureAlgorithmFactory();

      logger.info("Starting Besu");

      // Need to create vertx after cmdline has been parsed, such that metricsSystem is configurable
      vertx = createVertx(createVertxOptions(besuComponent.getMetricsSystem()));

      validateOptions();

      configure();

      // If we're not running against a named network, or if version compat protection has been
      // explicitly enabled, perform compatibility check
      VersionMetadata.versionCompatibilityChecks(versionCompatibilityProtection, dataDir());

      configureNativeLibs();
      besuController = buildController();

      besuPluginContext.beforeExternalServices();

      final var runner = buildRunner();
      runner.startExternalServices();

      startPlugins(runner);
      validatePrivacyPluginOptions();
      setReleaseMetrics();
      preSynchronization();

      runner.startEthereumMainLoop();

      besuPluginContext.afterExternalServicesMainLoop();

      runner.awaitStop();

    } catch (final Exception e) {
      logger.error("Failed to start Besu", e);
      throw new ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  private void checkPermissionsAndPrintPaths(final String userName) {
    // Check permissions for the data path
    checkPermissions(dataDir(), userName, false);

    // Check permissions for genesis file
    try {
      if (genesisFile != null) {
        checkPermissions(genesisFile.toPath(), userName, true);
      }
    } catch (Exception e) {
      commandLine
          .getOut()
          .println("Error: Failed checking genesis file: Reason: " + e.getMessage());
    }
  }

  // Helper method to check permissions on a given path
  private void checkPermissions(final Path path, final String besuUser, final boolean readOnly) {
    try {
      // Get the permissions of the file
      // check if besu user is the owner - get owner permissions if yes
      // else, check if besu user and owner are in the same group - if yes, check the group
      // permission
      // otherwise check permissions for others

      // Get the owner of the file or directory
      UserPrincipal owner = Files.getOwner(path);
      boolean hasReadPermission, hasWritePermission;

      // Get file permissions
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);

      // Check if besu is the owner
      if (owner.getName().equals(besuUser)) {
        // Owner permissions
        hasReadPermission = permissions.contains(PosixFilePermission.OWNER_READ);
        hasWritePermission = permissions.contains(PosixFilePermission.OWNER_WRITE);
      } else {
        // Get the group of the file
        // Get POSIX file attributes and then group
        PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
        GroupPrincipal group = attrs.group();

        // Check if besu user belongs to this group
        boolean isMember = isGroupMember(besuUserName, group);

        if (isMember) {
          // Group's permissions
          hasReadPermission = permissions.contains(PosixFilePermission.GROUP_READ);
          hasWritePermission = permissions.contains(PosixFilePermission.GROUP_WRITE);
        } else {
          // Others' permissions
          hasReadPermission = permissions.contains(PosixFilePermission.OTHERS_READ);
          hasWritePermission = permissions.contains(PosixFilePermission.OTHERS_WRITE);
        }
      }

      if (!hasReadPermission || (!readOnly && !hasWritePermission)) {
        String accessType = readOnly ? "READ" : "READ_WRITE";
        commandLine.getOut().println("PERMISSION_CHECK_PATH:" + path + ":" + accessType);
      }
    } catch (Exception e) {
      // Do nothing upon catching an error
      commandLine
          .getOut()
          .println(
              "Error: Failed to check permissions for path: '"
                  + path
                  + "'. Reason: "
                  + e.getMessage());
    }
  }

  private static boolean isGroupMember(final String userName, final GroupPrincipal group)
      throws IOException {
    // Get the groups of the user by executing 'id -Gn username'
    Process process = Runtime.getRuntime().exec(new String[] {"id", "-Gn", userName});
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));

    // Read the output of the command
    String line = reader.readLine();
    boolean isMember = false;
    if (line != null) {
      // Split the groups
      Iterable<String> userGroups = Splitter.on(" ").split(line);
      // Check if any of the user's groups match the file's group

      for (String grp : userGroups) {
        if (grp.equals(group.getName())) {
          isMember = true;
          break;
        }
      }
    }
    return isMember;
  }

  @VisibleForTesting
  void setBesuConfiguration(final BesuConfigurationImpl pluginCommonConfiguration) {
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

    metricCategoryRegistry.addCategories(BesuMetricCategory.class);
    metricCategoryRegistry.addCategories(StandardMetricCategory.class);
    besuPluginContext.addService(MetricCategoryRegistry.class, metricCategoryRegistry);
    besuPluginContext.addService(PermissioningService.class, permissioningService);
    besuPluginContext.addService(PrivacyPluginService.class, privacyPluginService);
    besuPluginContext.addService(RpcEndpointService.class, rpcEndpointServiceImpl);
    besuPluginContext.addService(
        TransactionSelectionService.class, transactionSelectionServiceImpl);
    besuPluginContext.addService(
        TransactionPoolValidatorService.class, transactionValidatorServiceImpl);
    besuPluginContext.addService(
        TransactionSimulationService.class, transactionSimulationServiceImpl);
    besuPluginContext.addService(BlockchainService.class, blockchainServiceImpl);

    // register built-in plugins
    rocksDBPlugin = new RocksDBPlugin();
    rocksDBPlugin.register(besuPluginContext);
    new InMemoryStoragePlugin().register(besuPluginContext);

    // register default security module
    securityModuleService.register(
        DEFAULT_SECURITY_MODULE, Suppliers.memoize(this::defaultSecurityModule));
  }

  private SecurityModule defaultSecurityModule() {
    return new KeyPairSecurityModule(loadKeyPair(nodePrivateKeyFileOption.getNodePrivateKeyFile()));
  }

  /**
   * Load key pair from private key. Visible to be accessed by subcommands.
   *
   * @param nodePrivateKeyFile File containing private key
   * @return KeyPair loaded from private key file
   */
  public KeyPair loadKeyPair(final File nodePrivateKeyFile) {
    return KeyPairUtil.loadKeyPair(resolveNodePrivateKeyFile(nodePrivateKeyFile));
  }

  private void preSynchronization() {
    preSynchronizationTaskRunner.runTasks(besuController);
  }

  private Runner buildRunner() {
    return synchronize(
        besuController,
        p2PDiscoveryConfig.p2pEnabled(),
        p2PDiscoveryConfig.peerDiscoveryEnabled(),
        ethNetworkConfig,
        p2PDiscoveryConfig.p2pHost(),
        p2PDiscoveryConfig.p2pInterface(),
        p2PDiscoveryConfig.p2pPort(),
        graphQLConfiguration,
        jsonRpcConfiguration,
        engineJsonRpcConfiguration,
        webSocketConfiguration,
        jsonRpcIpcConfiguration,
        inProcessRpcConfiguration,
        apiConfigurationSupplier.get(),
        metricsConfiguration,
        permissioningConfiguration,
        staticNodes,
        pidPath);
  }

  private void startPlugins(final Runner runner) {
    blockchainServiceImpl.init(
        besuController.getProtocolContext().getBlockchain(), besuController.getProtocolSchedule());
    transactionSimulationServiceImpl.init(
        besuController.getProtocolContext().getBlockchain(),
        besuController.getTransactionSimulator());
    rpcEndpointServiceImpl.init(runner.getInProcessRpcMethods());

    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState(),
            besuController.getProtocolContext().getBadBlockManager()));
    besuPluginContext.addService(MetricsSystem.class, getMetricsSystem());

    besuPluginContext.addService(BlockchainService.class, blockchainServiceImpl);

    besuPluginContext.addService(
        SynchronizationService.class,
        new SynchronizationServiceImpl(
            besuController.getSynchronizer(),
            besuController.getProtocolContext(),
            besuController.getProtocolSchedule(),
            besuController.getSyncState(),
            besuController.getProtocolContext().getWorldStateArchive()));

    besuPluginContext.addService(P2PService.class, new P2PServiceImpl(runner.getP2PNetwork()));

    besuPluginContext.addService(
        TransactionPoolService.class,
        new TransactionPoolServiceImpl(besuController.getTransactionPool()));

    besuPluginContext.addService(
        RlpConverterService.class,
        new RlpConverterServiceImpl(besuController.getProtocolSchedule()));

    besuPluginContext.addService(
        TraceService.class,
        new TraceServiceImpl(
            new BlockchainQueries(
                besuController.getProtocolSchedule(),
                besuController.getProtocolContext().getBlockchain(),
                besuController.getProtocolContext().getWorldStateArchive(),
                miningParametersSupplier.get()),
            besuController.getProtocolSchedule()));

    besuPluginContext.addService(
        MiningService.class, new MiningServiceImpl(besuController.getMiningCoordinator()));

    besuPluginContext.addService(
        BlockSimulationService.class,
        new BlockSimulatorServiceImpl(
            besuController.getProtocolContext().getWorldStateArchive(),
            miningParametersSupplier.get(),
            besuController.getTransactionSimulator(),
            besuController.getProtocolSchedule(),
            besuController.getProtocolContext().getBlockchain()));

    besuController.getAdditionalPluginServices().appendPluginServices(besuPluginContext);
    besuPluginContext.startPlugins();
  }

  private void validatePrivacyPluginOptions() {
    // plugins do not 'wire up' until start has been called
    // consequently you can only do some configuration checks
    // after start has been called on plugins

    if (Boolean.TRUE.equals(privacyOptionGroup.isPrivacyEnabled)) {
      logger.warn(
          "--Xprivacy-plugin-enabled and related options are " + PRIVACY_DEPRECATION_PREFIX);

      if (privacyOptionGroup.privateMarkerTransactionSigningKeyPath != null
          && privacyPluginService != null
          && privacyPluginService.getPrivateMarkerTransactionFactory() != null) {
        throw new ParameterException(
            commandLine,
            "--privacy-marker-transaction-signing-key-file can not be used in conjunction with a plugin that specifies a PrivateMarkerTransactionFactory");
      }

      if (Wei.ZERO.compareTo(miningParametersSupplier.get().getMinTransactionGasPrice()) < 0
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
          && privacyOptionGroup.isFlexiblePrivacyGroupsEnabled) {
        throw new ParameterException(
            commandLine, "Privacy Plugin can not be used with flexible privacy groups");
      }
    }
  }

  private void setReleaseMetrics() {
    besuComponent
        .getMetricsSystem()
        .createLabelledSuppliedGauge(
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
        && AbstractAltBnPrecompiledContract.maybeEnableNative()) {
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
        && SignatureAlgorithmFactory.getInstance().maybeEnableNative()) {
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

    if (genesisConfigOptionsSupplier.get().getCancunTime().isPresent()
        || genesisConfigOptionsSupplier.get().getCancunEOFTime().isPresent()
        || genesisConfigOptionsSupplier.get().getPragueTime().isPresent()
        || genesisConfigOptionsSupplier.get().getOsakaTime().isPresent()) {
      if (kzgTrustedSetupFile != null) {
        KZGPointEvalPrecompiledContract.init(kzgTrustedSetupFile);
      } else {
        KZGPointEvalPrecompiledContract.init();
      }
    } else if (kzgTrustedSetupFile != null) {
      throw new ParameterException(
          this.commandLine,
          "--kzg-trusted-setup can only be specified on networks with data blobs enabled");
    }
  }

  private void validateOptions() {
    issueOptionWarnings();
    validateP2POptions();
    validateMiningParams();
    validateNatParams();
    validateNetStatsParams();
    validateDnsOptionsParams();
    ensureValidPeerBoundParams();
    validateRpcOptionsParams();
    validateRpcWsOptions();
    validateChainDataPruningParams();
    validatePostMergeCheckpointBlockRequirements();
    validateTransactionPoolOptions();
    validateDataStorageOptions();
    validateGraphQlOptions();
    validateConsensusSyncCompatibilityOptions();
    validatePluginOptions();
  }

  private void validateConsensusSyncCompatibilityOptions() {
    // snap and checkpoint are experimental for BFT
    if ((genesisConfigOptionsSupplier.get().isIbftLegacy()
            || genesisConfigOptionsSupplier.get().isIbft2()
            || genesisConfigOptionsSupplier.get().isQbft())
        && !unstableSynchronizerOptions.isSnapSyncBftEnabled()) {
      final String errorSuffix = "can't be used with BFT networks";
      if (SyncMode.CHECKPOINT.equals(syncMode)) {
        throw new ParameterException(
            commandLine, String.format("%s %s", "Checkpoint sync", errorSuffix));
      }
      if (syncMode == SyncMode.SNAP) {
        throw new ParameterException(commandLine, String.format("%s %s", "Snap sync", errorSuffix));
      }
    }
  }

  private void validatePluginOptions() {
    pluginsConfigurationOptions.validate(commandLine);
  }

  private void validateApiOptions() {
    apiConfigurationOptions.validate(commandLine, logger);
  }

  private void validateTransactionPoolOptions() {
    transactionPoolOptions.validate(commandLine, genesisConfigOptionsSupplier.get());
  }

  private void validateDataStorageOptions() {
    dataStorageOptions.validate(commandLine);
  }

  private void validateMiningParams() {
    miningOptions.validate(
        commandLine, genesisConfigOptionsSupplier.get(), isMergeEnabled(), logger);
  }

  private void validateP2POptions() {
    p2PDiscoveryOptions.validate(commandLine, getNetworkInterfaceChecker());
  }

  /**
   * Returns a network interface checker that can be used to validate P2P options.
   *
   * @return A {@link P2PDiscoveryOptions.NetworkInterfaceChecker} that checks if a network
   *     interface is available.
   */
  protected P2PDiscoveryOptions.NetworkInterfaceChecker getNetworkInterfaceChecker() {
    return NetworkUtility::isNetworkInterfaceAvailable;
  }

  private void validateGraphQlOptions() {
    graphQlOptions.validate(logger, commandLine);
  }

  @SuppressWarnings("ConstantConditions")
  private void validateNatParams() {
    if (natMethod.equals(NatMethod.KUBERNETES)) {
      logger.warn("Kubernetes NAT method is deprecated. Please use Docker or UPNP");
    }
    if (!unstableNatOptions.getNatManagerServiceName().equals(DEFAULT_BESU_SERVICE_NAME_FILTER)) {
      logger.warn(
          "`--Xnat-kube-service-name` and Kubernetes NAT method are deprecated. Please use Docker or UPNP");
    }
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

  private void ensureValidPeerBoundParams() {
    maxPeers = p2PDiscoveryOptions.maxPeers;
    final Boolean isLimitRemoteWireConnectionsEnabled =
        p2PDiscoveryOptions.isLimitRemoteWireConnectionsEnabled;
    if (isLimitRemoteWireConnectionsEnabled) {
      final float fraction =
          Fraction.fromPercentage(p2PDiscoveryOptions.maxRemoteConnectionsPercentage).getValue();
      checkState(
          fraction >= 0.0 && fraction <= 1.0,
          "Fraction of remote connections allowed must be between 0.0 and 1.0 (inclusive).");
      maxRemoteInitiatedPeers = Math.round(fraction * maxPeers);
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
    jsonRpcHttpOptions.validate(logger, commandLine, configuredApis);
  }

  private void validateRpcWsOptions() {
    final Predicate<String> configuredApis =
        apiName ->
            Arrays.stream(RpcApis.values())
                    .anyMatch(builtInApi -> apiName.equals(builtInApi.name()))
                || rpcEndpointServiceImpl.hasNamespace(apiName);
    rpcWebsocketOptions.validate(logger, commandLine, configuredApis);
  }

  private void validateChainDataPruningParams() {
    if (unstableChainPruningOptions.getChainDataPruningEnabled()
        && unstableChainPruningOptions.getChainDataPruningBlocksRetained()
            < unstableChainPruningOptions.getChainDataPruningBlocksRetainedLimit()) {
      throw new ParameterException(
          this.commandLine,
          "--Xchain-pruning-blocks-retained must be >= "
              + unstableChainPruningOptions.getChainDataPruningBlocksRetainedLimit());
    }
  }

  private GenesisConfig readGenesisConfig() {
    GenesisConfig effectiveGenesisFile;
    effectiveGenesisFile =
        network.equals(EPHEMERY)
            ? EphemeryGenesisUpdater.updateGenesis(genesisConfigOverrides)
            : genesisFile != null
                ? GenesisConfig.fromSource(genesisConfigSource(genesisFile))
                : GenesisConfig.fromResource(
                    Optional.ofNullable(network).orElse(MAINNET).getGenesisFile());
    return effectiveGenesisFile.withOverrides(genesisConfigOverrides);
  }

  private GenesisConfigOptions readGenesisConfigOptions() {
    try {
      return genesisConfigSupplier.get().getConfigOptions();
    } catch (final Exception e) {
      throw new ParameterException(
          this.commandLine, "Unable to load genesis file. " + e.getCause());
    }
  }

  private void issueOptionWarnings() {

    // Check that P2P options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2PDiscoveryOptions.p2pEnabled,
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

    if (SyncMode.FAST == syncMode) {
      logger.warn("FAST sync is deprecated. Recommend using SNAP sync instead.");
    }

    if (SyncMode.isFullSync(getDefaultSyncModeIfNotSet())
        && isOptionSet(commandLine, "--sync-min-peers")) {
      logger.warn("--sync-min-peers is ignored in FULL sync-mode");
    }

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "--Xcheckpoint-post-merge-enabled can only be used with CHECKPOINT sync-mode",
        getDefaultSyncModeIfNotSet() == SyncMode.CHECKPOINT,
        singletonList("--Xcheckpoint-post-merge-enabled"));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "--Xsnapsync-synchronizer-flat option can only be used when --Xbonsai-full-flat-db-enabled is true",
        dataStorageOptions
            .toDomainObject()
            .getDiffBasedSubStorageConfiguration()
            .getUnstable()
            .getFullFlatDbEnabled(),
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
  }

  private void configure() throws Exception {
    p2PDiscoveryConfig = p2PDiscoveryOptions.toDomainObject();
    engineRPCConfig = engineRPCOptions.toDomainObject();
    checkPortClash();
    checkIfRequiredPortsAreAvailable();
    syncMode = getDefaultSyncModeIfNotSet();
    versionCompatibilityProtection = getDefaultVersionCompatibilityProtectionIfNotSet();

    ethNetworkConfig = updateNetworkConfig(network);

    jsonRpcConfiguration =
        jsonRpcHttpOptions.jsonRpcConfiguration(
            hostsAllowlist, p2PDiscoveryOptions.p2pHost, unstableRPCOptions.getHttpTimeoutSec());
    if (isEngineApiEnabled()) {
      engineJsonRpcConfiguration = createEngineJsonRpcConfiguration();
    }
    graphQLConfiguration =
        graphQlOptions.graphQLConfiguration(
            hostsAllowlist, p2PDiscoveryOptions.p2pHost, unstableRPCOptions.getHttpTimeoutSec());

    webSocketConfiguration =
        rpcWebsocketOptions.webSocketConfiguration(
            hostsAllowlist, p2PDiscoveryConfig.p2pHost(), unstableRPCOptions.getWsTimeoutSec());
    jsonRpcIpcConfiguration =
        jsonRpcIpcConfiguration(
            unstableIpcOptions.isEnabled(),
            unstableIpcOptions.getIpcPath(),
            unstableIpcOptions.getRpcIpcApis());
    inProcessRpcConfiguration = inProcessRpcOptions.toDomainObject();
    dataStorageConfiguration = getDataStorageConfiguration();

    permissioningConfiguration = permissioningConfiguration();
    staticNodes = loadStaticNodes();

    final List<EnodeURL> enodeURIs = ethNetworkConfig.bootNodes();
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

  private Optional<PermissioningConfiguration> permissioningConfiguration() throws Exception {
    return permissionsOptions.permissioningConfiguration(
        jsonRpcHttpOptions,
        rpcWebsocketOptions,
        getEnodeDnsConfiguration(),
        dataDir(),
        logger,
        commandLine);
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

  /**
   * Builds BesuController
   *
   * @return instance of BesuController
   */
  public BesuController buildController() {
    try {
      return setupControllerBuilder().build();
    } catch (final Exception e) {
      throw new ExecutionException(this.commandLine, e.getMessage(), e);
    }
  }

  /**
   * Builds BesuControllerBuilder which can be used to build BesuController
   *
   * @return instance of BesuControllerBuilder
   */
  public BesuControllerBuilder setupControllerBuilder() {
    pluginCommonConfiguration
        .init(dataDir(), dataDir().resolve(DATABASE_PATH), getDataStorageConfiguration())
        .withMiningParameters(miningParametersSupplier.get())
        .withJsonRpcHttpOptions(jsonRpcHttpOptions);
    final KeyValueStorageProvider storageProvider = keyValueStorageProvider(keyValueStorageName);
    BesuControllerBuilder besuControllerBuilder =
        controllerBuilder
            .fromEthNetworkConfig(updateNetworkConfig(network), getDefaultSyncModeIfNotSet())
            .synchronizerConfiguration(buildSyncConfig())
            .ethProtocolConfiguration(unstableEthProtocolOptions.toDomainObject())
            .networkConfiguration(unstableNetworkingOptions.toDomainObject())
            .dataDirectory(dataDir())
            .dataStorageConfiguration(getDataStorageConfiguration())
            .miningParameters(miningParametersSupplier.get())
            .transactionPoolConfiguration(buildTransactionPoolConfiguration())
            .nodeKey(new NodeKey(securityModule()))
            .metricsSystem((ObservableMetricsSystem) besuComponent.getMetricsSystem())
            .messagePermissioningProviders(permissioningService.getMessagePermissioningProviders())
            .privacyParameters(privacyParameters())
            .clock(Clock.systemUTC())
            .isRevertReasonEnabled(isRevertReasonEnabled)
            .storageProvider(storageProvider)
            .gasLimitCalculator(
                miningParametersSupplier.get().getTargetGasLimit().isPresent()
                    ? new FrontierTargetingGasLimitCalculator()
                    : GasLimitCalculator.constant())
            .requiredBlocks(requiredBlocks)
            .reorgLoggingThreshold(reorgLoggingThreshold)
            .evmConfiguration(unstableEvmOptions.toDomainObject())
            .maxPeers(p2PDiscoveryOptions.maxPeers)
            .maxRemotelyInitiatedPeers(maxRemoteInitiatedPeers)
            .randomPeerPriority(p2PDiscoveryOptions.randomPeerPriority)
            .chainPruningConfiguration(unstableChainPruningOptions.toDomainObject())
            .cacheLastBlocks(numberOfblocksToCache)
            .genesisStateHashCacheEnabled(genesisStateHashCacheEnabled)
            .apiConfiguration(apiConfigurationSupplier.get())
            .besuComponent(besuComponent);
    if (DataStorageFormat.BONSAI.equals(getDataStorageConfiguration().getDataStorageFormat())) {
      final DiffBasedSubStorageConfiguration subStorageConfiguration =
          getDataStorageConfiguration().getDiffBasedSubStorageConfiguration();
      besuControllerBuilder.isParallelTxProcessingEnabled(
          subStorageConfiguration.getUnstable().isParallelTxProcessingEnabled());
    }
    return besuControllerBuilder;
  }

  private JsonRpcConfiguration createEngineJsonRpcConfiguration() {
    jsonRpcHttpOptions.checkDependencies(logger, commandLine);
    final JsonRpcConfiguration engineConfig =
        jsonRpcHttpOptions.jsonRpcConfiguration(
            engineRPCConfig.engineHostsAllowlist(),
            p2PDiscoveryConfig.p2pHost(),
            unstableRPCOptions.getWsTimeoutSec());
    engineConfig.setPort(engineRPCConfig.engineRpcPort());
    engineConfig.setRpcApis(Arrays.asList("ENGINE", "ETH"));
    engineConfig.setEnabled(isEngineApiEnabled());
    if (!engineRPCConfig.isEngineAuthDisabled()) {
      engineConfig.setAuthenticationEnabled(true);
      engineConfig.setAuthenticationAlgorithm(JwtAlgorithm.HS256);
      if (Objects.nonNull(engineRPCConfig.engineJwtKeyFile())
          && java.nio.file.Files.exists(engineRPCConfig.engineJwtKeyFile())) {
        engineConfig.setAuthenticationPublicKeyFile(engineRPCConfig.engineJwtKeyFile().toFile());
      } else {
        logger.warn(
            "Engine API authentication enabled without key file. Expect ephemeral jwt.hex file in datadir");
      }
    }
    return engineConfig;
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

  /**
   * Metrics Configuration for Besu
   *
   * @return instance of MetricsConfiguration.
   */
  public MetricsConfiguration metricsConfiguration() {
    if (metricsOptions.getMetricsEnabled() && metricsOptions.getMetricsPushEnabled()) {
      throw new ParameterException(
          this.commandLine,
          "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
              + "time.  Please refer to CLI reference for more details about this constraint.");
    }

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-enabled",
        !metricsOptions.getMetricsEnabled(),
        asList("--metrics-host", "--metrics-port"));

    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--metrics-push-enabled",
        !metricsOptions.getMetricsPushEnabled(),
        asList(
            "--metrics-push-host",
            "--metrics-push-port",
            "--metrics-push-interval",
            "--metrics-push-prometheus-job"));

    metricsOptions.setMetricCategoryRegistry(metricCategoryRegistry);

    metricsOptions.validate(commandLine);

    final MetricsConfiguration.Builder metricsConfigurationBuilder =
        metricsOptions.toDomainObject();
    metricsConfigurationBuilder
        .host(
            Strings.isNullOrEmpty(metricsOptions.getMetricsHost())
                ? p2PDiscoveryOptions.p2pHost
                : metricsOptions.getMetricsHost())
        .pushHost(
            Strings.isNullOrEmpty(metricsOptions.getMetricsPushHost())
                ? p2PDiscoveryOptions.autoDiscoverDefaultIP().getHostAddress()
                : metricsOptions.getMetricsPushHost())
        .hostsAllowlist(hostsAllowlist);
    final var metricsConfiguration = metricsConfigurationBuilder.build();
    metricCategoryRegistry.setMetricsConfiguration(metricsConfiguration);
    return metricsConfiguration;
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
      logger.warn("--privacy-enabled and related options are " + PRIVACY_DEPRECATION_PREFIX);
      final String errorSuffix = "cannot be enabled with privacy.";
      if (syncMode == SyncMode.FAST) {
        throw new ParameterException(commandLine, String.format("%s %s", "Fast sync", errorSuffix));
      }
      if (syncMode == SyncMode.SNAP) {
        throw new ParameterException(commandLine, String.format("%s %s", "Snap sync", errorSuffix));
      }
      if (syncMode == SyncMode.CHECKPOINT) {
        throw new ParameterException(
            commandLine, String.format("%s %s", "Checkpoint sync", errorSuffix));
      }
      if (getDataStorageConfiguration().getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
        throw new ParameterException(commandLine, String.format("%s %s", "Bonsai", errorSuffix));
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
          privacyOptionGroup.isFlexiblePrivacyGroupsEnabled);
      privacyParametersBuilder.setPrivacyPluginEnabled(
          unstablePrivacyPluginOptions.isPrivacyPluginEnabled());
      privacyParametersBuilder.setPrivateNonceAlwaysIncrementsEnabled(
          privacyOptionGroup.isPrivateNonceAlwaysIncrementsEnabled);

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
    return jsonRpcHttpOptions.getRpcHttpApis().contains(RpcApis.EEA.name())
        || rpcWebsocketOptions.getRpcWsApis().contains(RpcApis.EEA.name())
        || jsonRpcHttpOptions.getRpcHttpApis().contains(RpcApis.PRIV.name())
        || rpcWebsocketOptions.getRpcWsApis().contains(RpcApis.PRIV.name());
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

  private SynchronizerConfiguration buildSyncConfig() {
    return unstableSynchronizerOptions
        .toDomainObject()
        .syncMode(syncMode)
        .syncMinimumPeerCount(syncMinPeerCount)
        .build();
  }

  private TransactionPoolConfiguration buildTransactionPoolConfiguration() {
    transactionPoolOptions.setPluginTransactionValidatorService(transactionValidatorServiceImpl);
    final var txPoolConf = transactionPoolOptions.toDomainObject();
    final var txPoolConfBuilder =
        ImmutableTransactionPoolConfiguration.builder()
            .from(txPoolConf)
            .saveFile((dataPath.resolve(txPoolConf.getSaveFile().getPath()).toFile()));

    if (genesisConfigOptionsSupplier.get().isZeroBaseFee()) {
      logger.warn(
          "Forcing price bump for transaction replacement to 0, since we are on a zero basefee network");
      txPoolConfBuilder.priceBump(Percentage.ZERO);
    }

    if (miningParametersSupplier.get().getMinTransactionGasPrice().equals(Wei.ZERO)
        && !transactionPoolOptions.isPriceBumpSet(commandLine)) {
      logger.warn(
          "Forcing price bump for transaction replacement to 0, since min-gas-price is set to 0");
      txPoolConfBuilder.priceBump(Percentage.ZERO);
    }

    if (miningParametersSupplier
        .get()
        .getMinTransactionGasPrice()
        .lessThan(txPoolConf.getMinGasPrice())) {
      if (transactionPoolOptions.isMinGasPriceSet(commandLine)) {
        throw new ParameterException(
            commandLine, "tx-pool-min-gas-price cannot be greater than the value of min-gas-price");

      } else {
        // for backward compatibility, if tx-pool-min-gas-price is not set, we adjust its value
        // to be the same as min-gas-price, so the behavior is as before this change, and we notify
        // the user of the change
        logger.warn(
            "Forcing tx-pool-min-gas-price="
                + miningParametersSupplier.get().getMinTransactionGasPrice().toDecimalString()
                + ", since it cannot be greater than the value of min-gas-price");
        txPoolConfBuilder.minGasPrice(miningParametersSupplier.get().getMinTransactionGasPrice());
      }
    }

    return txPoolConfBuilder.build();
  }

  private MiningConfiguration getMiningParameters() {
    miningOptions.setTransactionSelectionService(transactionSelectionServiceImpl);
    final var miningParameters = miningOptions.toDomainObject();
    getGenesisBlockPeriodSeconds(genesisConfigOptionsSupplier.get())
        .ifPresent(miningParameters::setBlockPeriodSeconds);
    initMiningParametersMetrics(miningParameters);
    // if network = holesky, set targetGasLimit to 36,000,000 unless otherwise specified
    if (miningParameters.getTargetGasLimit().isEmpty() && NetworkName.HOLESKY.equals(network)) {
      logger.info(
          "Setting target gas limit for holesky: {}",
          MiningConfiguration.DEFAULT_TARGET_GAS_LIMIT_HOLESKY);
      miningParameters.setTargetGasLimit(MiningConfiguration.DEFAULT_TARGET_GAS_LIMIT_HOLESKY);
    }
    return miningParameters;
  }

  private ApiConfiguration getApiConfiguration() {
    validateApiOptions();
    return apiConfigurationOptions.apiConfiguration();
  }

  /**
   * Get the data storage configuration
   *
   * @return the data storage configuration
   */
  public DataStorageConfiguration getDataStorageConfiguration() {
    if (dataStorageConfiguration == null) {
      dataStorageConfiguration = dataStorageOptions.toDomainObject();
    }

    if (SyncMode.FULL.equals(getDefaultSyncModeIfNotSet())
        && DataStorageFormat.BONSAI.equals(dataStorageConfiguration.getDataStorageFormat())) {
      final DiffBasedSubStorageConfiguration diffBasedSubStorageConfiguration =
          dataStorageConfiguration.getDiffBasedSubStorageConfiguration();
      if (diffBasedSubStorageConfiguration.getLimitTrieLogsEnabled()) {
        if (CommandLineUtils.isOptionSet(
            commandLine, DiffBasedSubStorageOptions.LIMIT_TRIE_LOGS_ENABLED)) {
          throw new ParameterException(
              commandLine,
              String.format(
                  "Cannot enable %s with --sync-mode=%s and --data-storage-format=%s. You must set %s or use a different sync-mode",
                  DiffBasedSubStorageOptions.LIMIT_TRIE_LOGS_ENABLED,
                  SyncMode.FULL,
                  DataStorageFormat.BONSAI,
                  DiffBasedSubStorageOptions.LIMIT_TRIE_LOGS_ENABLED + "=false"));
        }

        dataStorageConfiguration =
            ImmutableDataStorageConfiguration.copyOf(dataStorageConfiguration)
                .withDiffBasedSubStorageConfiguration(
                    ImmutableDiffBasedSubStorageConfiguration.copyOf(
                            dataStorageConfiguration.getDiffBasedSubStorageConfiguration())
                        .withLimitTrieLogsEnabled(false));
        logger.warn(
            "Forcing {}, since it cannot be enabled with --sync-mode={} and --data-storage-format={}.",
            DiffBasedSubStorageOptions.LIMIT_TRIE_LOGS_ENABLED + "=false",
            SyncMode.FULL,
            DataStorageFormat.BONSAI);
      }
    }
    return dataStorageConfiguration;
  }

  private void initMiningParametersMetrics(final MiningConfiguration miningConfiguration) {
    new MiningParametersMetrics(getMetricsSystem(), miningConfiguration);
  }

  private OptionalInt getGenesisBlockPeriodSeconds(
      final GenesisConfigOptions genesisConfigOptions) {
    if (genesisConfigOptions.isClique()) {
      return OptionalInt.of(genesisConfigOptions.getCliqueConfigOptions().getBlockPeriodSeconds());
    }

    if (genesisConfigOptions.isIbft2()) {
      return OptionalInt.of(genesisConfigOptions.getBftConfigOptions().getBlockPeriodSeconds());
    }

    if (genesisConfigOptions.isQbft()) {
      return OptionalInt.of(genesisConfigOptions.getQbftConfigOptions().getBlockPeriodSeconds());
    }

    return OptionalInt.empty();
  }

  // Blockchain synchronization from peers.
  private Runner synchronize(
      final BesuController controller,
      final boolean p2pEnabled,
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
      final InProcessRpcConfiguration inProcessRpcConfiguration,
      final ApiConfiguration apiConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Collection<EnodeURL> staticNodes,
      final Path pidPath) {

    checkNotNull(runnerBuilder);

    final Runner runner =
        runnerBuilder
            .vertx(vertx)
            .besuController(controller)
            .p2pEnabled(p2pEnabled)
            .natMethod(natMethod)
            .natManagerServiceName(unstableNatOptions.getNatManagerServiceName())
            .natMethodFallbackEnabled(unstableNatOptions.getNatMethodFallbackEnabled())
            .discoveryEnabled(peerDiscoveryEnabled)
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
            .inProcessRpcConfiguration(inProcessRpcConfiguration)
            .apiConfiguration(apiConfiguration)
            .pidPath(pidPath)
            .dataDir(dataDir())
            .bannedNodeIds(p2PDiscoveryConfig.bannedNodeIds())
            .metricsSystem((ObservableMetricsSystem) besuComponent.getMetricsSystem())
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
            .allowedSubnets(p2PDiscoveryConfig.allowedSubnets())
            .poaDiscoveryRetryBootnodes(p2PDiscoveryConfig.poaDiscoveryRetryBootnodes())
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
            new io.vertx.core.metrics.MetricsOptions()
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

      if (networkId == null) {
        // If no chain id is found in the genesis, use mainnet network id
        try {
          builder.setNetworkId(
              genesisConfigOptionsSupplier
                  .get()
                  .getChainId()
                  .orElse(EthNetworkConfig.getNetworkConfig(MAINNET).networkId()));
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

      if (p2PDiscoveryOptions.bootNodes == null) {
        builder.setBootNodes(new ArrayList<>());
      }
      builder.setDnsDiscoveryUrl(null);
    }

    builder.setGenesisConfig(genesisConfigSupplier.get());

    if (networkId != null) {
      builder.setNetworkId(networkId);
    }
    // ChainId update is required for Ephemery network
    if (network.equals(EPHEMERY)) {
      String chainId = genesisConfigOverrides.get("chainId");
      builder.setNetworkId(new BigInteger(chainId));
    }
    if (p2PDiscoveryOptions.discoveryDnsUrl != null) {
      builder.setDnsDiscoveryUrl(p2PDiscoveryOptions.discoveryDnsUrl);
    } else {
      final Optional<String> discoveryDnsUrlFromGenesis =
          genesisConfigOptionsSupplier.get().getDiscoveryOptions().getDiscoveryDnsUrl();
      discoveryDnsUrlFromGenesis.ifPresent(builder::setDnsDiscoveryUrl);
    }

    List<EnodeURL> listBootNodes = null;
    if (p2PDiscoveryOptions.bootNodes != null) {
      try {
        listBootNodes = buildEnodes(p2PDiscoveryOptions.bootNodes, getEnodeDnsConfiguration());
      } catch (final IllegalArgumentException e) {
        throw new ParameterException(commandLine, e.getMessage());
      }
    } else {
      final Optional<List<String>> bootNodesFromGenesis =
          genesisConfigOptionsSupplier.get().getDiscoveryOptions().getBootNodes();
      if (bootNodesFromGenesis.isPresent()) {
        listBootNodes = buildEnodes(bootNodesFromGenesis.get(), getEnodeDnsConfiguration());
      }
    }
    if (listBootNodes != null) {
      if (!p2PDiscoveryOptions.peerDiscoveryEnabled) {
        logger.warn("Discovery disabled: bootnodes will be ignored.");
      }
      DiscoveryConfiguration.assertValidBootnodes(listBootNodes);
      builder.setBootNodes(listBootNodes);
    }
    return builder.build();
  }

  private URL genesisConfigSource(final File genesisFile) {
    try {
      return genesisFile.toURI().toURL();
    } catch (final IOException e) {
      throw new ParameterException(
          this.commandLine, String.format("Unable to load genesis URL %s.", genesisFile), e);
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

  /**
   * Metrics System used by Besu
   *
   * @return Instance of MetricsSystem
   */
  public MetricsSystem getMetricsSystem() {
    return besuComponent.getMetricsSystem();
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
              if (port.equals(p2PDiscoveryConfig.p2pPort())
                  && (NetworkUtility.isPortUnavailableForTcp(port)
                      || NetworkUtility.isPortUnavailableForUdp(port))) {
                unavailablePorts.add(port);
              }
              if (!port.equals(p2PDiscoveryConfig.p2pPort())
                  && NetworkUtility.isPortUnavailableForTcp(port)) {
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
    addPortIfEnabled(effectivePorts, p2PDiscoveryOptions.p2pPort, p2PDiscoveryOptions.p2pEnabled);
    addPortIfEnabled(
        effectivePorts, graphQlOptions.getGraphQLHttpPort(), graphQlOptions.isGraphQLHttpEnabled());
    addPortIfEnabled(
        effectivePorts, jsonRpcHttpOptions.getRpcHttpPort(), jsonRpcHttpOptions.isRpcHttpEnabled());
    addPortIfEnabled(
        effectivePorts, rpcWebsocketOptions.getRpcWsPort(), rpcWebsocketOptions.isRpcWsEnabled());
    addPortIfEnabled(effectivePorts, engineRPCConfig.engineRpcPort(), isEngineApiEnabled());
    addPortIfEnabled(
        effectivePorts, metricsOptions.getMetricsPort(), metricsOptions.getMetricsEnabled());
    addPortIfEnabled(
        effectivePorts,
        miningParametersSupplier.get().getStratumPort(),
        miningParametersSupplier.get().isStratumMiningEnabled());
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

  /**
   * Returns the flag indicating that version compatibility checks will be made.
   *
   * @return true if compatibility checks should be made, otherwise false
   */
  @VisibleForTesting
  public Boolean getVersionCompatibilityProtection() {
    return versionCompatibilityProtection;
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
          "Invalid genesis file configuration for ecCurve. " + e.getMessage());
    }
  }

  private Optional<String> getEcCurveFromGenesisFile() {
    if (genesisFile == null) {
      return Optional.empty();
    }
    return genesisConfigOptionsSupplier.get().getEcCurve();
  }

  /**
   * Return the genesis config options
   *
   * @return the genesis config options
   */
  protected GenesisConfigOptions getGenesisConfigOptions() {
    return genesisConfigOptionsSupplier.get();
  }

  private void setMergeConfigOptions() {
    MergeConfiguration.setMergeEnabled(
        genesisConfigOptionsSupplier.get().getTerminalTotalDifficulty().isPresent());
  }

  /** Set ignorable segments in RocksDB Storage Provider plugin. */
  public void setIgnorableStorageSegments() {
    if (!unstableChainPruningOptions.getChainDataPruningEnabled()) {
      rocksDBPlugin.addIgnorableSegmentIdentifier(KeyValueSegmentIdentifier.CHAIN_PRUNER_STATE);
    }
  }

  private void validatePostMergeCheckpointBlockRequirements() {
    final SynchronizerConfiguration synchronizerConfiguration =
        unstableSynchronizerOptions.toDomainObject().build();
    final Optional<UInt256> terminalTotalDifficulty =
        genesisConfigOptionsSupplier.get().getTerminalTotalDifficulty();
    final CheckpointConfigOptions checkpointConfigOptions =
        genesisConfigOptionsSupplier.get().getCheckpointOptions();
    if (synchronizerConfiguration.isCheckpointPostMergeEnabled()) {
      if (!checkpointConfigOptions.isValid()) {
        throw new InvalidConfigurationException(
            "PoS checkpoint sync requires a checkpoint block configured in the genesis file");
      }
      terminalTotalDifficulty.ifPresentOrElse(
          ttd -> {
            if (UInt256.fromHexString(checkpointConfigOptions.getTotalDifficulty().get())
                    .equals(UInt256.ZERO)
                && ttd.equals(UInt256.ZERO)) {
              throw new InvalidConfigurationException(
                  "PoS checkpoint sync can't be used with TTD = 0 and checkpoint totalDifficulty = 0");
            }
            if (UInt256.fromHexString(checkpointConfigOptions.getTotalDifficulty().get())
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
    return MergeConfiguration.isMergeEnabled();
  }

  private boolean isEngineApiEnabled() {
    return engineRPCConfig.overrideEngineRpcEnabled() || isMergeEnabled();
  }

  private SyncMode getDefaultSyncModeIfNotSet() {
    return Optional.ofNullable(syncMode)
        .orElse(
            genesisFile == null
                    && !privacyOptionGroup.isPrivacyEnabled
                    && Optional.ofNullable(network).map(NetworkName::canSnapSync).orElse(false)
                ? SyncMode.SNAP
                : SyncMode.FULL);
  }

  private Boolean getDefaultVersionCompatibilityProtectionIfNotSet() {
    // Version compatibility protection is enabled by default for non-named networks
    return Optional.ofNullable(versionCompatibilityProtection)
        // if we have a specific genesis file or custom network id, we are not using a named network
        .orElse(genesisFile != null || networkId != null);
  }

  private String generateConfigurationOverview() {
    final ConfigurationOverviewBuilder builder = new ConfigurationOverviewBuilder(logger);

    if (environment != null) {
      builder.setEnvironment(environment);
    }

    if (network != null) {
      builder.setNetwork(network.normalize());
    }

    if (profile != null) {
      builder.setProfile(profile);
    }

    builder.setHasCustomGenesis(genesisFile != null);
    if (genesisFile != null) {
      builder.setCustomGenesis(genesisFile.getAbsolutePath());
    }
    builder.setNetworkId(ethNetworkConfig.networkId());

    builder
        .setDataStorage(dataStorageOptions.normalizeDataStorageFormat())
        .setSyncMode(syncMode.normalize())
        .setSyncMinPeers(syncMinPeerCount);

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

    if (DataStorageFormat.BONSAI.equals(getDataStorageConfiguration().getDataStorageFormat())) {
      final DiffBasedSubStorageConfiguration subStorageConfiguration =
          getDataStorageConfiguration().getDiffBasedSubStorageConfiguration();
      if (subStorageConfiguration.getLimitTrieLogsEnabled()) {
        builder.setLimitTrieLogsEnabled();
        builder.setTrieLogRetentionLimit(subStorageConfiguration.getMaxLayersToLoad());
        builder.setTrieLogsPruningWindowSize(subStorageConfiguration.getTrieLogPruningWindowSize());
      }
    }

    builder.setSnapServerEnabled(this.unstableSynchronizerOptions.isSnapsyncServerEnabled());
    builder.setSnapSyncBftEnabled(this.unstableSynchronizerOptions.isSnapSyncBftEnabled());

    builder.setTxPoolImplementation(buildTransactionPoolConfiguration().getTxPoolImplementation());
    builder.setWorldStateUpdateMode(unstableEvmOptions.toDomainObject().worldUpdaterMode());

    builder.setPluginContext(this.besuPluginContext);

    return builder.build();
  }

  /**
   * 2 Returns the plugin context.
   *
   * @return the plugin context.
   */
  public BesuPluginContextImpl getBesuPluginContext() {
    return besuPluginContext;
  }

  /**
   * Returns the metrics options
   *
   * @return the metrics options
   */
  public MetricsOptions getMetricsOptions() {
    return metricsOptions;
  }
}
