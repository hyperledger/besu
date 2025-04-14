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
package org.hyperledger.besu.cli

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableMap
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.DecodeException
import io.vertx.core.metrics.MetricsOptions
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.Runner
import org.hyperledger.besu.RunnerBuilder
import org.hyperledger.besu.chainexport.RlpBlockExporter
import org.hyperledger.besu.chainimport.Era1BlockImporter
import org.hyperledger.besu.chainimport.JsonBlockImporter
import org.hyperledger.besu.chainimport.RlpBlockImporter
import org.hyperledger.besu.cli.DefaultCommandValues.Companion.DEFAULT_NAT_METHOD
import org.hyperledger.besu.cli.DefaultCommandValues.Companion.getDefaultBesuDataPath
import org.hyperledger.besu.cli.NetworkDeprecationMessage.generate
import org.hyperledger.besu.cli.UnstableOptionsSubCommand.Companion.createUnstableOptions
import org.hyperledger.besu.cli.config.EthNetworkConfig
import org.hyperledger.besu.cli.config.EthNetworkConfig.Companion.getNetworkConfig
import org.hyperledger.besu.cli.config.NativeRequirement.NativeRequirementResult
import org.hyperledger.besu.cli.config.NetworkName
import org.hyperledger.besu.cli.config.ProfilesCompletionCandidates
import org.hyperledger.besu.cli.custom.JsonRPCAllowlistHostsProperty
import org.hyperledger.besu.cli.error.BesuExecutionExceptionHandler
import org.hyperledger.besu.cli.error.BesuParameterExceptionHandler
import org.hyperledger.besu.cli.options.*
import org.hyperledger.besu.cli.options.IpcOptions.Companion.getDefaultPath
import org.hyperledger.besu.cli.options.P2PDiscoveryOptions.NetworkInterfaceChecker
import org.hyperledger.besu.cli.options.PluginsConfigurationOptions.Companion.fromCommandLine
import org.hyperledger.besu.cli.options.storage.DataStorageOptions
import org.hyperledger.besu.cli.options.storage.PathBasedExtraStorageOptions
import org.hyperledger.besu.cli.options.unstable.QBFTOptions
import org.hyperledger.besu.cli.presynctasks.PreSynchronizationTaskRunner
import org.hyperledger.besu.cli.presynctasks.PrivateDatabaseMigrationPreSyncTask
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand
import org.hyperledger.besu.cli.subcommands.TxParseSubCommand
import org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand
import org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand
import org.hyperledger.besu.cli.util.BesuCommandCustomFactory
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.cli.util.ConfigDefaultValueProviderStrategy
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.components.BesuComponent
import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.config.MergeConfiguration
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.controller.BesuControllerBuilder
import org.hyperledger.besu.crypto.*
import org.hyperledger.besu.cryptoservices.KeyPairSecurityModule
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.enclave.EnclaveFactory
import org.hyperledger.besu.ethereum.api.ApiConfiguration
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.EngineAuthService
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.MiningParametersMetrics
import org.hyperledger.besu.ethereum.core.PrivacyParameters
import org.hyperledger.besu.ethereum.core.VersionMetadata
import org.hyperledger.besu.ethereum.eth.sync.SyncMode
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration
import org.hyperledger.besu.ethereum.p2p.discovery.P2PDiscoveryConfiguration
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl
import org.hyperledger.besu.ethereum.p2p.peers.StaticNodesParser
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProvider
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration
import org.hyperledger.besu.evm.precompile.*
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract.CacheEvent
import org.hyperledger.besu.metrics.*
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration
import org.hyperledger.besu.metrics.vertx.VertxMetricsAdapterFactory
import org.hyperledger.besu.nat.NatMethod
import org.hyperledger.besu.plugin.data.EnodeURL
import org.hyperledger.besu.plugin.services.*
import org.hyperledger.besu.plugin.services.exception.StorageException
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry
import org.hyperledger.besu.plugin.services.mining.MiningService
import org.hyperledger.besu.plugin.services.p2p.P2PService
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin
import org.hyperledger.besu.plugin.services.sync.SynchronizationService
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService
import org.hyperledger.besu.services.*
import org.hyperledger.besu.services.kvstore.InMemoryStoragePlugin
import org.hyperledger.besu.util.BesuVersionUtils
import org.hyperledger.besu.util.EphemeryGenesisUpdater.updateGenesis
import org.hyperledger.besu.util.InvalidConfigurationException
import org.hyperledger.besu.util.LogConfigurator
import org.hyperledger.besu.util.NetworkUtility
import org.hyperledger.besu.util.PermissioningConfigurationValidator.areAllNodesInAllowlist
import org.hyperledger.besu.util.number.Fraction
import org.hyperledger.besu.util.number.Percentage
import org.hyperledger.besu.util.number.PositiveNumber
import org.slf4j.Logger
import oshi.PlatformEnum
import oshi.SystemInfo
import picocli.AutoComplete
import picocli.CommandLine
import java.io.*
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import java.util.*
import java.util.function.DoubleSupplier
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

/** Represents the main Besu CLI command that runs the Besu Ethereum client full node.  */
// because Picocli injected fields report false positives
@CommandLine.Command(
    description = ["This command runs the Besu Ethereum client full node."],
    abbreviateSynopsis = true,
    name = "besu",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    header = ["@|bold,fg(cyan) Usage:|@"],
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold,fg(cyan) Description:|@%n%n",
    optionListHeading = "%n@|bold,fg(cyan) Options:|@%n",
    footerHeading = "%nBesu is licensed under the Apache License 2.0%n",
    footer = ["%n%n@|fg(cyan) To get started quickly, just choose a network to sync and a profile to run with suggested defaults:|@", "%n@|fg(cyan) for Mainnet|@ --network=mainnet --profile=[minimalist_staker|staker]", "%nMore info and other profiles at https://besu.hyperledger.org%n"
    ]
)
open class BesuCommand @VisibleForTesting protected constructor(
  private val rlpBlockImporter: Supplier<RlpBlockImporter>?,
  private val jsonBlockImporterFactory: Function<BesuController, JsonBlockImporter>?,
  private val era1BlockImporter: Supplier<Era1BlockImporter>?,
  private val rlpBlockExporterFactory: Function<Blockchain, RlpBlockExporter>?,
  private val runnerBuilder: RunnerBuilder,
  private val controllerBuilder: BesuController.Builder,
  /**
     * 2 Returns the plugin context.
     *
     * @return the plugin context.
     */
  @JvmField val besuPluginContext: BesuPluginContextImpl,
  private val environment: Map<String, String>?,
  private val storageService: StorageServiceImpl,
  private val securityModuleService: SecurityModuleServiceImpl,
  private val permissioningService: PermissioningServiceImpl,
  private val privacyPluginService: PrivacyPluginServiceImpl?,
  rpcEndpointServiceImpl: RpcEndpointServiceImpl,
  transactionSelectionServiceImpl: TransactionSelectionServiceImpl,
  transactionValidatorServiceImpl: TransactionPoolValidatorServiceImpl,
  transactionSimulationServiceImpl: TransactionSimulationServiceImpl,
  blockchainServiceImpl: BlockchainServiceImpl,
    // non-static for testing
  private val logger: Logger
) : DefaultCommandValues, Runnable {
    private var commandLine: CommandLine? = null

    // Unstable CLI options
    @JvmField
    val unstableNetworkingOptions: NetworkingOptions = NetworkingOptions.create()
    @JvmField
    val unstableSynchronizerOptions: SynchronizerOptions = SynchronizerOptions.create()
    @JvmField
    val unstableEthProtocolOptions: EthProtocolOptions = EthProtocolOptions.create()
    private val unstableDnsOptions = DnsOptions.create()
    private val unstableNatOptions = NatOptions.create()
    private val unstableNativeLibraryOptions = NativeLibraryOptions.create()
    private val unstableRPCOptions = RPCOptions.create()
    private val unstablePrivacyPluginOptions = PrivacyPluginOptions.create()
    private val unstableEvmOptions = EvmOptions.create()
    private val unstableIpcOptions = IpcOptions.create()
    private val unstableChainPruningOptions = ChainPruningOptions.create()
    private val unstableQbftOptions = QBFTOptions.create()

    // stable CLI options
    @JvmField
    val dataStorageOptions: DataStorageOptions = DataStorageOptions.create()
    private val ethstatsOptions = EthstatsOptions.create()
    private val nodePrivateKeyFileOption = NodePrivateKeyFileOption.create()
    private val loggingLevelOption = LoggingLevelOption.create()

    @JvmField
    @CommandLine.ArgGroup(validate = false, heading = "@|bold Tx Pool Common Options|@%n")
    val transactionPoolOptions: TransactionPoolOptions = TransactionPoolOptions.create()

    @JvmField
    @CommandLine.ArgGroup(validate = false, heading = "@|bold Block Builder Options|@%n")
    val miningOptions: MiningOptions = MiningOptions.create()

    private val rpcEndpointServiceImpl: RpcEndpointServiceImpl

    private val metricCategoryRegistry = MetricCategoryRegistryImpl()

    private val preSynchronizationTaskRunner = PreSynchronizationTaskRunner()

    private val allocatedPorts: MutableSet<Int> = HashSet()
    private val genesisConfigSupplier: Supplier<GenesisConfig> = Suppliers.memoize { this.readGenesisConfig() }
    private val genesisConfigOptionsSupplier: Supplier<GenesisConfigOptions> =
        Suppliers.memoize { this.readGenesisConfigOptions() }
    private val miningParametersSupplier: Supplier<MiningConfiguration> = Suppliers.memoize { this.miningParameters }
    private val apiConfigurationSupplier: Supplier<ApiConfiguration> = Suppliers.memoize { this.apiConfiguration }

    private var rocksDBPlugin: RocksDBPlugin? = null

    private var maxPeers = 0
    private var maxRemoteInitiatedPeers = 0

    // CLI options defined by user at runtime.
    // Options parsing is done with CLI library Picocli https://picocli.info/
    // While this variable is never read it is needed for the PicoCLI to create
    // the config file option that is read elsewhere.
    @CommandLine.Option(
        names = [DefaultCommandValues.CONFIG_FILE_OPTION_NAME],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["TOML config file (default: none)"]
    )
    private val configFile: File? = null

    @CommandLine.Option(
        names = ["--data-path"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["The path to Besu data directory (default: \${DEFAULT-VALUE})"]
    )
    val dataPath: Path = getDefaultBesuDataPath(this)

    // Genesis file path with null default option.
    // This default is handled by Runner
    // to use mainnet json file from resources as indicated in the
    // default network option
    // Then we ignore genesis default value here.
    @CommandLine.Option(
        names = ["--genesis-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Genesis file for your custom network. Setting this option requires --network-id to be set. (Cannot be used with --network)"]
    )
    private val genesisFile: File? = null

    @CommandLine.Option(
        names = ["--genesis-state-hash-cache-enabled"],
        description = ["Use genesis state hash from data on startup if specified (default: \${DEFAULT-VALUE})"]
    )
    private val genesisStateHashCacheEnabled = false

    @CommandLine.Option(
        names = ["--identity"],
        paramLabel = "<String>",
        description = ["Identification for this node in the Client ID"],
        arity = "1"
    )
    private val identityString = Optional.empty<String>()

    private var printPathsAndExit: Boolean = java.lang.Boolean.FALSE
    private var besuUserName = "besu"

    @CommandLine.Option(
        names = ["--print-paths-and-exit"],
        paramLabel = "<username>",
        description = ["Print the configured paths and exit without starting the node."],
        arity = "0..1"
    )
    fun setUserName(userName: String?) {
        val currentPlatform = SystemInfo.getCurrentPlatform()
        // Only allow on Linux and macOS
        if (currentPlatform == PlatformEnum.LINUX || currentPlatform == PlatformEnum.MACOS) {
            if (userName != null) {
                besuUserName = userName
            }
            printPathsAndExit = java.lang.Boolean.TRUE
        } else {
            throw UnsupportedOperationException(
                "--print-paths-and-exit is only supported on Linux and macOS."
            )
        }
    }

    // P2P Discovery Option Group
    @CommandLine.ArgGroup(validate = false, heading = "@|bold P2P Discovery Options|@%n")
    var p2PDiscoveryOptions: P2PDiscoveryOptions = P2PDiscoveryOptions()

    var p2PDiscoveryConfig: P2PDiscoveryConfiguration? = null

    private val transactionSelectionServiceImpl: TransactionSelectionServiceImpl
    private val transactionValidatorServiceImpl: TransactionPoolValidatorServiceImpl
    private val transactionSimulationServiceImpl: TransactionSimulationServiceImpl
    private val blockchainServiceImpl: BlockchainServiceImpl
    private var besuComponent: BesuComponent? = null

    @CommandLine.Option(
        names = ["--sync-mode"],
        paramLabel = DefaultCommandValues.MANDATORY_MODE_FORMAT_HELP,
        description = ["Synchronization mode, possible values are \${COMPLETION-CANDIDATES} (default: SNAP if a --network is supplied and privacy isn't enabled. FULL otherwise.)"]
    )
    private var syncMode: SyncMode? = null

    @CommandLine.Option(
        names = ["--sync-min-peers", "--fast-sync-min-peers"],
        paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
        description = ["Minimum number of peers required before starting sync. Has effect only on non-PoS networks. (default: \${DEFAULT-VALUE})"]
    )
    private val syncMinPeerCount = DefaultCommandValues.SYNC_MIN_PEER_COUNT

    /**
     * Gets the network for this BesuCommand
     *
     * @return the network for this BesuCommand
     */
    @CommandLine.Option(
        names = ["--network"],
        paramLabel = DefaultCommandValues.MANDATORY_NETWORK_FORMAT_HELP,
        defaultValue = "MAINNET",
        description = [("Synchronize against the indicated network, possible values are \${COMPLETION-CANDIDATES}."
                + " (default: \${DEFAULT-VALUE})")]
    )
    val network: NetworkName? = null

    @CommandLine.Option(
        names = [DefaultCommandValues.PROFILE_OPTION_NAME],
        paramLabel = DefaultCommandValues.PROFILE_FORMAT_HELP,
        completionCandidates = ProfilesCompletionCandidates::class,
        description = ["Overwrite default settings. Possible values are \${COMPLETION-CANDIDATES}. (default: none)"]
    )
    private val profile: String? = null // don't set it as final due to picocli completion candidates

    @CommandLine.Option(
        names = ["--nat-method"],
        description = [("Specify the NAT circumvention method to be used, possible values are \${COMPLETION-CANDIDATES}."
                + " NONE disables NAT functionality. (default: \${DEFAULT-VALUE})")]
    )
    private val natMethod = DEFAULT_NAT_METHOD

    @CommandLine.Option(
        names = ["--network-id"],
        paramLabel = "<BIG INTEGER>",
        description = ["P2P network identifier. (default: the selected network chain ID or custom genesis chain ID)"],
        arity = "1"
    )
    private val networkId: BigInteger? = null

    @CommandLine.Option(
        names = ["--kzg-trusted-setup"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = [("Path to file containing the KZG trusted setup, mandatory for custom networks that support data blobs, "
                + "optional for overriding named networks default.")],
        arity = "1"
    )
    private val kzgTrustedSetupFile: Path? = null

    /**
     * Returns the flag indicating that version compatibility checks will be made.
     *
     * @return true if compatibility checks should be made, otherwise false
     */
    @get:VisibleForTesting
    @CommandLine.Option(
        names = ["--version-compatibility-protection"],
        description = ["Perform compatibility checks between the version of Besu being started and the version of Besu that last started with this data directory. (default: \${DEFAULT-VALUE})"]
    )
    var versionCompatibilityProtection: Boolean? = null
        private set

    @CommandLine.ArgGroup(validate = false, heading = "@|bold GraphQL Options|@%n")
    var graphQlOptions: GraphQlOptions = GraphQlOptions()

    // Engine JSON-PRC Options
    @CommandLine.ArgGroup(validate = false, heading = "@|bold Engine JSON-RPC Options|@%n")
    var engineRPCOptions: EngineRPCOptions = EngineRPCOptions()

    var engineRPCConfig: EngineRPCConfiguration = engineRPCOptions.toDomainObject()

    // JSON-RPC HTTP Options
    @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC HTTP Options|@%n")
    var jsonRpcHttpOptions: JsonRpcHttpOptions = JsonRpcHttpOptions()

    // JSON-RPC Websocket Options
    @CommandLine.ArgGroup(validate = false, heading = "@|bold JSON-RPC Websocket Options|@%n")
    var rpcWebsocketOptions: RpcWebsocketOptions = RpcWebsocketOptions()

    // In-Process RPC Options
    @CommandLine.ArgGroup(validate = false, heading = "@|bold In-Process RPC Options|@%n")
    var inProcessRpcOptions: InProcessRpcOptions = InProcessRpcOptions.create()

    // Privacy Options Group
    @CommandLine.ArgGroup(validate = false, heading = "@|bold (Deprecated) Privacy Options |@%n")
    var privacyOptionGroup: PrivacyOptionGroup = PrivacyOptionGroup()

    class PrivacyOptionGroup {
        @CommandLine.Option(
            names = ["--privacy-tls-enabled"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Enable TLS for connecting to privacy enclave (default: \${DEFAULT-VALUE})")]
        )
        val isPrivacyTlsEnabled: Boolean = false

        @CommandLine.Option(
            names = ["--privacy-tls-keystore-file"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Path to a PKCS#12 formatted keystore; used to enable TLS on inbound connections.")]
        )
        val privacyKeyStoreFile: Path? = null

        @CommandLine.Option(
            names = ["--privacy-tls-keystore-password-file"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Path to a file containing the password used to decrypt the keystore.")]
        )
        val privacyKeyStorePasswordFile: Path? = null

        @CommandLine.Option(
            names = ["--privacy-tls-known-enclave-file"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Path to a file containing the fingerprints of the authorized privacy enclave.")]
        )
        val privacyTlsKnownEnclaveFile: Path? = null

        @CommandLine.Option(
            names = ["--privacy-enabled"],
            description = [PRIVACY_DEPRECATION_PREFIX + "Enable private transactions (default: \${DEFAULT-VALUE})"]
        )
        val isPrivacyEnabled: Boolean = false

        @CommandLine.Option(
            names = ["--privacy-multi-tenancy-enabled"], description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Enable multi-tenant private transactions (default: \${DEFAULT-VALUE})")]
        )
        val isPrivacyMultiTenancyEnabled: Boolean = false

        @CommandLine.Option(
            names = ["--privacy-url"],
            description = [PRIVACY_DEPRECATION_PREFIX + "The URL on which the enclave is running"]
        )
        val privacyUrl: URI = PrivacyParameters.DEFAULT_ENCLAVE_URL

        @CommandLine.Option(
            names = ["--privacy-public-key-file"],
            description = [PRIVACY_DEPRECATION_PREFIX + "The enclave's public key file"]
        )
        val privacyPublicKeyFile: File? = null

        @CommandLine.Option(
            names = ["--privacy-marker-transaction-signing-key-file"], description = [(PRIVACY_DEPRECATION_PREFIX
                    + "The name of a file containing the private key used to sign privacy marker transactions. If unset, each will be signed with a random key.")]
        )
        val privateMarkerTransactionSigningKeyPath: Path? = null

        @CommandLine.Option(
            names = ["--privacy-enable-database-migration"], description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Enable private database metadata migration (default: \${DEFAULT-VALUE})")]
        )
        val migratePrivateDatabase: Boolean = false

        @CommandLine.Option(
            names = ["--privacy-flexible-groups-enabled"], description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Enable flexible privacy groups (default: \${DEFAULT-VALUE})")]
        )
        val isFlexiblePrivacyGroupsEnabled: Boolean = false

        @CommandLine.Option(
            names = ["--privacy-nonce-always-increments"], description = [(PRIVACY_DEPRECATION_PREFIX
                    + "Enable private nonce "
                    + "incrementation even if the transaction didn't succeeded (default: \${DEFAULT-VALUE})")]
        )
        val isPrivateNonceAlwaysIncrementsEnabled: Boolean = false
    }

    /**
     * Returns the metrics options
     *
     * @return the metrics options
     */
    // Metrics Option Group
    @JvmField
    @CommandLine.ArgGroup(validate = false, heading = "@|bold Metrics Options|@%n")
    var metricsOptions: org.hyperledger.besu.cli.options.MetricsOptions =
        org.hyperledger.besu.cli.options.MetricsOptions.create()

    @CommandLine.Option(
        names = ["--host-allowlist"],
        paramLabel = "<hostname>[,<hostname>...]... or * or all",
        description = ["Comma separated list of hostnames to allow for RPC access, or * to accept any host (default: \${DEFAULT-VALUE})"],
        defaultValue = "localhost,127.0.0.1"
    )
    private val hostsAllowlist = JsonRPCAllowlistHostsProperty()

    @CommandLine.Option(
        names = ["--reorg-logging-threshold"],
        description = ["How deep a chain reorganization must be in order for it to be logged (default: \${DEFAULT-VALUE})"]
    )
    private val reorgLoggingThreshold = 6L

    // Permission Option Group
    @CommandLine.ArgGroup(validate = false, heading = "@|bold Permissions Options|@%n")
    var permissionsOptions: PermissionsOptions = PermissionsOptions()

    @CommandLine.Option(
        names = ["--revert-reason-enabled"],
        description = ["Enable passing the revert reason back through TransactionReceipts (default: \${DEFAULT-VALUE})"]
    )
    private val isRevertReasonEnabled = false

    @CommandLine.Option(
        names = ["--required-blocks", "--required-block"],
        paramLabel = "BLOCK=HASH",
        description = ["Block number and hash peers are required to have."],
        arity = "*",
        split = ","
    )
    private val requiredBlocks: Map<Long, Hash> = HashMap()

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--key-value-storage"],
        description = ["Identity for the key-value storage to be used."],
        arity = "1"
    )
    private val keyValueStorageName = DefaultCommandValues.DEFAULT_KEY_VALUE_STORAGE_NAME

    @CommandLine.Option(
        names = ["--security-module"],
        paramLabel = "<NAME>",
        description = ["Identity for the Security Module to be used."],
        arity = "1"
    )
    private val securityModuleName = DefaultCommandValues.DEFAULT_SECURITY_MODULE

    @CommandLine.Option(
        names = ["--auto-log-bloom-caching-enabled"],
        description = ["Enable automatic log bloom caching (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private val autoLogBloomCachingEnabled = true

    @CommandLine.Option(
        names = ["--override-genesis-config"],
        paramLabel = "NAME=VALUE",
        description = ["Overrides configuration values in the genesis file.  Use with care."],
        arity = "*",
        hidden = true,
        split = ","
    )
    private val genesisConfigOverrides: Map<String?, String?> = TreeMap(java.lang.String.CASE_INSENSITIVE_ORDER)

    @CommandLine.Option(
        names = ["--pid-path"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["Path to PID file (optional)"]
    )
    private val pidPath: Path? = null

    // API Configuration Option Group
    @CommandLine.ArgGroup(validate = false, heading = "@|bold API Configuration Options|@%n")
    var apiConfigurationOptions: ApiConfigurationOptions = ApiConfigurationOptions()

    @CommandLine.Option(
        names = ["--static-nodes-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Specifies the static node file containing the static nodes for this node to connect to"]
    )
    private val staticNodesFile: Path? = null

    @CommandLine.Option(
        names = ["--cache-last-blocks"],
        description = ["Specifies the number of last blocks to cache  (default: \${DEFAULT-VALUE})"]
    )
    private val numberOfBlocksToCache = 0

    @CommandLine.Option(
        names = ["--cache-precompiles"],
        description = ["Specifies whether to cache precompile results (default: \${DEFAULT-VALUE})"]
    )
    private val enablePrecompileCaching = false

    // Plugins Configuration Option Group
    @CommandLine.ArgGroup(validate = false)
    var pluginsConfigurationOptions: PluginsConfigurationOptions = PluginsConfigurationOptions()

    private var ethNetworkConfig: EthNetworkConfig? = null
    private var jsonRpcConfiguration: JsonRpcConfiguration? = null
    private var engineJsonRpcConfiguration: JsonRpcConfiguration? = null
    private var graphQLConfiguration: GraphQLConfiguration? = null
    private var webSocketConfiguration: WebSocketConfiguration? = null
    private var jsonRpcIpcConfiguration: JsonRpcIpcConfiguration? = null
    private var inProcessRpcConfiguration: InProcessRpcConfiguration? = null
    private var metricsConfiguration: MetricsConfiguration? = null
    private var permissioningConfiguration: Optional<PermissioningConfiguration>? = null
    private var dataStorageConfiguration: DataStorageConfiguration? = null
    private var staticNodes: Collection<EnodeURL>? = null
    private var besuController: BesuController? = null
    private var pluginCommonConfiguration: BesuConfigurationImpl? = null

    private var vertx: Vertx? = null

    @get:VisibleForTesting
    var enodeDnsConfiguration: EnodeDnsConfiguration? = null
        /**
         * Represents Enode DNS Configuration. Visible for testing.
         *
         * @return instance of EnodeDnsConfiguration
         */
        get() {
            if (field == null) {
                field = unstableDnsOptions.toDomainObject()
            }
            return field
        }
        private set
    private var keyValueStorageProvider: KeyValueStorageProvider? = null

    /**
     * Besu command constructor.
     *
     * @param rlpBlockImporter RlpBlockImporter supplier
     * @param jsonBlockImporterFactory instance of `Function<BesuController, JsonBlockImporter>`
     * @param era1BlockImporter Era1BlockImporter supplier
     * @param rlpBlockExporterFactory instance of `Function<Blockchain, RlpBlockExporter>`
     * @param runnerBuilder instance of RunnerBuilder
     * @param controllerBuilder instance of BesuController.Builder
     * @param besuPluginContext instance of BesuPluginContextImpl
     * @param environment Environment variables map
     * @param commandLogger instance of Logger for outputting to the CLI
     */
    constructor(
        rlpBlockImporter: Supplier<RlpBlockImporter>?,
        jsonBlockImporterFactory: Function<BesuController, JsonBlockImporter>?,
        era1BlockImporter: Supplier<Era1BlockImporter>?,
        rlpBlockExporterFactory: Function<Blockchain, RlpBlockExporter>?,
        runnerBuilder: RunnerBuilder,
        controllerBuilder: BesuController.Builder,
        besuPluginContext: BesuPluginContextImpl,
        environment: Map<String, String>?,
        commandLogger: Logger
    ) : this(
        rlpBlockImporter!!,
        jsonBlockImporterFactory!!,
        era1BlockImporter,
        rlpBlockExporterFactory,
        runnerBuilder,
        controllerBuilder,
        besuPluginContext,
        environment,
        StorageServiceImpl(),
        SecurityModuleServiceImpl(),
        PermissioningServiceImpl(),
        PrivacyPluginServiceImpl(),
        RpcEndpointServiceImpl(),
        TransactionSelectionServiceImpl(),
        TransactionPoolValidatorServiceImpl(),
        TransactionSimulationServiceImpl(),
        BlockchainServiceImpl(),
        commandLogger
    )

    /**
     * Overloaded Besu command constructor visible for testing.
     *
     * @param rlpBlockImporter RlpBlockImporter supplier
     * @param jsonBlockImporterFactory instance of `Function<BesuController, JsonBlockImporter>`
     * @param era1BlockImporter Era1BlockImporter supplier
     * @param rlpBlockExporterFactory instance of `Function<Blockchain, RlpBlockExporter>`
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
    init {
        if (besuPluginContext.getService(BesuConfigurationImpl::class.java).isPresent) {
            this.pluginCommonConfiguration =
                besuPluginContext.getService(BesuConfigurationImpl::class.java).get()
        } else {
            this.pluginCommonConfiguration = BesuConfigurationImpl()
            besuPluginContext.addService<BesuConfiguration>(
                BesuConfiguration::class.java,
                pluginCommonConfiguration!!
            )
        }
        this.rpcEndpointServiceImpl = rpcEndpointServiceImpl
        this.transactionSelectionServiceImpl = transactionSelectionServiceImpl
        this.transactionValidatorServiceImpl = transactionValidatorServiceImpl
        this.transactionSimulationServiceImpl = transactionSimulationServiceImpl
        this.blockchainServiceImpl = blockchainServiceImpl
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
    fun parse(
        resultHandler: CommandLine.IExecutionStrategy,
        parameterExceptionHandler: BesuParameterExceptionHandler,
        executionExceptionHandler: BesuExecutionExceptionHandler,
        `in`: InputStream,
        besuComponent: BesuComponent,
        vararg args: String?
    ): Int {
        requireNotNull(besuComponent) { "BesuComponent must be provided" }
        this.besuComponent = besuComponent
        initializeCommandLineSettings(`in`)

        // Create the execution strategy chain.
        val executeTask = createExecuteTask(resultHandler)
        val pluginRegistrationTask = createPluginRegistrationTask(executeTask)
        val setDefaultValueProviderTask =
            createDefaultValueProviderTask(pluginRegistrationTask)

        // 1- Config default value provider
        // 2- Register plugins
        // 3- Execute command
        return executeCommandLine(
            setDefaultValueProviderTask, parameterExceptionHandler, executionExceptionHandler, *args
        )
    }

    private fun initializeCommandLineSettings(`in`: InputStream) {
        toCommandLine()
        // Automatically adjust the width of usage messages to the terminal width.
        commandLine!!.commandSpec.usageMessage().autoWidth(true)

        handleStableOptions()
        addSubCommands(`in`)
        registerConverters()
        handleUnstableOptions()
        preparePlugins()
    }

    private fun createExecuteTask(nextStep: CommandLine.IExecutionStrategy): CommandLine.IExecutionStrategy {
        return CommandLine.IExecutionStrategy { parseResult: CommandLine.ParseResult ->
            commandLine!!.setExecutionStrategy(nextStep)
            // At this point we don't allow unmatched options since plugins were already registered
            commandLine!!.setUnmatchedArgumentsAllowed(false)
            commandLine!!.execute(*parseResult.originalArgs().toTypedArray<String>())
        }
    }

    private fun createPluginRegistrationTask(nextStep: CommandLine.IExecutionStrategy): CommandLine.IExecutionStrategy {
        return CommandLine.IExecutionStrategy { parseResult: CommandLine.ParseResult ->
            besuPluginContext.initialize(
                fromCommandLine(
                    commandLine!!
                )
            )
            besuPluginContext.registerPlugins()
            commandLine!!.setExecutionStrategy(nextStep)
            commandLine!!.execute(*parseResult.originalArgs().toTypedArray<String>())
        }
    }

    private fun createDefaultValueProviderTask(nextStep: CommandLine.IExecutionStrategy): CommandLine.IExecutionStrategy {
        return ConfigDefaultValueProviderStrategy(nextStep, environment!!)
    }

    /**
     * Executes the command line with the provided execution strategy and exception handlers.
     *
     * @param executionStrategy The execution strategy to use.
     * @param args The command line arguments.
     * @return The execution result status code.
     */
    private fun executeCommandLine(
        executionStrategy: CommandLine.IExecutionStrategy,
        parameterExceptionHandler: BesuParameterExceptionHandler,
        executionExceptionHandler: BesuExecutionExceptionHandler,
        vararg args: String?
    ): Int {
        return commandLine!!
            .setExecutionStrategy(executionStrategy)
            .setParameterExceptionHandler(parameterExceptionHandler)
            .setExecutionExceptionHandler(executionExceptionHandler) // As this happens before the plugins registration and plugins can add options, we must
            // allow unmatched options
            .setUnmatchedArgumentsAllowed(true)
            .execute(*args)
    }

    /** Used by Dagger to parse all options into a commandline instance.  */
    fun toCommandLine() {
        commandLine =
            CommandLine(this, BesuCommandCustomFactory(besuPluginContext))
                .setCaseInsensitiveEnumValuesAllowed(true)
    }

    override fun run() {
        if (network != null && network.isDeprecated) {
            logger.warn(generate(network))
        }
        try {
            configureLogging(true)

            if (printPathsAndExit) {
                // Print configured paths requiring read/write permissions to be adjusted
                checkPermissionsAndPrintPaths(besuUserName)
                System.exit(0) // Exit before any services are started
            }

            // set merge config on the basis of genesis config
            setMergeConfigOptions()

            setIgnorableStorageSegments()

            instantiateSignatureAlgorithmFactory()

            logger.info("Starting Besu")

            // Need to create vertx after cmdline has been parsed, such that metricsSystem is configurable
            vertx = createVertx(createVertxOptions(besuComponent!!.metricsSystem))

            validateOptions()

            configure()

            // If we're not running against a named network, or if version compat protection has been
            // explicitly enabled, perform compatibility check
            VersionMetadata.versionCompatibilityChecks(versionCompatibilityProtection!!, dataDir())

            configureNativeLibs(Optional.ofNullable(network))
            if (enablePrecompileCaching) {
                configurePrecompileCaching()
            }

            besuController = buildController()

            besuPluginContext.beforeExternalServices()

            val runner = buildRunner()
            runner.startExternalServices()

            startPlugins(runner)
            validatePrivacyPluginOptions()
            setReleaseMetrics()
            preSynchronization()

            runner.startEthereumMainLoop()

            besuPluginContext.afterExternalServicesMainLoop()

            runner.awaitStop()
        } catch (e: Exception) {
            logger.error("Failed to start Besu: {}", e.message)
            logger.debug("Startup failure cause", e)
            throw CommandLine.ParameterException(this.commandLine, e.message, e)
        }
    }

    private fun configurePrecompileCaching() {
        // enable precompile caching:
        AbstractPrecompiledContract.setPrecompileCaching(enablePrecompileCaching)
        // separately set KZG precompile caching, it does not extend AbstractPrecompiledContract:
        KZGPointEvalPrecompiledContract.setPrecompileCaching(enablePrecompileCaching)
        // separately set BLS precompiles caching, they do not extend AbstractPrecompiledContract:
        AbstractBLS12PrecompiledContract.setPrecompileCaching(enablePrecompileCaching)

        // set a metric logger
        val precompileCounter =
            metricsSystem
                .createLabelledCounter(
                    BesuMetricCategory.BLOCK_PROCESSING,
                    "precompile_cache",
                    "precompile cache labeled counter",
                    "precompile_name",
                    "event"
                )

        // set a cache event consumer which logs a metrics event
        AbstractPrecompiledContract.setCacheEventConsumer { cacheEvent: CacheEvent ->
            precompileCounter
                .labels(cacheEvent.precompile, cacheEvent.cacheMetric.name)
                .inc()
        }
    }

    private fun checkPermissionsAndPrintPaths(userName: String) {
        // Check permissions for the data path
        checkPermissions(dataDir(), userName, false)

        // Check permissions for genesis file
        try {
            if (genesisFile != null) {
                checkPermissions(genesisFile.toPath(), userName, true)
            }
        } catch (e: Exception) {
            commandLine!!
                .getOut()
                .println("Error: Failed checking genesis file: Reason: " + e.message)
        }
    }

    // Helper method to check permissions on a given path
    private fun checkPermissions(path: Path, besuUser: String, readOnly: Boolean) {
        try {
            // Get the permissions of the file
            // check if besu user is the owner - get owner permissions if yes
            // else, check if besu user and owner are in the same group - if yes, check the group
            // permission
            // otherwise check permissions for others

            // Get the owner of the file or directory

            val owner = Files.getOwner(path)
            val hasReadPermission: Boolean
            val hasWritePermission: Boolean

            // Get file permissions
            val permissions = Files.getPosixFilePermissions(path)

            // Check if besu is the owner
            if (owner.name == besuUser) {
                // Owner permissions
                hasReadPermission = permissions.contains(PosixFilePermission.OWNER_READ)
                hasWritePermission = permissions.contains(PosixFilePermission.OWNER_WRITE)
            } else {
                // Get the group of the file
                // Get POSIX file attributes and then group
                val attrs = Files.readAttributes(
                    path,
                    PosixFileAttributes::class.java
                )
                val group = attrs.group()

                // Check if besu user belongs to this group
                val isMember = isGroupMember(besuUserName, group)

                if (isMember) {
                    // Group's permissions
                    hasReadPermission = permissions.contains(PosixFilePermission.GROUP_READ)
                    hasWritePermission = permissions.contains(PosixFilePermission.GROUP_WRITE)
                } else {
                    // Others' permissions
                    hasReadPermission = permissions.contains(PosixFilePermission.OTHERS_READ)
                    hasWritePermission = permissions.contains(PosixFilePermission.OTHERS_WRITE)
                }
            }

            if (!hasReadPermission || (!readOnly && !hasWritePermission)) {
                val accessType = if (readOnly) "READ" else "READ_WRITE"
                commandLine!!.out.println("PERMISSION_CHECK_PATH:$path:$accessType")
            }
        } catch (e: Exception) {
            // Do nothing upon catching an error
            commandLine!!
                .getOut()
                .println(
                    ("Error: Failed to check permissions for path: '"
                            + path
                            + "'. Reason: "
                            + e.message)
                )
        }
    }

    @VisibleForTesting
    fun setBesuConfiguration(pluginCommonConfiguration: BesuConfigurationImpl?) {
        this.pluginCommonConfiguration = pluginCommonConfiguration
    }

    private fun addSubCommands(`in`: InputStream) {
        commandLine!!.addSubcommand(
            BlocksSubCommand.COMMAND_NAME,
            BlocksSubCommand(
                rlpBlockImporter!!,
                jsonBlockImporterFactory!!,
                era1BlockImporter!!,
                rlpBlockExporterFactory!!,
                commandLine!!.out
            )
        )
        commandLine!!.addSubcommand(
            TxParseSubCommand.COMMAND_NAME, TxParseSubCommand(commandLine!!.out)
        )
        commandLine!!.addSubcommand(
            PublicKeySubCommand.COMMAND_NAME, PublicKeySubCommand(commandLine!!.out)
        )
        commandLine!!.addSubcommand(
            PasswordSubCommand.COMMAND_NAME, PasswordSubCommand(commandLine!!.out)
        )
        commandLine!!.addSubcommand(
            RLPSubCommand.COMMAND_NAME, RLPSubCommand(commandLine!!.out, `in`)
        )
        commandLine!!.addSubcommand(
            OperatorSubCommand.COMMAND_NAME, OperatorSubCommand(commandLine!!.out)
        )
        commandLine!!.addSubcommand(
            ValidateConfigSubCommand.COMMAND_NAME,
            ValidateConfigSubCommand(commandLine!!, commandLine!!.out)
        )
        commandLine!!.addSubcommand(
            StorageSubCommand.COMMAND_NAME, StorageSubCommand(commandLine!!.out)
        )
        val generateCompletionSubcommandName = "generate-completion"
        commandLine!!.addSubcommand(
            generateCompletionSubcommandName, AutoComplete.GenerateCompletion::class.java
        )
        val generateCompletionSubcommand =
            commandLine!!.subcommands[generateCompletionSubcommandName]
        generateCompletionSubcommand!!.commandSpec.usageMessage().hidden(true)
    }

    private fun registerConverters() {
        commandLine!!.registerConverter(
            Address::class.java
        ) { str: String? -> Address.fromHexStringStrict(str) }
        commandLine!!.registerConverter(
            Bytes::class.java
        ) { str: String? -> Bytes.fromHexString(str) }
        commandLine!!.registerConverter(
            MetricsProtocol::class.java
        ) { str: String? -> MetricsProtocol.fromString(str) }
        commandLine!!.registerConverter(
            UInt256::class.java
        ) { arg: String? -> UInt256.valueOf(BigInteger(arg)) }
        commandLine!!.registerConverter(
            Wei::class.java
        ) { arg: String? -> Wei.of(java.lang.Long.parseUnsignedLong(arg)) }
        commandLine!!.registerConverter(
            PositiveNumber::class.java
        ) { str: String? -> PositiveNumber.fromString(str) }
        commandLine!!.registerConverter(
            Hash::class.java
        ) { str: String? -> Hash.fromHexString(str) }
        commandLine!!.registerConverter(
            Optional::class.java
        ) { value: String? ->
            Optional.of(
                value!!
            )
        }
        commandLine!!.registerConverter(
            Double::class.java
        ) { s: String -> s.toDouble() }
    }

    private fun handleStableOptions() {
        commandLine!!.addMixin("Ethstats", ethstatsOptions)
        commandLine!!.addMixin("Private key file", nodePrivateKeyFileOption)
        commandLine!!.addMixin("Logging level", loggingLevelOption)
        commandLine!!.addMixin("Data Storage Options", dataStorageOptions)
    }

    private fun handleUnstableOptions() {
        // Add unstable options
        val unstableOptionsBuild = ImmutableMap.builder<String, Any>()
        val unstableOptions =
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
                .put("QBFT Options", unstableQbftOptions)
                .build()

        createUnstableOptions(commandLine!!, unstableOptions)
    }

    private fun preparePlugins() {
        besuPluginContext.addService(PicoCLIOptions::class.java, PicoCLIOptionsImpl(commandLine!!))
        besuPluginContext.addService(SecurityModuleService::class.java, securityModuleService)
        besuPluginContext.addService(StorageService::class.java, storageService)

        metricCategoryRegistry.addCategories(BesuMetricCategory::class.java)
        metricCategoryRegistry.addCategories(StandardMetricCategory::class.java)
        besuPluginContext.addService(MetricCategoryRegistry::class.java, metricCategoryRegistry)
        besuPluginContext.addService(PermissioningService::class.java, permissioningService)
        besuPluginContext.addService(PrivacyPluginService::class.java, privacyPluginService!!)
        besuPluginContext.addService(RpcEndpointService::class.java, rpcEndpointServiceImpl)
        besuPluginContext.addService(
            TransactionSelectionService::class.java, transactionSelectionServiceImpl
        )
        besuPluginContext.addService(
            TransactionPoolValidatorService::class.java, transactionValidatorServiceImpl
        )
        besuPluginContext.addService(
            TransactionSimulationService::class.java, transactionSimulationServiceImpl
        )
        besuPluginContext.addService(BlockchainService::class.java, blockchainServiceImpl)

        // register built-in plugins
        rocksDBPlugin = RocksDBPlugin()
        rocksDBPlugin!!.register(besuPluginContext)
        InMemoryStoragePlugin().register(besuPluginContext)

        // register default security module
        securityModuleService.register(
            DefaultCommandValues.DEFAULT_SECURITY_MODULE, Suppliers.memoize { this.defaultSecurityModule() })
    }

    private fun defaultSecurityModule(): SecurityModule {
        return KeyPairSecurityModule(loadKeyPair(nodePrivateKeyFileOption.nodePrivateKeyFile))
    }

    /**
     * Load key pair from private key. Visible to be accessed by subcommands.
     *
     * @param nodePrivateKeyFile File containing private key
     * @return KeyPair loaded from private key file
     */
    fun loadKeyPair(nodePrivateKeyFile: File?): KeyPair {
        return KeyPairUtil.loadKeyPair(resolveNodePrivateKeyFile(nodePrivateKeyFile))
    }

    private fun preSynchronization() {
        preSynchronizationTaskRunner.runTasks(besuController)
    }

    private fun buildRunner(): Runner {
        return synchronize(
            besuController,
            p2PDiscoveryConfig!!.p2pEnabled,
            p2PDiscoveryConfig!!.peerDiscoveryEnabled,
            ethNetworkConfig,
            p2PDiscoveryConfig!!.p2pHost,
            p2PDiscoveryConfig!!.p2pInterface,
            p2PDiscoveryConfig!!.p2pPort,
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
            pidPath
        )
    }

    private fun startPlugins(runner: Runner) {
        blockchainServiceImpl.init(
            besuController!!.protocolContext.blockchain, besuController!!.protocolSchedule
        )
        transactionSimulationServiceImpl.init(
            besuController!!.protocolContext.blockchain, besuController!!.transactionSimulator
        )
        rpcEndpointServiceImpl.init(runner.inProcessRpcMethods)

        besuPluginContext.addService(
            BesuEvents::class.java,
            BesuEventsImpl(
                besuController!!.protocolContext.blockchain,
                besuController!!.protocolManager.blockBroadcaster,
                besuController!!.transactionPool,
                besuController!!.syncState,
                besuController!!.protocolContext.badBlockManager
            )
        )
        besuPluginContext.addService(MetricsSystem::class.java, metricsSystem)

        besuPluginContext.addService(BlockchainService::class.java, blockchainServiceImpl)

        besuPluginContext.addService(
            SynchronizationService::class.java,
            SynchronizationServiceImpl(
                besuController!!.synchronizer,
                besuController!!.protocolContext,
                besuController!!.protocolSchedule,
                besuController!!.syncState,
                besuController!!.protocolContext.worldStateArchive
            )
        )

        besuPluginContext.addService(P2PService::class.java, P2PServiceImpl(runner.p2PNetwork))

        besuPluginContext.addService(
            TransactionPoolService::class.java,
            TransactionPoolServiceImpl(besuController!!.transactionPool)
        )

        besuPluginContext.addService(
            RlpConverterService::class.java, RlpConverterServiceImpl(besuController!!.protocolSchedule)
        )

        besuPluginContext.addService(
            TraceService::class.java,
            TraceServiceImpl(
                BlockchainQueries(
                    besuController!!.protocolSchedule,
                    besuController!!.protocolContext.blockchain,
                    besuController!!.protocolContext.worldStateArchive,
                    miningParametersSupplier.get()
                ),
                besuController!!.protocolSchedule
            )
        )

        besuPluginContext.addService(
            MiningService::class.java, MiningServiceImpl(besuController!!.miningCoordinator)
        )

        besuPluginContext.addService(
            BlockSimulationService::class.java,
            BlockSimulatorServiceImpl(
                besuController!!.protocolContext.worldStateArchive,
                miningParametersSupplier.get(),
                besuController!!.transactionSimulator,
                besuController!!.protocolSchedule,
                besuController!!.protocolContext.blockchain
            )
        )

        besuController!!.additionalPluginServices.appendPluginServices(besuPluginContext)
        besuPluginContext.startPlugins()
    }

    private fun validatePrivacyPluginOptions() {
        // plugins do not 'wire up' until start has been called
        // consequently you can only do some configuration checks
        // after start has been called on plugins

        if (java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyEnabled) {
            logger.warn(
                "--Xprivacy-plugin-enabled and related options are " + PRIVACY_DEPRECATION_PREFIX
            )

            if (privacyOptionGroup.privateMarkerTransactionSigningKeyPath != null && privacyPluginService != null && privacyPluginService.privateMarkerTransactionFactory != null) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "--privacy-marker-transaction-signing-key-file can not be used in conjunction with a plugin that specifies a PrivateMarkerTransactionFactory"
                )
            }

            if (Wei.ZERO.compareTo(miningParametersSupplier.get().minTransactionGasPrice) < 0
                && (privacyOptionGroup.privateMarkerTransactionSigningKeyPath == null
                        && (privacyPluginService == null
                        || privacyPluginService.privateMarkerTransactionFactory == null))
            ) {
                // if gas is required, cannot use random keys to sign private tx
                // ie --privacy-marker-transaction-signing-key-file must be set
                throw CommandLine.ParameterException(
                    commandLine,
                    "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified and must be a funded account. Private transactions cannot be signed by random (non-funded) accounts in paid gas networks"
                )
            }

            if (unstablePrivacyPluginOptions.isPrivacyPluginEnabled
                && privacyPluginService != null && privacyPluginService.payloadProvider == null
            ) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "No Payload Provider has been provided. You must register one when enabling privacy plugin!"
                )
            }

            if (unstablePrivacyPluginOptions.isPrivacyPluginEnabled
                && privacyOptionGroup.isFlexiblePrivacyGroupsEnabled
            ) {
                throw CommandLine.ParameterException(
                    commandLine, "Privacy Plugin can not be used with flexible privacy groups"
                )
            }
        }
    }

    private fun setReleaseMetrics() {
        besuComponent!!
            .getMetricsSystem()
            .createLabelledSuppliedGauge(
                StandardMetricCategory.PROCESS, "release", "Release information", "version"
            )
            .labels(DoubleSupplier { 1.0 }, BesuVersionUtils.version())
    }

    /**
     * Configure logging framework for Besu
     *
     * @param announce sets to true to print the logging level on standard output
     */
    fun configureLogging(announce: Boolean) {
        // To change the configuration if color was enabled/disabled
        LogConfigurator.reconfigure()
        // set log level per CLI flags
        val logLevel = loggingLevelOption.logLevel
        if (logLevel != null) {
            if (announce) {
                println("Setting logging level to $logLevel")
            }
            LogConfigurator.setLevel("", logLevel)
        }
    }

    @VisibleForTesting
    fun configureNativeLibs(configuredNetwork: Optional<NetworkName>) {
        if (unstableNativeLibraryOptions.nativeAltbn128
            && AbstractAltBnPrecompiledContract.maybeEnableNative()
        ) {
            logger.info("Using the native implementation of alt bn128")
        } else {
            AbstractAltBnPrecompiledContract.disableNative()
            logger.info("Using the Java implementation of alt bn128")
        }

        if (unstableNativeLibraryOptions.nativeModExp
            && BigIntegerModularExponentiationPrecompiledContract.maybeEnableNative()
        ) {
            logger.info("Using the native implementation of modexp")
        } else {
            BigIntegerModularExponentiationPrecompiledContract.disableNative()
            logger.info("Using the Java implementation of modexp")
        }

        if (unstableNativeLibraryOptions.nativeSecp
            && SignatureAlgorithmFactory.getInstance().maybeEnableNative()
        ) {
            logger.info("Using the native implementation of the signature algorithm")
        } else {
            SignatureAlgorithmFactory.getInstance().disableNative()
            logger.info("Using the Java implementation of the signature algorithm")
        }

        if (unstableNativeLibraryOptions.nativeBlake2bf
            && Blake2bfMessageDigest.Blake2bfDigest.isNative()
        ) {
            logger.info("Using the native implementation of the blake2bf algorithm")
        } else {
            Blake2bfMessageDigest.Blake2bfDigest.disableNative()
            logger.info("Using the Java implementation of the blake2bf algorithm")
        }

        if (genesisConfigOptionsSupplier.get().cancunTime.isPresent
            || genesisConfigOptionsSupplier.get().cancunEOFTime.isPresent
            || genesisConfigOptionsSupplier.get().pragueTime.isPresent
            || genesisConfigOptionsSupplier.get().osakaTime.isPresent
        ) {
            if (kzgTrustedSetupFile != null) {
                KZGPointEvalPrecompiledContract.init(kzgTrustedSetupFile)
            } else {
                KZGPointEvalPrecompiledContract.init()
            }
        } else if (kzgTrustedSetupFile != null) {
            throw CommandLine.ParameterException(
                this.commandLine,
                "--kzg-trusted-setup can only be specified on networks with data blobs enabled"
            )
        }
        // assert required native libraries have been loaded
        if (genesisFile == null && configuredNetwork.isPresent) {
            checkRequiredNativeLibraries(configuredNetwork.get())
        }
    }

    @VisibleForTesting
    fun checkRequiredNativeLibraries(configuredNetwork: NetworkName?) {
        if (configuredNetwork == null) {
            return
        }

        // assert native library requirements for named networks:
        val failedNativeReqs =
            configuredNetwork.getNativeRequirements().stream().filter { r: NativeRequirementResult -> !r.present }
                .toList()

        if (!failedNativeReqs.isEmpty()) {
            val failures =
                failedNativeReqs.stream()
                    .map { r: NativeRequirementResult -> r.libname + " " + r.errorMessage }
                    .collect(Collectors.joining("\n\t"))
            throw UnsupportedOperationException(
                String.format(
                    ("""
                    Failed to load required native libraries for network %s. Verify whether your platform %s and arch %s are supported by besu. Failures loading: 
                    %s
                    """.trimIndent()),
                    configuredNetwork.name,
                    System.getProperty("os.name"),
                    System.getProperty("os.arch"),
                    failures
                )
            )
        }
    }

    private fun validateOptions() {
        issueOptionWarnings()
        validateP2POptions()
        validateMiningParams()
        validateNatParams()
        validateNetStatsParams()
        validateDnsOptionsParams()
        ensureValidPeerBoundParams()
        validateRpcOptionsParams()
        validateRpcWsOptions()
        validateChainDataPruningParams()
        validatePostMergeCheckpointBlockRequirements()
        validateTransactionPoolOptions()
        validateDataStorageOptions()
        validateGraphQlOptions()
        validatePluginOptions()
    }

    private fun validatePluginOptions() {
        pluginsConfigurationOptions.validate(commandLine)
    }

    private fun validateApiOptions() {
        apiConfigurationOptions.validate(commandLine!!, logger)
    }

    private fun validateTransactionPoolOptions() {
        transactionPoolOptions.validate(commandLine, genesisConfigOptionsSupplier.get())
    }

    private fun validateDataStorageOptions() {
        dataStorageOptions.validate(commandLine!!)
    }

    private fun validateMiningParams() {
        miningOptions.validate(
            commandLine!!, genesisConfigOptionsSupplier.get(), isMergeEnabled, logger
        )
    }

    private fun validateP2POptions() {
        p2PDiscoveryOptions.validate(commandLine!!, networkInterfaceChecker!!)
    }

    protected open val networkInterfaceChecker: NetworkInterfaceChecker?
        /**
         * Returns a network interface checker that can be used to validate P2P options.
         *
         * @return A [P2PDiscoveryOptions.NetworkInterfaceChecker] that checks if a network
         * interface is available.
         */
        get() = NetworkInterfaceChecker { ipAddress: String? -> NetworkUtility.isNetworkInterfaceAvailable(ipAddress) }

    private fun validateGraphQlOptions() {
        graphQlOptions.validate(logger, commandLine)
    }

    private fun validateNatParams() {
        if (natMethod == NatMethod.AUTO && !unstableNatOptions.natMethodFallbackEnabled) {
            throw CommandLine.ParameterException(
                this.commandLine,
                "The `--Xnat-method-fallback-enabled` parameter cannot be used in AUTO mode. Either remove --Xnat-method-fallback-enabled"
                        + " or select another mode (via --nat--method=XXXX)"
            )
        }
    }

    private fun validateNetStatsParams() {
        if (Strings.isNullOrEmpty(ethstatsOptions.ethstatsUrl)
            && !ethstatsOptions.ethstatsContact.isEmpty()
        ) {
            throw CommandLine.ParameterException(
                this.commandLine,
                "The `--ethstats-contact` requires ethstats server URL to be provided. Either remove --ethstats-contact"
                        + " or provide a URL (via --ethstats=nodename:secret@host:port)"
            )
        }
    }

    private fun validateDnsOptionsParams() {
        if (!unstableDnsOptions.dnsEnabled && unstableDnsOptions.dnsUpdateEnabled) {
            throw CommandLine.ParameterException(
                this.commandLine,
                "The `--Xdns-update-enabled` requires dns to be enabled. Either remove --Xdns-update-enabled"
                        + " or specify dns is enabled (--Xdns-enabled)"
            )
        }
    }

    private fun ensureValidPeerBoundParams() {
        maxPeers = p2PDiscoveryOptions.maxPeers
        val isLimitRemoteWireConnectionsEnabled =
            p2PDiscoveryOptions.isLimitRemoteWireConnectionsEnabled
        if (isLimitRemoteWireConnectionsEnabled) {
            val fraction =
                Fraction.fromPercentage(p2PDiscoveryOptions.maxRemoteConnectionsPercentage).value
            Preconditions.checkState(
                fraction >= 0.0 && fraction <= 1.0,
                "Fraction of remote connections allowed must be between 0.0 and 1.0 (inclusive)."
            )
            maxRemoteInitiatedPeers = Math.round(fraction * maxPeers)
        } else {
            maxRemoteInitiatedPeers = maxPeers
        }
    }

    private fun validateRpcOptionsParams() {
        val configuredApis =
            Predicate { apiName: String ->
                Arrays.stream(RpcApis.entries.toTypedArray())
                    .anyMatch { builtInApi: RpcApis -> apiName == builtInApi.name }
                        || rpcEndpointServiceImpl.hasNamespace(apiName)
            }
        jsonRpcHttpOptions.validate(logger, commandLine!!, configuredApis)
    }

    private fun validateRpcWsOptions() {
        val configuredApis =
            Predicate { apiName: String ->
                Arrays.stream(RpcApis.entries.toTypedArray())
                    .anyMatch { builtInApi: RpcApis -> apiName == builtInApi.name }
                        || rpcEndpointServiceImpl.hasNamespace(apiName)
            }
        rpcWebsocketOptions.validate(logger, commandLine!!, configuredApis)
    }

    private fun validateChainDataPruningParams() {
        val chainDataPruningBlocksRetained =
            unstableChainPruningOptions.chainDataPruningBlocksRetained
        if (unstableChainPruningOptions.chainDataPruningEnabled) {
            val genesisConfigOptions = readGenesisConfigOptions()
            if (chainDataPruningBlocksRetained
                < unstableChainPruningOptions.chainDataPruningBlocksRetainedLimit
            ) {
                throw CommandLine.ParameterException(
                    this.commandLine,
                    "--Xchain-pruning-blocks-retained must be >= "
                            + unstableChainPruningOptions.chainDataPruningBlocksRetainedLimit
                )
            } else if (genesisConfigOptions.isPoa) {
                var epochLength = 0L
                var consensusMechanism = ""
                if (genesisConfigOptions.isIbft2) {
                    epochLength = genesisConfigOptions.bftConfigOptions.epochLength
                    consensusMechanism = "IBFT2"
                } else if (genesisConfigOptions.isQbft) {
                    epochLength = genesisConfigOptions.qbftConfigOptions.epochLength
                    consensusMechanism = "QBFT"
                } else if (genesisConfigOptions.isClique) {
                    epochLength = genesisConfigOptions.cliqueConfigOptions.epochLength
                    consensusMechanism = "Clique"
                }
                if (chainDataPruningBlocksRetained < epochLength) {
                    throw CommandLine.ParameterException(
                        this.commandLine,
                        String.format(
                            "--Xchain-pruning-blocks-retained(%d) must be >= epochlength(%d) for %s",
                            chainDataPruningBlocksRetained, epochLength, consensusMechanism
                        )
                    )
                }
            }
        }
    }

    private fun readGenesisConfig(): GenesisConfig {
        val effectiveGenesisFile = if (network == NetworkName.EPHEMERY)
            updateGenesis(genesisConfigOverrides.toMutableMap())
        else
            if (genesisFile != null)
                GenesisConfig.fromSource(genesisConfigSource(genesisFile))
            else
                GenesisConfig.fromResource(
                    Optional.ofNullable(network).orElse(NetworkName.MAINNET).genesisFile
                )
        return effectiveGenesisFile.withOverrides(genesisConfigOverrides)
    }

    private fun readGenesisConfigOptions(): GenesisConfigOptions {
        try {
            return genesisConfigSupplier.get().configOptions
        } catch (e: Exception) {
            throw CommandLine.ParameterException(
                this.commandLine, "Unable to load genesis file. " + e.cause
            )
        }
    }

    private fun issueOptionWarnings() {
        // Check that P2P options are able to work

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--p2p-enabled",
            !p2PDiscoveryOptions.p2pEnabled,
            mutableListOf(
                "--bootnodes",
                "--discovery-enabled",
                "--max-peers",
                "--banned-node-id",
                "--banned-node-ids",
                "--p2p-host",
                "--p2p-interface",
                "--p2p-port",
                "--remote-connections-max-percentage"
            )
        )

        if (SyncMode.FAST == syncMode) {
            logger.warn("FAST sync is deprecated. Recommend using SNAP sync instead.")
        }

        if (SyncMode.isFullSync(defaultSyncModeIfNotSet)
            && CommandLineUtils.isOptionSet(commandLine, "--sync-min-peers")
        ) {
            logger.warn("--sync-min-peers is ignored in FULL sync-mode")
        }

        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            "--Xcheckpoint-post-merge-enabled can only be used with CHECKPOINT sync-mode",
            defaultSyncModeIfNotSet == SyncMode.CHECKPOINT,
            listOf("--Xcheckpoint-post-merge-enabled")
        )

        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            "--Xsnapsync-synchronizer-flat option can only be used when --Xbonsai-full-flat-db-enabled is true",
            dataStorageOptions
                .toDomainObject()
                .pathBasedExtraStorageConfiguration
                .unstable
                .fullFlatDbEnabled,
            mutableListOf(
                "--Xsnapsync-synchronizer-flat-account-healed-count-per-request",
                "--Xsnapsync-synchronizer-flat-slot-healed-count-per-request"
            )
        )

        if (securityModuleName != DefaultCommandValues.DEFAULT_SECURITY_MODULE && nodePrivateKeyFileOption.nodePrivateKeyFile != null) {
            logger.warn(
                CommandLineUtils.DEPENDENCY_WARNING_MSG,
                "--node-private-key-file",
                "--security-module=" + DefaultCommandValues.DEFAULT_SECURITY_MODULE
            )
        }
    }

    @Throws(Exception::class)
    private fun configure() {
        p2PDiscoveryConfig = p2PDiscoveryOptions.toDomainObject()
        engineRPCConfig = engineRPCOptions.toDomainObject()
        checkPortClash()
        checkIfRequiredPortsAreAvailable()
        syncMode = defaultSyncModeIfNotSet
        versionCompatibilityProtection = defaultVersionCompatibilityProtectionIfNotSet

        ethNetworkConfig = updateNetworkConfig(network!!)

        jsonRpcConfiguration =
            jsonRpcHttpOptions.jsonRpcConfiguration(
                hostsAllowlist, p2PDiscoveryOptions.p2pHost, unstableRPCOptions.httpTimeoutSec
            )
        if (isEngineApiEnabled) {
            engineJsonRpcConfiguration = createEngineJsonRpcConfiguration()
        }
        graphQLConfiguration =
            graphQlOptions.graphQLConfiguration(
                hostsAllowlist, p2PDiscoveryOptions.p2pHost, unstableRPCOptions.httpTimeoutSec
            )

        webSocketConfiguration =
            rpcWebsocketOptions.webSocketConfiguration(
                hostsAllowlist, p2PDiscoveryConfig!!.p2pHost, unstableRPCOptions.wsTimeoutSec
            )
        jsonRpcIpcConfiguration =
            jsonRpcIpcConfiguration(
                unstableIpcOptions.isEnabled,
                unstableIpcOptions.ipcPath,
                unstableIpcOptions.rpcIpcApis
            )
        inProcessRpcConfiguration = inProcessRpcOptions.toDomainObject()
        dataStorageConfiguration = getDataStorageConfiguration()

        permissioningConfiguration = permissioningConfiguration()
        staticNodes = loadStaticNodes()

        val enodeURIs = ethNetworkConfig!!.bootNodes
        permissioningConfiguration!!
            .flatMap { obj: PermissioningConfiguration -> obj.localConfig }
            .ifPresent { p: LocalPermissioningConfiguration -> ensureAllNodesAreInAllowlist(enodeURIs, p) }

        permissioningConfiguration!!
            .flatMap { obj: PermissioningConfiguration -> obj.localConfig }
            .ifPresent { p: LocalPermissioningConfiguration -> ensureAllNodesAreInAllowlist(staticNodes!!, p) }
        metricsConfiguration = metricsConfiguration()

        instantiateSignatureAlgorithmFactory()

        logger.info(generateConfigurationOverview())
        logger.info("Security Module: {}", securityModuleName)
    }

    @Throws(Exception::class)
    private fun permissioningConfiguration(): Optional<PermissioningConfiguration> {
        return permissionsOptions.permissioningConfiguration(
            jsonRpcHttpOptions,
            rpcWebsocketOptions,
            enodeDnsConfiguration,
            dataDir(),
            logger,
            commandLine!!
        )
    }

    private fun jsonRpcIpcConfiguration(
        enabled: Boolean, ipcPath: Path?, rpcIpcApis: List<String>
    ): JsonRpcIpcConfiguration {
        val actualPath = ipcPath ?: getDefaultPath(dataDir())
        return JsonRpcIpcConfiguration(
            vertx!!.isNativeTransportEnabled && enabled, actualPath, rpcIpcApis
        )
    }

    private fun ensureAllNodesAreInAllowlist(
        enodeAddresses: Collection<EnodeURL>,
        permissioningConfiguration: LocalPermissioningConfiguration
    ) {
        try {
            areAllNodesInAllowlist(
                enodeAddresses, permissioningConfiguration
            )
        } catch (e: Exception) {
            throw CommandLine.ParameterException(this.commandLine, e.message)
        }
    }

    /**
     * Builds BesuController
     *
     * @return instance of BesuController
     */
    fun buildController(): BesuController? {
        try {
            return setupControllerBuilder().build()
        } catch (e: Exception) {
            throw CommandLine.ExecutionException(this.commandLine, e.message, e)
        }
    }

    /**
     * Builds BesuControllerBuilder which can be used to build BesuController
     *
     * @return instance of BesuControllerBuilder
     */
    fun setupControllerBuilder(): BesuControllerBuilder {
        pluginCommonConfiguration!!
            .init(dataDir(), dataDir().resolve(BesuController.DATABASE_PATH), getDataStorageConfiguration())
            .withMiningParameters(miningParametersSupplier.get())
            .withJsonRpcHttpOptions(jsonRpcHttpOptions)
        val storageProvider = keyValueStorageProvider(keyValueStorageName)
        val besuControllerBuilder =
            controllerBuilder
                .fromEthNetworkConfig(updateNetworkConfig(network!!), defaultSyncModeIfNotSet)
                .synchronizerConfiguration(buildSyncConfig())!!
                .ethProtocolConfiguration(unstableEthProtocolOptions.toDomainObject())!!
                .networkConfiguration(unstableNetworkingOptions.toDomainObject())
                .dataDirectory(dataDir())!!
                .dataStorageConfiguration(getDataStorageConfiguration()!!)!!
                .miningParameters(miningParametersSupplier.get())!!
                .transactionPoolConfiguration(buildTransactionPoolConfiguration())!!
                .nodeKey(NodeKey(securityModule()))!!
                .metricsSystem(besuComponent!!.metricsSystem as ObservableMetricsSystem)!!
                .messagePermissioningProviders(permissioningService.getMessagePermissioningProviders())!!
                .privacyParameters(privacyParameters())!!
                .clock(Clock.systemUTC())!!
                .isRevertReasonEnabled(isRevertReasonEnabled)!!
                .storageProvider(storageProvider)!!
                .isEarlyRoundChangeEnabled(unstableQbftOptions.isEarlyRoundChangeEnabled)
                .requiredBlocks(requiredBlocks)!!
                .reorgLoggingThreshold(reorgLoggingThreshold)!!
                .evmConfiguration(unstableEvmOptions.toDomainObject())!!
                .maxPeers(p2PDiscoveryOptions.maxPeers)
                .maxRemotelyInitiatedPeers(maxRemoteInitiatedPeers)
                .randomPeerPriority(p2PDiscoveryOptions.randomPeerPriority)
                .chainPruningConfiguration(unstableChainPruningOptions.toDomainObject())
                .cacheLastBlocks(numberOfBlocksToCache)
                .genesisStateHashCacheEnabled(genesisStateHashCacheEnabled)
                .apiConfiguration(apiConfigurationSupplier.get())
                .besuComponent(besuComponent)
        if (DataStorageFormat.BONSAI == getDataStorageConfiguration()!!.dataStorageFormat) {
            val subStorageConfiguration =
                getDataStorageConfiguration()!!.pathBasedExtraStorageConfiguration
            besuControllerBuilder.isParallelTxProcessingEnabled(
                subStorageConfiguration.unstable.isParallelTxProcessingEnabled
            )
        }
        return besuControllerBuilder
    }

    private fun createEngineJsonRpcConfiguration(): JsonRpcConfiguration {
        jsonRpcHttpOptions.checkDependencies(logger, commandLine!!)
        val engineConfig =
            jsonRpcHttpOptions.jsonRpcConfiguration(
                engineRPCConfig.engineHostsAllowlist,
                p2PDiscoveryConfig!!.p2pHost,
                unstableRPCOptions.wsTimeoutSec
            )
        engineConfig.port = engineRPCConfig.engineRpcPort
        engineConfig.setRpcApis(mutableListOf("ENGINE", "ETH"))
        engineConfig.isEnabled = isEngineApiEnabled
        if (!engineRPCConfig.isEngineAuthDisabled) {
            engineConfig.isAuthenticationEnabled = true
            engineConfig.authenticationAlgorithm = JwtAlgorithm.HS256
            if (Objects.nonNull(engineRPCConfig.engineJwtKeyFile)
                && Files.exists(engineRPCConfig.engineJwtKeyFile)
            ) {
                engineConfig.authenticationPublicKeyFile = engineRPCConfig.engineJwtKeyFile.toFile()
            } else {
                logger.warn(
                    "Engine API authentication enabled without key file. Expect ephemeral jwt.hex file in datadir"
                )
            }
        }
        return engineConfig
    }

    private fun checkPrivacyTlsOptionsDependencies() {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--privacy-tls-enabled",
            !privacyOptionGroup.isPrivacyTlsEnabled,
            mutableListOf(
                "--privacy-tls-keystore-file",
                "--privacy-tls-keystore-password-file",
                "--privacy-tls-known-enclave-file"
            )
        )
    }

    /**
     * Metrics Configuration for Besu
     *
     * @return instance of MetricsConfiguration.
     */
    fun metricsConfiguration(): MetricsConfiguration {
        if (metricsOptions.metricsEnabled && metricsOptions.metricsPushEnabled) {
            throw CommandLine.ParameterException(
                this.commandLine,
                "--metrics-enabled option and --metrics-push-enabled option can't be used at the same "
                        + "time.  Please refer to CLI reference for more details about this constraint."
            )
        }

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--metrics-enabled",
            !metricsOptions.metricsEnabled,
            mutableListOf("--metrics-host", "--metrics-port")
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--metrics-push-enabled",
            !metricsOptions.metricsPushEnabled,
            mutableListOf(
                "--metrics-push-host",
                "--metrics-push-port",
                "--metrics-push-interval",
                "--metrics-push-prometheus-job"
            )
        )

        metricsOptions.setMetricCategoryRegistry(metricCategoryRegistry)

        metricsOptions.validate(commandLine!!)

        val metricsConfigurationBuilder =
            metricsOptions.toDomainObject()
        metricsConfigurationBuilder
            .host(
                if (Strings.isNullOrEmpty(metricsOptions.metricsHost))
                    p2PDiscoveryOptions.p2pHost
                else
                    metricsOptions.metricsHost
            )
            .pushHost(
                if (Strings.isNullOrEmpty(metricsOptions.metricsPushHost))
                    p2PDiscoveryOptions.autoDiscoverDefaultIP()!!.hostAddress
                else
                    metricsOptions.metricsPushHost
            )
            .hostsAllowlist(hostsAllowlist)
        val metricsConfiguration = metricsConfigurationBuilder.build()
        metricCategoryRegistry.setMetricsConfiguration(metricsConfiguration)
        return metricsConfiguration
    }

    private fun privacyParameters(): PrivacyParameters {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--privacy-enabled",
            !privacyOptionGroup.isPrivacyEnabled,
            mutableListOf("--privacy-multi-tenancy-enabled", "--privacy-tls-enabled")
        )

        CommandLineUtils.checkMultiOptionDependencies(
            logger,
            commandLine,
            "--privacy-url and/or --privacy-public-key-file ignored because none of --privacy-enabled was defined.",
            java.util.List.of(!privacyOptionGroup.isPrivacyEnabled),
            listOf("--privacy-url", "--privacy-public-key-file")
        )

        checkPrivacyTlsOptionsDependencies()

        val privacyParametersBuilder = PrivacyParameters.Builder()
        if (java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyEnabled) {
            logger.warn("--privacy-enabled and related options are " + PRIVACY_DEPRECATION_PREFIX)
            val errorSuffix = "cannot be enabled with privacy."
            if (syncMode == SyncMode.FAST) {
                throw CommandLine.ParameterException(commandLine, String.format("%s %s", "Fast sync", errorSuffix))
            }
            if (syncMode == SyncMode.SNAP) {
                throw CommandLine.ParameterException(commandLine, String.format("%s %s", "Snap sync", errorSuffix))
            }
            if (syncMode == SyncMode.CHECKPOINT) {
                throw CommandLine.ParameterException(
                    commandLine, String.format("%s %s", "Checkpoint sync", errorSuffix)
                )
            }
            if (getDataStorageConfiguration()!!.dataStorageFormat == DataStorageFormat.BONSAI) {
                throw CommandLine.ParameterException(commandLine, String.format("%s %s", "Bonsai", errorSuffix))
            }

            if (java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyMultiTenancyEnabled
                && java.lang.Boolean.FALSE == jsonRpcConfiguration!!.isAuthenticationEnabled
                && java.lang.Boolean.FALSE == webSocketConfiguration!!.isAuthenticationEnabled
            ) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled"
                )
            }

            privacyParametersBuilder.setEnabled(true)
            privacyParametersBuilder.setEnclaveUrl(privacyOptionGroup.privacyUrl)
            privacyParametersBuilder.setMultiTenancyEnabled(
                privacyOptionGroup.isPrivacyMultiTenancyEnabled
            )
            privacyParametersBuilder.setFlexiblePrivacyGroupsEnabled(
                privacyOptionGroup.isFlexiblePrivacyGroupsEnabled
            )
            privacyParametersBuilder.setPrivacyPluginEnabled(
                unstablePrivacyPluginOptions.isPrivacyPluginEnabled
            )
            privacyParametersBuilder.setPrivateNonceAlwaysIncrementsEnabled(
                privacyOptionGroup.isPrivateNonceAlwaysIncrementsEnabled
            )

            val hasPrivacyPublicKey = privacyOptionGroup.privacyPublicKeyFile != null

            if (hasPrivacyPublicKey
                && java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyMultiTenancyEnabled
            ) {
                throw CommandLine.ParameterException(
                    commandLine, "Privacy multi-tenancy and privacy public key cannot be used together"
                )
            }

            if (!hasPrivacyPublicKey && !privacyOptionGroup.isPrivacyMultiTenancyEnabled && !unstablePrivacyPluginOptions.isPrivacyPluginEnabled) {
                throw CommandLine.ParameterException(
                    commandLine, "Please specify Enclave public key file path to enable privacy"
                )
            }

            if (hasPrivacyPublicKey
                && java.lang.Boolean.FALSE == privacyOptionGroup.isPrivacyMultiTenancyEnabled
            ) {
                try {
                    privacyParametersBuilder.setPrivacyUserIdUsingFile(
                        privacyOptionGroup.privacyPublicKeyFile
                    )
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        commandLine, "Problem with privacy-public-key-file: " + e.message, e
                    )
                } catch (e: IllegalArgumentException) {
                    throw CommandLine.ParameterException(
                        commandLine, "Contents of privacy-public-key-file invalid: " + e.message, e
                    )
                }
            }

            privacyParametersBuilder.setPrivateKeyPath(
                privacyOptionGroup.privateMarkerTransactionSigningKeyPath
            )
            privacyParametersBuilder.setStorageProvider(
                privacyKeyStorageProvider("$keyValueStorageName-privacy")
            )
            if (java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyTlsEnabled) {
                privacyParametersBuilder.setPrivacyKeyStoreFile(privacyOptionGroup.privacyKeyStoreFile)
                privacyParametersBuilder.setPrivacyKeyStorePasswordFile(
                    privacyOptionGroup.privacyKeyStorePasswordFile
                )
                privacyParametersBuilder.setPrivacyTlsKnownEnclaveFile(
                    privacyOptionGroup.privacyTlsKnownEnclaveFile
                )
            }
            privacyParametersBuilder.setEnclaveFactory(EnclaveFactory(vertx))
        }

        if (java.lang.Boolean.FALSE == privacyOptionGroup.isPrivacyEnabled && anyPrivacyApiEnabled()) {
            logger.warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.")
        }

        privacyParametersBuilder.setPrivacyService(privacyPluginService)
        val privacyParameters = privacyParametersBuilder.build()

        if (java.lang.Boolean.TRUE == privacyOptionGroup.isPrivacyEnabled) {
            preSynchronizationTaskRunner.addTask(
                PrivateDatabaseMigrationPreSyncTask(
                    privacyParameters, privacyOptionGroup.migratePrivateDatabase
                )
            )
        }

        return privacyParameters
    }

    private fun anyPrivacyApiEnabled(): Boolean {
        return jsonRpcHttpOptions.rpcHttpApis.contains(RpcApis.EEA.name)
                || rpcWebsocketOptions.rpcWsApis.contains(RpcApis.EEA.name)
                || jsonRpcHttpOptions.rpcHttpApis.contains(RpcApis.PRIV.name)
                || rpcWebsocketOptions.rpcWsApis.contains(RpcApis.PRIV.name)
    }

    private fun privacyKeyStorageProvider(name: String): PrivacyKeyValueStorageProvider {
        return PrivacyKeyValueStorageProviderBuilder()
            .withStorageFactory(privacyKeyValueStorageFactory(name))
            .withCommonConfiguration(pluginCommonConfiguration)
            .withMetricsSystem(metricsSystem)
            .build()
    }

    private fun privacyKeyValueStorageFactory(name: String): PrivacyKeyValueStorageFactory {
        return storageService
            .getByName(name)
            .orElseThrow { StorageException("No KeyValueStorageFactory found for key: $name") } as PrivacyKeyValueStorageFactory
    }

    private fun keyValueStorageProvider(name: String): KeyValueStorageProvider? {
        if (this.keyValueStorageProvider == null) {
            this.keyValueStorageProvider =
                KeyValueStorageProviderBuilder()
                    .withStorageFactory(
                        storageService
                            .getByName(name)
                            .orElseThrow {
                                StorageException(
                                    "No KeyValueStorageFactory found for key: $name"
                                )
                            })
                    .withCommonConfiguration(pluginCommonConfiguration)
                    .withMetricsSystem(metricsSystem)
                    .build()
        }
        return this.keyValueStorageProvider
    }

    val storageProvider: StorageProvider?
        /**
         * Get the storage provider
         *
         * @return the storage provider
         */
        get() = keyValueStorageProvider(keyValueStorageName)

    private fun buildSyncConfig(): SynchronizerConfiguration {
        return unstableSynchronizerOptions
            .toDomainObject()
            .syncMode(syncMode)
            .syncMinimumPeerCount(syncMinPeerCount)
            .build()
    }

    private fun buildTransactionPoolConfiguration(): TransactionPoolConfiguration {
        transactionPoolOptions.setPluginTransactionValidatorService(transactionValidatorServiceImpl)
        val txPoolConf = transactionPoolOptions.toDomainObject()
        val txPoolConfBuilder =
            ImmutableTransactionPoolConfiguration.builder()
                .from(txPoolConf)
                .saveFile((dataPath.resolve(txPoolConf.saveFile.path).toFile()))

        if (genesisConfigOptionsSupplier.get().isZeroBaseFee) {
            logger.warn(
                "Forcing price bump for transaction replacement to 0, since we are on a zero basefee network"
            )
            txPoolConfBuilder.priceBump(Percentage.ZERO)
        }

        if (miningParametersSupplier.get().minTransactionGasPrice == Wei.ZERO
            && !transactionPoolOptions.isPriceBumpSet(commandLine!!)
        ) {
            logger.warn(
                "Forcing price bump for transaction replacement to 0, since min-gas-price is set to 0"
            )
            txPoolConfBuilder.priceBump(Percentage.ZERO)
        }

        if (miningParametersSupplier
                .get()
                .minTransactionGasPrice
                .lessThan(txPoolConf.minGasPrice)
        ) {
            if (transactionPoolOptions.isMinGasPriceSet(commandLine!!)) {
                throw CommandLine.ParameterException(
                    commandLine, "tx-pool-min-gas-price cannot be greater than the value of min-gas-price"
                )
            } else {
                // for backward compatibility, if tx-pool-min-gas-price is not set, we adjust its value
                // to be the same as min-gas-price, so the behavior is as before this change, and we notify
                // the user of the change
                logger.warn(
                    ("Forcing tx-pool-min-gas-price="
                            + miningParametersSupplier.get().minTransactionGasPrice.toDecimalString()
                            + ", since it cannot be greater than the value of min-gas-price")
                )
                txPoolConfBuilder.minGasPrice(miningParametersSupplier.get().minTransactionGasPrice)
            }
        }

        return txPoolConfBuilder.build()
    }

    private val miningParameters: MiningConfiguration
        get() {
            miningOptions.setTransactionSelectionService(transactionSelectionServiceImpl)
            val miningParameters = miningOptions.toDomainObject()
            getGenesisBlockPeriodSeconds(genesisConfigOptionsSupplier.get())
                .ifPresent { blockPeriodSeconds: Int -> miningParameters.setBlockPeriodSeconds(blockPeriodSeconds) }
            initMiningParametersMetrics(miningParameters)

            return miningParameters
        }

    private val apiConfiguration: ApiConfiguration
        get() {
            validateApiOptions()
            return apiConfigurationOptions.apiConfiguration()
        }

    /**
     * Get the data storage configuration
     *
     * @return the data storage configuration
     */
    fun getDataStorageConfiguration(): DataStorageConfiguration? {
        if (dataStorageConfiguration == null) {
            dataStorageConfiguration = dataStorageOptions.toDomainObject()
        }

        if (SyncMode.FULL == defaultSyncModeIfNotSet
            && DataStorageFormat.BONSAI == dataStorageConfiguration!!.dataStorageFormat
        ) {
            val pathBasedExtraStorageConfiguration =
                dataStorageConfiguration!!.pathBasedExtraStorageConfiguration
            if (pathBasedExtraStorageConfiguration.limitTrieLogsEnabled) {
                if (CommandLineUtils.isOptionSet(
                        commandLine, PathBasedExtraStorageOptions.LIMIT_TRIE_LOGS_ENABLED
                    )
                ) {
                    throw CommandLine.ParameterException(
                        commandLine,
                        String.format(
                            "Cannot enable %s with --sync-mode=%s and --data-storage-format=%s. You must set %s or use a different sync-mode",
                            PathBasedExtraStorageOptions.LIMIT_TRIE_LOGS_ENABLED,
                            SyncMode.FULL,
                            DataStorageFormat.BONSAI,
                            PathBasedExtraStorageOptions.LIMIT_TRIE_LOGS_ENABLED + "=false"
                        )
                    )
                }

                dataStorageConfiguration =
                    ImmutableDataStorageConfiguration.copyOf(dataStorageConfiguration)
                        .withPathBasedExtraStorageConfiguration(
                            ImmutablePathBasedExtraStorageConfiguration.copyOf(
                                dataStorageConfiguration!!.pathBasedExtraStorageConfiguration
                            )
                                .withLimitTrieLogsEnabled(false)
                        )
                logger.warn(
                    "Forcing {}, since it cannot be enabled with --sync-mode={} and --data-storage-format={}.",
                    PathBasedExtraStorageOptions.LIMIT_TRIE_LOGS_ENABLED + "=false",
                    SyncMode.FULL,
                    DataStorageFormat.BONSAI
                )
            }
        }
        return dataStorageConfiguration
    }

    private fun initMiningParametersMetrics(miningConfiguration: MiningConfiguration) {
        MiningParametersMetrics(metricsSystem, miningConfiguration)
    }

    private fun getGenesisBlockPeriodSeconds(
        genesisConfigOptions: GenesisConfigOptions
    ): OptionalInt {
        if (genesisConfigOptions.isClique) {
            return OptionalInt.of(genesisConfigOptions.cliqueConfigOptions.blockPeriodSeconds)
        }

        if (genesisConfigOptions.isIbft2) {
            return OptionalInt.of(genesisConfigOptions.bftConfigOptions.blockPeriodSeconds)
        }

        if (genesisConfigOptions.isQbft) {
            return OptionalInt.of(genesisConfigOptions.qbftConfigOptions.blockPeriodSeconds)
        }

        return OptionalInt.empty()
    }

    // Blockchain synchronization from peers.
    private fun synchronize(
        controller: BesuController?,
        p2pEnabled: Boolean,
        peerDiscoveryEnabled: Boolean,
        ethNetworkConfig: EthNetworkConfig?,
        p2pAdvertisedHost: String,
        p2pListenInterface: String,
        p2pListenPort: Int,
        graphQLConfiguration: GraphQLConfiguration?,
        jsonRpcConfiguration: JsonRpcConfiguration?,
        engineJsonRpcConfiguration: JsonRpcConfiguration?,
        webSocketConfiguration: WebSocketConfiguration?,
        jsonRpcIpcConfiguration: JsonRpcIpcConfiguration?,
        inProcessRpcConfiguration: InProcessRpcConfiguration?,
        apiConfiguration: ApiConfiguration,
        metricsConfiguration: MetricsConfiguration?,
        permissioningConfiguration: Optional<PermissioningConfiguration>?,
        staticNodes: Collection<EnodeURL>?,
        pidPath: Path?
    ): Runner {
        Preconditions.checkNotNull(runnerBuilder)

        val runner =
            runnerBuilder
                .vertx(vertx)
                .besuController(controller)
                .p2pEnabled(p2pEnabled)
                .natMethod(natMethod)
                .natMethodFallbackEnabled(unstableNatOptions.natMethodFallbackEnabled)
                .discoveryEnabled(peerDiscoveryEnabled)
                .ethNetworkConfig(ethNetworkConfig)
                .permissioningConfiguration(permissioningConfiguration)
                .p2pAdvertisedHost(p2pAdvertisedHost)
                .p2pListenInterface(p2pListenInterface)
                .p2pListenPort(p2pListenPort)
                .networkingConfiguration(unstableNetworkingOptions.toDomainObject())
                .graphQLConfiguration(graphQLConfiguration)
                .jsonRpcConfiguration(jsonRpcConfiguration)
                .engineJsonRpcConfiguration(engineJsonRpcConfiguration)
                .webSocketConfiguration(webSocketConfiguration)
                .jsonRpcIpcConfiguration(jsonRpcIpcConfiguration)
                .inProcessRpcConfiguration(inProcessRpcConfiguration)
                .apiConfiguration(apiConfiguration)
                .pidPath(pidPath)
                .dataDir(dataDir())
                .bannedNodeIds(p2PDiscoveryConfig!!.bannedNodeIds)
                .metricsSystem(besuComponent!!.metricsSystem as ObservableMetricsSystem)
                .permissioningService(permissioningService)
                .metricsConfiguration(metricsConfiguration)
                .staticNodes(staticNodes)
                .identityString(identityString)
                .besuPluginContext(besuPluginContext)
                .autoLogBloomCaching(autoLogBloomCachingEnabled)
                .ethstatsOptions(ethstatsOptions)
                .storageProvider(keyValueStorageProvider(keyValueStorageName))
                .rpcEndpointService(rpcEndpointServiceImpl)
                .enodeDnsConfiguration(enodeDnsConfiguration)
                .allowedSubnets(p2PDiscoveryConfig!!.allowedSubnets)
                .poaDiscoveryRetryBootnodes(p2PDiscoveryConfig!!.poaDiscoveryRetryBootnodes)
                .build()

        addShutdownHook(runner)

        return runner
    }

    /**
     * Builds Vertx instance from VertxOptions. Visible for testing.
     *
     * @param vertxOptions Instance of VertxOptions
     * @return Instance of Vertx.
     */
    @VisibleForTesting
    protected open fun createVertx(vertxOptions: VertxOptions?): Vertx? {
        return Vertx.vertx(vertxOptions)
    }

    private fun createVertxOptions(metricsSystem: MetricsSystem): VertxOptions {
        return VertxOptions()
            .setPreferNativeTransport(true)
            .setMetricsOptions(
                MetricsOptions()
                    .setEnabled(true)
                    .setFactory(VertxMetricsAdapterFactory(metricsSystem))
            )
    }

    private fun addShutdownHook(runner: Runner) {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread(
                    {
                        try {
                            besuPluginContext.stopPlugins()
                            runner.close()
                            LogConfigurator.shutdown()
                        } catch (e: Exception) {
                            logger.error("Failed to stop Besu")
                        }
                    },
                    "BesuCommand-Shutdown-Hook"
                )
            )
    }

    private fun updateNetworkConfig(network: NetworkName): EthNetworkConfig {
        val builder =
            EthNetworkConfig.Builder(getNetworkConfig(network))

        if (genesisFile != null) {
            if (commandLine!!.parseResult.hasMatchedOption("network")) {
                throw CommandLine.ParameterException(
                    this.commandLine,
                    "--network option and --genesis-file option can't be used at the same time.  Please "
                            + "refer to CLI reference for more details about this constraint."
                )
            }

            if (networkId == null) {
                // If no chain id is found in the genesis, use mainnet network id
                try {
                    builder.setNetworkId(
                        genesisConfigOptionsSupplier
                            .get()
                            .chainId
                            .orElse(getNetworkConfig(NetworkName.MAINNET).networkId)
                    )
                } catch (e: DecodeException) {
                    throw CommandLine.ParameterException(
                        this.commandLine, String.format("Unable to parse genesis file %s.", genesisFile), e
                    )
                } catch (e: ArithmeticException) {
                    throw CommandLine.ParameterException(
                        this.commandLine,
                        "No networkId specified and chainId in "
                                + "genesis file is too large to be used as a networkId"
                    )
                }
            }

            if (p2PDiscoveryOptions.bootNodes == null) {
                builder.setBootNodes(ArrayList())
            }
            builder.setDnsDiscoveryUrl(null)
        }

        builder.setGenesisConfig(genesisConfigSupplier.get())

        if (networkId != null) {
            builder.setNetworkId(networkId)
        }
        // ChainId update is required for Ephemery network
        if (network == NetworkName.EPHEMERY) {
            val chainId = genesisConfigOverrides["chainId"]
            builder.setNetworkId(BigInteger(chainId))
        }
        if (p2PDiscoveryOptions.discoveryDnsUrl != null) {
            builder.setDnsDiscoveryUrl(p2PDiscoveryOptions.discoveryDnsUrl!!)
        } else {
            val discoveryDnsUrlFromGenesis =
                genesisConfigOptionsSupplier.get().discoveryOptions.discoveryDnsUrl
            discoveryDnsUrlFromGenesis.ifPresent { dnsDiscoveryUrl: String? ->
                builder.setDnsDiscoveryUrl(
                    dnsDiscoveryUrl!!
                )
            }
        }

        var listBootNodes: List<EnodeURL>? = null
        if (p2PDiscoveryOptions.bootNodes != null) {
            try {
                listBootNodes = buildEnodes(p2PDiscoveryOptions.bootNodes!!, enodeDnsConfiguration!!)
            } catch (e: IllegalArgumentException) {
                throw CommandLine.ParameterException(commandLine, e.message)
            }
        } else {
            val bootNodesFromGenesis =
                genesisConfigOptionsSupplier.get().discoveryOptions.bootNodes
            if (bootNodesFromGenesis.isPresent) {
                listBootNodes = buildEnodes(bootNodesFromGenesis.get(), enodeDnsConfiguration!!)
            }
        }
        if (listBootNodes != null) {
            if (!p2PDiscoveryOptions.peerDiscoveryEnabled) {
                logger.warn("Discovery disabled: bootnodes will be ignored.")
            }
            DiscoveryConfiguration.assertValidBootnodes(listBootNodes)
            builder.setBootNodes(listBootNodes)
        }
        return builder.build()
    }

    private fun genesisConfigSource(genesisFile: File): URL {
        try {
            return genesisFile.toURI().toURL()
        } catch (e: IOException) {
            throw CommandLine.ParameterException(
                this.commandLine, String.format("Unable to load genesis URL %s.", genesisFile), e
            )
        }
    }

    /**
     * Returns data directory used by Besu. Visible as it is accessed by other subcommands.
     *
     * @return Path representing data directory.
     */
    fun dataDir(): Path {
        return dataPath.toAbsolutePath()
    }

    private fun securityModule(): SecurityModule {
        return securityModuleService
            .getByName(securityModuleName)
            .orElseThrow {
                RuntimeException(
                    "Security Module not found: $securityModuleName"
                )
            }
            .get()
    }

    private fun resolveNodePrivateKeyFile(nodePrivateKeyFile: File?): File {
        return Optional.ofNullable(nodePrivateKeyFile)
            .orElseGet { KeyPairUtil.getDefaultKeyFile(dataDir()) }
    }

    val metricsSystem: MetricsSystem
        /**
         * Metrics System used by Besu
         *
         * @return Instance of MetricsSystem
         */
        get() = besuComponent!!.metricsSystem

    @Throws(IOException::class)
    private fun loadStaticNodes(): Set<EnodeURL> {
        val staticNodesPath: Path
        if (staticNodesFile != null) {
            staticNodesPath = staticNodesFile.toAbsolutePath()
            if (!staticNodesPath.toFile().exists()) {
                throw CommandLine.ParameterException(
                    commandLine, String.format("Static nodes file %s does not exist", staticNodesPath)
                )
            }
        } else {
            val staticNodesFilename = "static-nodes.json"
            staticNodesPath = dataDir().resolve(staticNodesFilename)
        }
        logger.debug("Static Nodes file: {}", staticNodesPath)
        val staticNodes =
            StaticNodesParser.fromPath(staticNodesPath, enodeDnsConfiguration)
        logger.info("Connecting to {} static nodes.", staticNodes.size)
        logger.debug("Static Nodes = {}", staticNodes)
        return staticNodes
    }

    private fun buildEnodes(
        bootNodes: List<String>, enodeDnsConfiguration: EnodeDnsConfiguration
    ): List<EnodeURL>? {
        return bootNodes.stream()
            .filter { bootNode: String -> !bootNode.isEmpty() }
            .map { bootNode: String? -> EnodeURLImpl.fromString(bootNode, enodeDnsConfiguration) }
            .collect(Collectors.toList())
    }

    /**
     * Besu CLI Parameters exception handler used by VertX. Visible for testing.
     *
     * @return instance of BesuParameterExceptionHandler
     */
    fun parameterExceptionHandler(): BesuParameterExceptionHandler {
        return BesuParameterExceptionHandler(Supplier { this.logLevel!! })
    }

    /**
     * Returns BesuExecutionExceptionHandler. Visible as it is used in testing.
     *
     * @return instance of BesuExecutionExceptionHandler used by Vertx.
     */
    fun executionExceptionHandler(): BesuExecutionExceptionHandler {
        return BesuExecutionExceptionHandler()
    }

    private fun checkPortClash() {
        effectivePorts.stream()
            .filter { obj: Int? -> Objects.nonNull(obj) }
            .filter { port: Int -> port > 0 }
            .forEach { port: Int ->
                if (!allocatedPorts.add(port)) {
                    throw CommandLine.ParameterException(
                        commandLine,
                        ("Port number '"
                                + port
                                + "' has been specified multiple times. Please review the supplied configuration.")
                    )
                }
            }
    }

    /**
     * Check if required ports are available
     *
     * @throws InvalidConfigurationException if ports are not available.
     */
    protected open fun checkIfRequiredPortsAreAvailable() {
        val unavailablePorts: MutableList<Int> = ArrayList()
        effectivePorts.stream()
            .filter { obj: Int? -> Objects.nonNull(obj) }
            .filter { port: Int -> port > 0 }
            .forEach { port: Int ->
                if (port == p2PDiscoveryConfig!!.p2pPort
                    && (NetworkUtility.isPortUnavailableForTcp(port)
                            || NetworkUtility.isPortUnavailableForUdp(port))
                ) {
                    unavailablePorts.add(port)
                }
                if (port != p2PDiscoveryConfig!!.p2pPort && NetworkUtility.isPortUnavailableForTcp(port)) {
                    unavailablePorts.add(port)
                }
            }
        if (!unavailablePorts.isEmpty()) {
            throw InvalidConfigurationException(
                ("Port(s) '"
                        + unavailablePorts
                        + "' already in use. Check for other processes using the port(s).")
            )
        }
    }

    private val effectivePorts: List<Int>
        /**
         * * Gets the list of effective ports (ports that are enabled).
         *
         * @return The list of effective ports
         */
        get() {
            val effectivePorts: MutableList<Int> = ArrayList()
            addPortIfEnabled(effectivePorts, p2PDiscoveryOptions.p2pPort, p2PDiscoveryOptions.p2pEnabled)
            addPortIfEnabled(
                effectivePorts, graphQlOptions.graphQLHttpPort, graphQlOptions.isGraphQLHttpEnabled
            )
            addPortIfEnabled(
                effectivePorts, jsonRpcHttpOptions.rpcHttpPort, jsonRpcHttpOptions.isRpcHttpEnabled
            )
            addPortIfEnabled(
                effectivePorts, rpcWebsocketOptions.rpcWsPort, rpcWebsocketOptions.isRpcWsEnabled
            )
            addPortIfEnabled(effectivePorts, engineRPCConfig.engineRpcPort, isEngineApiEnabled)
            addPortIfEnabled(
                effectivePorts, metricsOptions.metricsPort, metricsOptions.metricsEnabled
            )
            addPortIfEnabled(
                effectivePorts,
                miningParametersSupplier.get().stratumPort,
                miningParametersSupplier.get().isStratumMiningEnabled
            )
            return effectivePorts
        }

    /**
     * Adds port to the specified list only if enabled.
     *
     * @param ports The list of ports
     * @param port The port value
     * @param enabled true if enabled, false otherwise
     */
    private fun addPortIfEnabled(
        ports: MutableList<Int>, port: Int, enabled: Boolean
    ) {
        if (enabled) {
            ports.add(port)
        }
    }

    @get:VisibleForTesting
    val logLevel: String?
        get() = loggingLevelOption.logLevel

    private fun instantiateSignatureAlgorithmFactory() {
        if (SignatureAlgorithmFactory.isInstanceSet()) {
            return
        }

        val ecCurve = ecCurveFromGenesisFile

        if (ecCurve.isEmpty) {
            SignatureAlgorithmFactory.setDefaultInstance()
            return
        }

        try {
            SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create(ecCurve.get()))
        } catch (e: IllegalArgumentException) {
            throw CommandLine.InitializationException(
                "Invalid genesis file configuration for ecCurve. " + e.message
            )
        }
    }

    private val ecCurveFromGenesisFile: Optional<String>
        get() {
            if (genesisFile == null) {
                return Optional.empty()
            }
            return genesisConfigOptionsSupplier.get().ecCurve
        }

    protected open val genesisConfigOptions: GenesisConfigOptions?
        /**
         * Return the genesis config options
         *
         * @return the genesis config options
         */
        get() = genesisConfigOptionsSupplier.get()

    private fun setMergeConfigOptions() {
        MergeConfiguration.setMergeEnabled(
            genesisConfigOptionsSupplier.get().terminalTotalDifficulty.isPresent
        )
    }

    /** Set ignorable segments in RocksDB Storage Provider plugin.  */
    fun setIgnorableStorageSegments() {
        if (!unstableChainPruningOptions.chainDataPruningEnabled) {
            rocksDBPlugin!!.addIgnorableSegmentIdentifier(KeyValueSegmentIdentifier.CHAIN_PRUNER_STATE)
        }
    }

    private fun validatePostMergeCheckpointBlockRequirements() {
        val synchronizerConfiguration =
            unstableSynchronizerOptions.toDomainObject().build()
        val terminalTotalDifficulty =
            genesisConfigOptionsSupplier.get().terminalTotalDifficulty
        val checkpointConfigOptions =
            genesisConfigOptionsSupplier.get().checkpointOptions
        if (synchronizerConfiguration.isCheckpointPostMergeEnabled) {
            if (!checkpointConfigOptions.isValid) {
                throw InvalidConfigurationException(
                    "PoS checkpoint sync requires a checkpoint block configured in the genesis file"
                )
            }
            terminalTotalDifficulty.ifPresentOrElse(
                { ttd: UInt256 ->
                    if ((UInt256.fromHexString(checkpointConfigOptions.totalDifficulty.get())
                                == UInt256.ZERO)
                        && ttd == UInt256.ZERO
                    ) {
                        throw InvalidConfigurationException(
                            "PoS checkpoint sync can't be used with TTD = 0 and checkpoint totalDifficulty = 0"
                        )
                    }
                    if (UInt256.fromHexString(checkpointConfigOptions.totalDifficulty.get())
                            .lessThan(ttd)
                    ) {
                        throw InvalidConfigurationException(
                            "PoS checkpoint sync requires a block with total difficulty greater or equal than the TTD"
                        )
                    }
                },
                {
                    throw InvalidConfigurationException(
                        "PoS checkpoint sync requires TTD in the genesis file"
                    )
                })
        }
    }

    private val isMergeEnabled: Boolean
        get() = MergeConfiguration.isMergeEnabled()

    private val isEngineApiEnabled: Boolean
        get() = engineRPCConfig.overrideEngineRpcEnabled || isMergeEnabled

    private val defaultSyncModeIfNotSet: SyncMode
        get() = Optional.ofNullable(syncMode)
            .orElse(
                if (genesisFile == null && !privacyOptionGroup.isPrivacyEnabled && Optional.ofNullable(network)
                        .map { obj: NetworkName -> obj.canSnapSync() }
                        .orElse(false)
                )
                    SyncMode.SNAP
                else
                    SyncMode.FULL)

    private val defaultVersionCompatibilityProtectionIfNotSet: Boolean
        get() =// Version compatibility protection is enabled by default for non-named networks
            Optional.ofNullable(versionCompatibilityProtection) // if we have a specific genesis file or custom network id, we are not using a named network
                .orElse(genesisFile != null || networkId != null)

    private fun generateConfigurationOverview(): String {
        val builder = ConfigurationOverviewBuilder(logger)

        if (environment != null) {
            builder.setEnvironment(environment)
        }

        if (network != null) {
            builder.setNetwork(network.normalize())
        }

        if (profile != null) {
            builder.setProfile(profile)
        }

        builder.setHasCustomGenesis(genesisFile != null)
        if (genesisFile != null) {
            builder.setCustomGenesis(genesisFile.absolutePath)
        }
        builder.setNetworkId(ethNetworkConfig!!.networkId)

        builder
            .setDataStorage(dataStorageOptions.normalizeDataStorageFormat())
            .setSyncMode(syncMode!!.normalize())
            .setSyncMinPeers(syncMinPeerCount)

        if (jsonRpcConfiguration != null && jsonRpcConfiguration!!.isEnabled) {
            builder
                .setRpcPort(jsonRpcConfiguration!!.port)
                .setRpcHttpApis(jsonRpcConfiguration!!.rpcApis)
        }

        if (engineJsonRpcConfiguration != null && engineJsonRpcConfiguration!!.isEnabled) {
            builder
                .setEnginePort(engineJsonRpcConfiguration!!.port)
                .setEngineApis(engineJsonRpcConfiguration!!.rpcApis)
            if (engineJsonRpcConfiguration!!.isAuthenticationEnabled) {
                if (engineJsonRpcConfiguration!!.authenticationPublicKeyFile != null) {
                    builder.setEngineJwtFile(
                        engineJsonRpcConfiguration!!.authenticationPublicKeyFile.absolutePath
                    )
                } else {
                    // default ephemeral jwt created later
                    builder.setEngineJwtFile(
                        dataDir().toAbsolutePath().toString() + "/" + EngineAuthService.EPHEMERAL_JWT_FILE
                    )
                }
            }
        }

        if (rocksDBPlugin!!.isHighSpecEnabled) {
            builder.setHighSpecEnabled()
        }

        if (DataStorageFormat.BONSAI == getDataStorageConfiguration()!!.dataStorageFormat) {
            val subStorageConfiguration =
                getDataStorageConfiguration()!!.pathBasedExtraStorageConfiguration
            if (subStorageConfiguration.limitTrieLogsEnabled) {
                builder.setLimitTrieLogsEnabled()
                builder.setTrieLogRetentionLimit(subStorageConfiguration.maxLayersToLoad)
                builder.setTrieLogsPruningWindowSize(subStorageConfiguration.trieLogPruningWindowSize)
            }
        }

        builder.setSnapServerEnabled(unstableSynchronizerOptions.isSnapsyncServerEnabled)

        builder.setTxPoolImplementation(buildTransactionPoolConfiguration().txPoolImplementation)
        builder.setWorldStateUpdateMode(unstableEvmOptions.toDomainObject().worldUpdaterMode)

        builder.setPluginContext(this.besuPluginContext)

        return builder.build()
    }

    companion object {
        private const val PRIVACY_DEPRECATION_PREFIX =
            "Deprecated. Tessera-based privacy is deprecated. See CHANGELOG for alternative options. "

        @JvmStatic
        @CommandLine.Option(
            names = ["--color-enabled"],
            description = ["Force color output to be enabled/disabled (default: colorized only if printing to console)"]
        )
        val colorEnabled: Optional<Boolean>? = null

        @Throws(IOException::class)
        private fun isGroupMember(userName: String, group: GroupPrincipal): Boolean {
            // Get the groups of the user by executing 'id -Gn username'
            val process = Runtime.getRuntime().exec(arrayOf("id", "-Gn", userName))
            val reader =
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))

            // Read the output of the command
            val line = reader.readLine()
            var isMember = false
            if (line != null) {
                // Split the groups
                val userGroups = Splitter.on(" ").split(line)

                // Check if any of the user's groups match the file's group
                for (grp in userGroups) {
                    if (grp == group.name) {
                        isMember = true
                        break
                    }
                }
            }
            return isMember
        }
    }
}
