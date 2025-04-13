/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.converter.DurationMillisConverter
import org.hyperledger.besu.cli.converter.FractionConverter
import org.hyperledger.besu.cli.converter.PercentageConverter
import org.hyperledger.besu.cli.options.TransactionPoolOptions.Layered
import org.hyperledger.besu.cli.options.TransactionPoolOptions.Sequenced
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.TransactionType
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService
import org.hyperledger.besu.util.number.Fraction
import org.hyperledger.besu.util.number.Percentage
import picocli.CommandLine
import java.io.File
import java.time.Duration
import java.util.*

/** The Transaction pool Cli stable options.  */
class TransactionPoolOptions private constructor() :
    CLIOptions<TransactionPoolConfiguration?> {
    private var transactionPoolValidatorService: TransactionPoolValidatorService? = null

    @CommandLine.Option(
        names = [TX_POOL_IMPLEMENTATION],
        paramLabel = "<Enum>",
        description = ["The Transaction Pool implementation to use(default: \${DEFAULT-VALUE})"],
        arity = "0..1"
    )
    private var txPoolImplementation = TransactionPoolConfiguration.Implementation.LAYERED

    @CommandLine.Option(
        names = [TX_POOL_NO_LOCAL_PRIORITY, TX_POOL_DISABLE_LOCALS],
        paramLabel = "<Boolean>",
        description = ["Set to true if senders of transactions sent via RPC should not have priority (default: \${DEFAULT-VALUE})"],
        fallbackValue = "true",
        arity = "0..1"
    )
    private var noLocalPriority = TransactionPoolConfiguration.DEFAULT_NO_LOCAL_PRIORITY

    @CommandLine.Option(
        names = [TX_POOL_ENABLE_SAVE_RESTORE],
        paramLabel = "<Boolean>",
        description = ["Set to true to enable saving the txpool content to file on shutdown and reloading it on startup (default: \${DEFAULT-VALUE})"],
        fallbackValue = "true",
        arity = "0..1"
    )
    private var saveRestoreEnabled = TransactionPoolConfiguration.DEFAULT_ENABLE_SAVE_RESTORE

    @CommandLine.Option(
        names = [TX_POOL_SAVE_FILE],
        paramLabel = "<STRING>",
        description = ["If saving the txpool content is enabled, define a custom path for the save file (default: \${DEFAULT-VALUE} in the data-dir)"],
        arity = "1"
    )
    private var saveFile: File = TransactionPoolConfiguration.DEFAULT_SAVE_FILE

    @CommandLine.Option(
        names = [TX_POOL_PRICE_BUMP],
        paramLabel = "<Percentage>",
        converter = [PercentageConverter::class],
        description = ["Price bump percentage to replace an already existing transaction  (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private var priceBump: Percentage = TransactionPoolConfiguration.DEFAULT_PRICE_BUMP

    @CommandLine.Option(
        names = [TX_POOL_BLOB_PRICE_BUMP],
        paramLabel = "<Percentage>",
        converter = [PercentageConverter::class],
        description = ["Blob price bump percentage to replace an already existing transaction blob tx (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private var blobPriceBump: Percentage = TransactionPoolConfiguration.DEFAULT_BLOB_PRICE_BUMP

    @CommandLine.Option(
        names = [RPC_TX_FEECAP],
        description = ["Maximum transaction fees (in Wei) accepted for transaction submitted through RPC (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private var txFeeCap: Wei = TransactionPoolConfiguration.DEFAULT_RPC_TX_FEE_CAP

    @CommandLine.Option(
        names = [STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG],
        paramLabel = "<Boolean>",
        description = ["Require transactions submitted via JSON-RPC to use replay protection in accordance with EIP-155 (default: \${DEFAULT-VALUE})"],
        fallbackValue = "true",
        arity = "0..1"
    )
    private var strictTxReplayProtectionEnabled = false

    @CommandLine.Option(
        names = [TX_POOL_PRIORITY_SENDERS],
        split = ",",
        paramLabel = "Comma separated list of addresses",
        description = ["Pending transactions sent exclusively by these addresses, from any source, are prioritized and only evicted after all others. If not specified, then only the senders submitting transactions via RPC have priority (default: \${DEFAULT-VALUE})"],
        arity = "1..*"
    )
    private var prioritySenders: Set<Address> = TransactionPoolConfiguration.DEFAULT_PRIORITY_SENDERS

    @CommandLine.Option(
        names = [TX_POOL_MIN_GAS_PRICE],
        paramLabel = "<Wei>",
        description = [("Transactions with gas price (in Wei) lower than this minimum will not be accepted into the txpool"
                + "(not to be confused with min-gas-price, that is applied on block creation) (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private var minGasPrice: Wei = TransactionPoolConfiguration.DEFAULT_TX_POOL_MIN_GAS_PRICE

    @CommandLine.ArgGroup(validate = false, heading = "@|bold Tx Pool Layered Implementation Options|@%n")
    private val layeredOptions = Layered()

    internal class Layered {
        @CommandLine.Option(
            names = [TX_POOL_LAYER_MAX_CAPACITY],
            paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
            description = ["Max amount of memory space, in bytes, that any layer within the transaction pool could occupy (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolLayerMaxCapacity: Long =
            TransactionPoolConfiguration.DEFAULT_PENDING_TRANSACTIONS_LAYER_MAX_CAPACITY_BYTES

        @CommandLine.Option(
            names = [TX_POOL_MAX_PRIORITIZED],
            paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
            description = ["Max number of pending transactions that are prioritized and thus kept sorted (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolMaxPrioritized: Int = TransactionPoolConfiguration.DEFAULT_MAX_PRIORITIZED_TRANSACTIONS

        @CommandLine.Option(
            names = [TX_POOL_MAX_PRIORITIZED_BY_TYPE],
            paramLabel = "MAP<TYPE,INTEGER>",
            split = ",",
            description = ["Max number of pending transactions, of a specific type, that are prioritized and thus kept sorted (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolMaxPrioritizedByType: Map<TransactionType, Int> =
            TransactionPoolConfiguration.DEFAULT_MAX_PRIORITIZED_TRANSACTIONS_BY_TYPE

        @CommandLine.Option(
            names = [TX_POOL_MAX_FUTURE_BY_SENDER],
            paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
            description = ["Max number of future pending transactions allowed for a single sender (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolMaxFutureBySender: Int = TransactionPoolConfiguration.DEFAULT_MAX_FUTURE_BY_SENDER

        @CommandLine.Option(
            names = [TX_POOL_MIN_SCORE],
            paramLabel = "<Byte>",
            description = [("Remove a pending transaction from the txpool if its score is lower than this value."
                    + "Accepts values between -128 and 127 (default: \${DEFAULT-VALUE})")],
            arity = "1"
        )
        var minScore: Byte = TransactionPoolConfiguration.DEFAULT_TX_POOL_MIN_SCORE

        companion object {
            private const val TX_POOL_LAYER_MAX_CAPACITY = "--tx-pool-layer-max-capacity"
            private const val TX_POOL_MAX_PRIORITIZED = "--tx-pool-max-prioritized"
            private const val TX_POOL_MAX_PRIORITIZED_BY_TYPE = "--tx-pool-max-prioritized-by-type"
            private const val TX_POOL_MAX_FUTURE_BY_SENDER = "--tx-pool-max-future-by-sender"
            private const val TX_POOL_MIN_SCORE = "--tx-pool-min-score"
        }
    }

    @CommandLine.ArgGroup(validate = false, heading = "@|bold Tx Pool Sequenced Implementation Options|@%n")
    private val sequencedOptions = Sequenced()

    internal class Sequenced {
        @CommandLine.Option(
            names = [TX_POOL_RETENTION_HOURS],
            paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
            description = ["Maximum retention period of pending transactions in hours (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var pendingTxRetentionPeriod: Int = TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS

        @CommandLine.Option(
            names = [TX_POOL_LIMIT_BY_ACCOUNT_PERCENTAGE],
            paramLabel = DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP,
            converter = [FractionConverter::class],
            description = ["Maximum portion of the transaction pool which a single account may occupy with future transactions (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolLimitByAccountPercentage: Fraction =
            TransactionPoolConfiguration.DEFAULT_LIMIT_TX_POOL_BY_ACCOUNT_PERCENTAGE

        @CommandLine.Option(
            names = [TX_POOL_MAX_SIZE],
            paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
            description = ["Maximum number of pending transactions that will be kept in the transaction pool (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txPoolMaxSize: Int = TransactionPoolConfiguration.DEFAULT_MAX_PENDING_TRANSACTIONS

        companion object {
            private const val TX_POOL_RETENTION_HOURS = "--tx-pool-retention-hours"
            private const val TX_POOL_LIMIT_BY_ACCOUNT_PERCENTAGE = "--tx-pool-limit-by-account-percentage"
            private const val TX_POOL_MAX_SIZE = "--tx-pool-max-size"
        }
    }

    @CommandLine.ArgGroup(validate = false)
    private val unstableOptions = Unstable()

    internal class Unstable {
        @CommandLine.Option(
            names = [TX_MESSAGE_KEEP_ALIVE_SEC_FLAG],
            paramLabel = "<INTEGER>",
            hidden = true,
            description = ["Keep alive of incoming transaction messages in seconds (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var txMessageKeepAliveSeconds: Int = TransactionPoolConfiguration.Unstable.DEFAULT_TX_MSG_KEEP_ALIVE

        @CommandLine.Option(
            names = [ETH65_TX_ANNOUNCED_BUFFERING_PERIOD_FLAG],
            paramLabel = "<LONG>",
            converter = [DurationMillisConverter::class],
            hidden = true,
            description = ["The period for which the announced transactions remain in the buffer before being requested from the peers in milliseconds (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var eth65TrxAnnouncedBufferingPeriod: Duration =
            TransactionPoolConfiguration.Unstable.ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD

        @CommandLine.Option(
            names = [MAX_TRACKED_SEEN_TXS_PER_PEER],
            paramLabel = "<LONG>",
            hidden = true,
            description = ["The number of exchanged txs that are remembered with each peer, to minimize broadcasting duplicates (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var maxTrackedSeenTxsPerPeer: Int = TransactionPoolConfiguration.Unstable.DEFAULT_MAX_TRACKED_SEEN_TXS_PER_PEER

        @CommandLine.Option(
            names = [PEER_TRACKER_FORGET_EVICTED_TXS_FLAG],
            paramLabel = "<BOOLEAN>",
            hidden = true,
            description = ["Whether txs evicted due to the pool being full should be removed from peer tracker cache that checks for already known txs (default: false on layered and true on sequenced txpool)"],
            arity = "0..1",
            fallbackValue = "true"
        )
        var peerTrackerForgetEvictedTxs: Boolean? = null

        companion object {
            private const val TX_MESSAGE_KEEP_ALIVE_SEC_FLAG = "--Xincoming-tx-messages-keep-alive-seconds"
            private const val ETH65_TX_ANNOUNCED_BUFFERING_PERIOD_FLAG =
                "--Xeth65-tx-announced-buffering-period-milliseconds"
            private const val MAX_TRACKED_SEEN_TXS_PER_PEER = "--Xmax-tracked-seen-txs-per-peer"
            private const val PEER_TRACKER_FORGET_EVICTED_TXS_FLAG = "--Xpeer-tracker-forget-evicted-txs"
        }
    }

    /**
     * Set the plugin txpool validator service
     *
     * @param transactionPoolValidatorService the plugin txpool validator service
     */
    fun setPluginTransactionValidatorService(
        transactionPoolValidatorService: TransactionPoolValidatorService?
    ) {
        this.transactionPoolValidatorService = transactionPoolValidatorService
    }

    /**
     * Validate that there are no inconsistencies in the specified options. For example that the
     * options are valid for the selected implementation.
     *
     * @param commandLine the full commandLine to check all the options specified by the user
     * @param genesisConfigOptions the genesis config options
     */
    fun validate(
        commandLine: CommandLine?, genesisConfigOptions: GenesisConfigOptions
    ) {
        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            "Could not use legacy or sequenced transaction pool options with layered implementation",
            txPoolImplementation != TransactionPoolConfiguration.Implementation.LAYERED,
            CommandLineUtils.getCLIOptionNames(Sequenced::class.java)
        )

        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            "Could not use layered transaction pool options with legacy or sequenced implementation",
            txPoolImplementation != TransactionPoolConfiguration.Implementation.LEGACY && txPoolImplementation != TransactionPoolConfiguration.Implementation.SEQUENCED,
            CommandLineUtils.getCLIOptionNames(Layered::class.java)
        )

        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            "Price bump option is not compatible with zero base fee market",
            !genesisConfigOptions.isZeroBaseFee,
            java.util.List.of(TX_POOL_PRICE_BUMP)
        )
    }

    override fun toDomainObject(): TransactionPoolConfiguration {
        return ImmutableTransactionPoolConfiguration.builder()
            .txPoolImplementation(txPoolImplementation)
            .enableSaveRestore(saveRestoreEnabled)
            .noLocalPriority(noLocalPriority)
            .priceBump(priceBump)
            .blobPriceBump(blobPriceBump)
            .txFeeCap(txFeeCap)
            .saveFile(saveFile)
            .strictTransactionReplayProtectionEnabled(strictTxReplayProtectionEnabled)
            .prioritySenders(prioritySenders)
            .minGasPrice(minGasPrice)
            .pendingTransactionsLayerMaxCapacityBytes(layeredOptions.txPoolLayerMaxCapacity)
            .maxPrioritizedTransactions(layeredOptions.txPoolMaxPrioritized)
            .maxPrioritizedTransactionsByType(layeredOptions.txPoolMaxPrioritizedByType)
            .maxFutureBySender(layeredOptions.txPoolMaxFutureBySender)
            .minScore(layeredOptions.minScore)
            .txPoolLimitByAccountPercentage(sequencedOptions.txPoolLimitByAccountPercentage)
            .txPoolMaxSize(sequencedOptions.txPoolMaxSize)
            .pendingTxRetentionPeriod(sequencedOptions.pendingTxRetentionPeriod)
            .transactionPoolValidatorService(transactionPoolValidatorService)
            .unstable(
                ImmutableTransactionPoolConfiguration.Unstable.builder()
                    .txMessageKeepAliveSeconds(unstableOptions.txMessageKeepAliveSeconds)
                    .eth65TrxAnnouncedBufferingPeriod(unstableOptions.eth65TrxAnnouncedBufferingPeriod)
                    .maxTrackedSeenTxsPerPeer(unstableOptions.maxTrackedSeenTxsPerPeer)
                    .peerTrackerForgetEvictedTxs(
                        Optional.ofNullable(unstableOptions.peerTrackerForgetEvictedTxs)
                            .orElse(deriveDefaultPeersTrackerForgetEvictedTxs(txPoolImplementation))
                    )
                    .build()
            )
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, TransactionPoolOptions())
    }

    /**
     * Is price bump option set?
     *
     * @param commandLine the command line
     * @return true is tx-pool-price-bump is set
     */
    fun isPriceBumpSet(commandLine: CommandLine): Boolean {
        return CommandLineUtils.isOptionSet(commandLine, TX_POOL_PRICE_BUMP)
    }

    /**
     * Is min gas price option set?
     *
     * @param commandLine the command line
     * @return true if tx-pool-min-gas-price is set
     */
    fun isMinGasPriceSet(commandLine: CommandLine): Boolean {
        return CommandLineUtils.isOptionSet(commandLine, TX_POOL_MIN_GAS_PRICE)
    }

    companion object {
        private const val TX_POOL_IMPLEMENTATION = "--tx-pool"

        /** Use TX_POOL_NO_LOCAL_PRIORITY instead  */
        @Deprecated("")
        private const val TX_POOL_DISABLE_LOCALS = "--tx-pool-disable-locals"

        private const val TX_POOL_NO_LOCAL_PRIORITY = "--tx-pool-no-local-priority"
        private const val TX_POOL_ENABLE_SAVE_RESTORE = "--tx-pool-enable-save-restore"
        private const val TX_POOL_SAVE_FILE = "--tx-pool-save-file"
        private const val TX_POOL_PRICE_BUMP = "--tx-pool-price-bump"
        private const val TX_POOL_BLOB_PRICE_BUMP = "--tx-pool-blob-price-bump"
        private const val RPC_TX_FEECAP = "--rpc-tx-feecap"
        private const val STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG = "--strict-tx-replay-protection-enabled"
        private const val TX_POOL_PRIORITY_SENDERS = "--tx-pool-priority-senders"
        private const val TX_POOL_MIN_GAS_PRICE = "--tx-pool-min-gas-price"

        /**
         * Create transaction pool options.
         *
         * @return the transaction pool options
         */
        @JvmStatic
        fun create(): TransactionPoolOptions {
            val txPoolOptions = TransactionPoolOptions()
            txPoolOptions.unstableOptions.peerTrackerForgetEvictedTxs =
                deriveDefaultPeersTrackerForgetEvictedTxs(txPoolOptions.txPoolImplementation)
            return txPoolOptions
        }

        /**
         * Create Transaction Pool Options from Transaction Pool Configuration.
         *
         * @param config the Transaction Pool Configuration
         * @return the transaction pool options
         */
        @JvmStatic
        fun fromConfig(config: TransactionPoolConfiguration): TransactionPoolOptions {
            val options = create()
            options.txPoolImplementation = config.txPoolImplementation
            options.saveRestoreEnabled = config.enableSaveRestore
            options.noLocalPriority = config.noLocalPriority
            options.priceBump = config.priceBump
            options.blobPriceBump = config.blobPriceBump
            options.txFeeCap = config.txFeeCap
            options.saveFile = config.saveFile
            options.strictTxReplayProtectionEnabled = config.strictTransactionReplayProtectionEnabled
            options.prioritySenders = config.prioritySenders
            options.minGasPrice = config.minGasPrice
            options.layeredOptions.txPoolLayerMaxCapacity =
                config.pendingTransactionsLayerMaxCapacityBytes
            options.layeredOptions.txPoolMaxPrioritized = config.maxPrioritizedTransactions
            options.layeredOptions.txPoolMaxPrioritizedByType =
                config.maxPrioritizedTransactionsByType
            options.layeredOptions.txPoolMaxFutureBySender = config.maxFutureBySender
            options.layeredOptions.minScore = config.minScore
            options.sequencedOptions.txPoolLimitByAccountPercentage =
                config.txPoolLimitByAccountPercentage
            options.sequencedOptions.txPoolMaxSize = config.txPoolMaxSize
            options.sequencedOptions.pendingTxRetentionPeriod = config.pendingTxRetentionPeriod
            options.transactionPoolValidatorService = config.transactionPoolValidatorService
            options.unstableOptions.txMessageKeepAliveSeconds =
                config.unstable.txMessageKeepAliveSeconds
            options.unstableOptions.eth65TrxAnnouncedBufferingPeriod =
                config.unstable.eth65TrxAnnouncedBufferingPeriod
            options.unstableOptions.maxTrackedSeenTxsPerPeer =
                config.unstable.maxTrackedSeenTxsPerPeer
            options.unstableOptions.peerTrackerForgetEvictedTxs =
                config.unstable.peerTrackerForgetEvictedTxs
            return options
        }

        private fun deriveDefaultPeersTrackerForgetEvictedTxs(
            txPoolImplementation: TransactionPoolConfiguration.Implementation
        ): Boolean {
            return when (txPoolImplementation) {
                TransactionPoolConfiguration.Implementation.LEGACY, TransactionPoolConfiguration.Implementation.SEQUENCED -> true
                TransactionPoolConfiguration.Implementation.LAYERED -> false
            }
        }
    }
}
