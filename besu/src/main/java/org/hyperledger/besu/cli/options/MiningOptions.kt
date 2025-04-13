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

import com.google.common.base.Preconditions
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.converter.PositiveNumberConverter
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.plugin.services.TransactionSelectionService
import org.hyperledger.besu.util.number.PositiveNumber
import org.slf4j.Logger
import picocli.CommandLine

/** The Mining CLI options.  */
class MiningOptions private constructor() : CLIOptions<MiningConfiguration?> {
    @CommandLine.Option(
        names = ["--miner-enabled"],
        description = [DEPRECATION_PREFIX + "Set if node will perform mining (default: \${DEFAULT-VALUE})"]
    )
    private var isMiningEnabled = false

    @CommandLine.Option(
        names = ["--miner-stratum-enabled"], description = [(DEPRECATION_PREFIX
                + "Set if node will perform Stratum mining (default: \${DEFAULT-VALUE})."
                + " Compatible with Proof of Work (PoW) only."
                + " Requires the network option (--network) to be set to CLASSIC.")]
    )
    private var iStratumMiningEnabled = false

    @CommandLine.Option(
        names = ["--miner-stratum-host"], description = [(DEPRECATION_PREFIX
                + "Host for Stratum network mining service (default: \${DEFAULT-VALUE})")]
    )
    private var stratumNetworkInterface = "0.0.0.0"

    @CommandLine.Option(
        names = ["--miner-stratum-port"],
        description = [DEPRECATION_PREFIX + "Stratum port binding (default: \${DEFAULT-VALUE})"]
    )
    private var stratumPort = 8008

    @CommandLine.Option(
        names = ["--miner-coinbase"],
        description = [("Account to which mining rewards are paid. You must specify a valid coinbase if "
                + "mining is enabled using --miner-enabled option")],
        arity = "1"
    )
    private var coinbase: Address? = null

    @CommandLine.Option(
        names = ["--miner-extra-data"],
        description = [("A hex string representing the (32) bytes to be included in the extra data "
                + "field of a mined block (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private var extraData: Bytes = MiningConfiguration.MutableInitValues.DEFAULT_EXTRA_DATA

    @CommandLine.Option(
        names = ["--min-block-occupancy-ratio"],
        description = ["Minimum occupancy ratio for a mined block (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private var minBlockOccupancyRatio = MiningConfiguration.MutableInitValues.DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO

    @CommandLine.Option(
        names = ["--min-gas-price"],
        description = [("Minimum price (in Wei) offered by a transaction for it to be included in a mined "
                + "block (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private var minTransactionGasPrice: Wei = MiningConfiguration.MutableInitValues.DEFAULT_MIN_TRANSACTION_GAS_PRICE

    @CommandLine.Option(
        names = ["--min-priority-fee"],
        description = [("Minimum priority fee per gas (in Wei) offered by a transaction for it to be included in a "
                + "block (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private var minPriorityFeePerGas: Wei = MiningConfiguration.MutableInitValues.DEFAULT_MIN_PRIORITY_FEE_PER_GAS

    @CommandLine.Option(
        names = ["--target-gas-limit"], description = [("Sets target gas limit per block."
                + " If set, each block's gas limit will approach this setting over time.")]
    )
    private var targetGasLimit: Long? = null

    @CommandLine.Option(
        names = ["--block-txs-selection-max-time"],
        converter = [PositiveNumberConverter::class],
        description = [(DEPRECATION_PREFIX
                + "Specifies the maximum time, in milliseconds, that could be spent selecting transactions to be included in the block."
                + " Not compatible with PoA networks, see poa-block-txs-selection-max-time. (default: \${DEFAULT-VALUE})")]
    )
    private var nonPoaBlockTxsSelectionMaxTime: PositiveNumber =
        MiningConfiguration.DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME

    @CommandLine.Option(
        names = ["--poa-block-txs-selection-max-time"],
        converter = [PositiveNumberConverter::class],
        description = [("Specifies the maximum time that could be spent selecting transactions to be included in the block, as a percentage of the fixed block time of the PoA network."
                + " To be only used on PoA networks, for other networks see block-txs-selection-max-time."
                + " (default: \${DEFAULT-VALUE})")]
    )
    private var poaBlockTxsSelectionMaxTime: PositiveNumber =
        MiningConfiguration.DEFAULT_POA_BLOCK_TXS_SELECTION_MAX_TIME

    @CommandLine.ArgGroup(validate = false)
    private val unstableOptions = Unstable()

    internal class Unstable {
        @CommandLine.Option(
            hidden = true, names = ["--Xminer-remote-sealers-limit"], description = [(DEPRECATION_PREFIX
                    + "Limits the number of remote sealers that can submit their hashrates (default: \${DEFAULT-VALUE})")]
        )
        var remoteSealersLimit: Int = MiningConfiguration.Unstable.DEFAULT_REMOTE_SEALERS_LIMIT

        @CommandLine.Option(
            hidden = true, names = ["--Xminer-remote-sealers-hashrate-ttl"], description = [(DEPRECATION_PREFIX
                    + "Specifies the lifetime of each entry in the cache. An entry will be automatically deleted if no update has been received before the deadline (default: \${DEFAULT-VALUE} minutes)")]
        )
        var remoteSealersTimeToLive: Long = MiningConfiguration.Unstable.DEFAULT_REMOTE_SEALERS_TTL

        @CommandLine.Option(
            hidden = true, names = ["--Xminer-pow-job-ttl"], description = [(DEPRECATION_PREFIX
                    + "Specifies the time PoW jobs are kept in cache and will accept a solution from miners (default: \${DEFAULT-VALUE} milliseconds)")]
        )
        var powJobTimeToLive: Long = MiningConfiguration.Unstable.DEFAULT_POW_JOB_TTL

        @CommandLine.Option(
            hidden = true, names = ["--Xmax-ommers-depth"], description = [(DEPRECATION_PREFIX
                    + "Specifies the depth of ommer blocks to accept when receiving solutions (default: \${DEFAULT-VALUE})")]
        )
        var maxOmmersDepth: Int = MiningConfiguration.Unstable.DEFAULT_MAX_OMMERS_DEPTH

        @CommandLine.Option(
            hidden = true, names = ["--Xminer-stratum-extranonce"], description = [(DEPRECATION_PREFIX
                    + "Extranonce for Stratum network miners (default: \${DEFAULT-VALUE})")]
        )
        var stratumExtranonce: String = "080c"

        @CommandLine.Option(
            hidden = true,
            names = ["--Xpos-block-creation-max-time"],
            description = ["Specifies the maximum time, in milliseconds, a PoS block creation jobs is allowed to run. Must be positive and ≤ 12000 (default: \${DEFAULT-VALUE} milliseconds)"]
        )
        var posBlockCreationMaxTime: Long = MiningConfiguration.Unstable.DEFAULT_POS_BLOCK_CREATION_MAX_TIME

        @CommandLine.Option(
            hidden = true,
            names = ["--Xpos-block-creation-repetition-min-duration"],
            description = [("If a PoS block creation repetition takes less than this duration, in milliseconds,"
                    + " then it waits before next repetition. Must be positive and ≤ 2000 (default: \${DEFAULT-VALUE} milliseconds)")]
        )
        var posBlockCreationRepetitionMinDuration: Long =
            MiningConfiguration.Unstable.DEFAULT_POS_BLOCK_CREATION_REPETITION_MIN_DURATION
    }

    private var transactionSelectionService: TransactionSelectionService? = null

    /**
     * Set the transaction selection service
     *
     * @param transactionSelectionService the transaction selection service
     */
    fun setTransactionSelectionService(
        transactionSelectionService: TransactionSelectionService?
    ) {
        this.transactionSelectionService = transactionSelectionService
    }

    /**
     * Validate that there are no inconsistencies in the specified options. For example that the
     * options are valid for the selected implementation.
     *
     * @param commandLine the full commandLine to check all the options specified by the user
     * @param genesisConfigOptions is EthHash?
     * @param isMergeEnabled is the Merge enabled?
     * @param logger the logger
     */
    fun validate(
        commandLine: CommandLine,
        genesisConfigOptions: GenesisConfigOptions,
        isMergeEnabled: Boolean,
        logger: Logger
    ) {
        if (java.lang.Boolean.TRUE == isMiningEnabled) {
            logger.warn("PoW consensus is deprecated. See CHANGELOG for alternative options.")
        }
        if (java.lang.Boolean.TRUE == isMiningEnabled && coinbase == null) {
            throw CommandLine.ParameterException(
                commandLine,
                "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled) "
                        + "or specify the beneficiary of mining (via --miner-coinbase <Address>)"
            )
        }
        if (java.lang.Boolean.FALSE == isMiningEnabled && java.lang.Boolean.TRUE == iStratumMiningEnabled) {
            throw CommandLine.ParameterException(
                commandLine,
                "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) "
                        + "or specify mining is enabled (--miner-enabled)"
            )
        }

        // Check that block producer options work
        if (!isMergeEnabled && genesisConfigOptions.isEthHash) {
            CommandLineUtils.checkOptionDependencies(
                logger,
                commandLine,
                "--miner-enabled",
                !isMiningEnabled,
                mutableListOf(
                    "--miner-coinbase",
                    "--min-gas-price",
                    "--min-priority-fee",
                    "--min-block-occupancy-ratio",
                    "--miner-extra-data"
                )
            )

            // Check that mining options are able to work
            CommandLineUtils.checkOptionDependencies(
                logger,
                commandLine,
                "--miner-enabled",
                !isMiningEnabled,
                mutableListOf(
                    "--miner-stratum-enabled",
                    "--Xminer-remote-sealers-limit",
                    "--Xminer-remote-sealers-hashrate-ttl"
                )
            )
        }

        if (unstableOptions.posBlockCreationMaxTime <= 0
            || unstableOptions.posBlockCreationMaxTime > MiningConfiguration.Unstable.DEFAULT_POS_BLOCK_CREATION_MAX_TIME
        ) {
            throw CommandLine.ParameterException(
                commandLine,
                "--Xpos-block-creation-max-time must be positive and ≤ "
                        + MiningConfiguration.Unstable.DEFAULT_POS_BLOCK_CREATION_MAX_TIME
            )
        }

        if (unstableOptions.posBlockCreationRepetitionMinDuration <= 0
            || unstableOptions.posBlockCreationRepetitionMinDuration > 2000
        ) {
            throw CommandLine.ParameterException(
                commandLine, "--Xpos-block-creation-repetition-min-duration must be positive and ≤ 2000"
            )
        }

        if (genesisConfigOptions.isPoa) {
            CommandLineUtils.failIfOptionDoesntMeetRequirement(
                commandLine,
                "--block-txs-selection-max-time can't be used with PoA networks,"
                        + " see poa-block-txs-selection-max-time instead",
                false,
                listOf("--block-txs-selection-max-time")
            )
        } else {
            CommandLineUtils.failIfOptionDoesntMeetRequirement(
                commandLine,
                "--poa-block-txs-selection-max-time can be only used with PoA networks,"
                        + " see --block-txs-selection-max-time instead",
                false,
                listOf("--poa-block-txs-selection-max-time")
            )
        }
    }

    override fun toDomainObject(): MiningConfiguration {
        Preconditions.checkNotNull(
            transactionSelectionService,
            "transactionSelectionService must be set before using this object"
        )

        val updatableInitValuesBuilder =
            ImmutableMiningConfiguration.MutableInitValues.builder()
                .isMiningEnabled(isMiningEnabled)
                .extraData(extraData)
                .minTransactionGasPrice(minTransactionGasPrice)
                .minPriorityFeePerGas(minPriorityFeePerGas)
                .minBlockOccupancyRatio(minBlockOccupancyRatio)

        if (targetGasLimit != null) {
            updatableInitValuesBuilder.targetGasLimit(targetGasLimit!!)
        }
        if (coinbase != null) {
            updatableInitValuesBuilder.coinbase(coinbase)
        }

        return ImmutableMiningConfiguration.builder()
            .transactionSelectionService(transactionSelectionService)
            .mutableInitValues(updatableInitValuesBuilder.build())
            .isStratumMiningEnabled(iStratumMiningEnabled)
            .stratumNetworkInterface(stratumNetworkInterface)
            .stratumPort(stratumPort)
            .nonPoaBlockTxsSelectionMaxTime(nonPoaBlockTxsSelectionMaxTime)
            .poaBlockTxsSelectionMaxTime(poaBlockTxsSelectionMaxTime)
            .unstable(
                ImmutableMiningConfiguration.Unstable.builder()
                    .remoteSealersLimit(unstableOptions.remoteSealersLimit)
                    .remoteSealersTimeToLive(unstableOptions.remoteSealersTimeToLive)
                    .powJobTimeToLive(unstableOptions.powJobTimeToLive)
                    .maxOmmerDepth(unstableOptions.maxOmmersDepth)
                    .stratumExtranonce(unstableOptions.stratumExtranonce)
                    .posBlockCreationMaxTime(unstableOptions.posBlockCreationMaxTime)
                    .posBlockCreationRepetitionMinDuration(
                        unstableOptions.posBlockCreationRepetitionMinDuration
                    )
                    .build()
            )
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, MiningOptions())
    }

    companion object {
        private const val DEPRECATION_PREFIX =
            "Deprecated. PoW consensus is deprecated. See CHANGELOG for alternative options. "

        /**
         * Create mining options.
         *
         * @return the mining options
         */
        @JvmStatic
        fun create(): MiningOptions {
            return MiningOptions()
        }

        @JvmStatic
        fun fromConfig(miningConfiguration: MiningConfiguration): MiningOptions {
            val miningOptions = create()
            miningOptions.setTransactionSelectionService(
                miningConfiguration.transactionSelectionService
            )
            miningOptions.isMiningEnabled = miningConfiguration.isMiningEnabled
            miningOptions.iStratumMiningEnabled = miningConfiguration.isStratumMiningEnabled
            miningOptions.stratumNetworkInterface = miningConfiguration.stratumNetworkInterface
            miningOptions.stratumPort = miningConfiguration.stratumPort
            miningOptions.extraData = miningConfiguration.extraData
            miningOptions.minTransactionGasPrice = miningConfiguration.minTransactionGasPrice
            miningOptions.minPriorityFeePerGas = miningConfiguration.minPriorityFeePerGas
            miningOptions.minBlockOccupancyRatio = miningConfiguration.minBlockOccupancyRatio
            miningOptions.nonPoaBlockTxsSelectionMaxTime =
                miningConfiguration.nonPoaBlockTxsSelectionMaxTime
            miningOptions.poaBlockTxsSelectionMaxTime =
                miningConfiguration.poaBlockTxsSelectionMaxTime

            miningOptions.unstableOptions.remoteSealersLimit =
                miningConfiguration.unstable.remoteSealersLimit
            miningOptions.unstableOptions.remoteSealersTimeToLive =
                miningConfiguration.unstable.remoteSealersTimeToLive
            miningOptions.unstableOptions.powJobTimeToLive =
                miningConfiguration.unstable.powJobTimeToLive
            miningOptions.unstableOptions.maxOmmersDepth =
                miningConfiguration.unstable.maxOmmerDepth
            miningOptions.unstableOptions.stratumExtranonce =
                miningConfiguration.unstable.stratumExtranonce
            miningOptions.unstableOptions.posBlockCreationMaxTime =
                miningConfiguration.unstable.posBlockCreationMaxTime
            miningOptions.unstableOptions.posBlockCreationRepetitionMinDuration =
                miningConfiguration.unstable.posBlockCreationRepetitionMinDuration

            miningConfiguration.coinbase.ifPresent { coinbase: Address? ->
                miningOptions.coinbase = coinbase
            }
            miningConfiguration.targetGasLimit.ifPresent { tgl: Long -> miningOptions.targetGasLimit = tgl }
            return miningOptions
        }
    }
}
