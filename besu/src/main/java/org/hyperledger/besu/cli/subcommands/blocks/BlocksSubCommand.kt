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
package org.hyperledger.besu.cli.subcommands.blocks

import com.google.common.base.Preconditions
import jakarta.validation.constraints.NotBlank
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.chainexport.RlpBlockExporter
import org.hyperledger.besu.chainimport.Era1BlockImporter
import org.hyperledger.besu.chainimport.JsonBlockImporter
import org.hyperledger.besu.chainimport.RlpBlockImporter
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.ImportSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.blockcreation.IncrementingNonceGenerator
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract
import org.hyperledger.besu.metrics.MetricsService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.Function
import java.util.function.Supplier

/** Blocks related sub-command  */
@CommandLine.Command(
    name = BlocksSubCommand.COMMAND_NAME,
    description = ["This command provides blocks related actions."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [ImportSubCommand::class, BlocksSubCommand.ExportSubCommand::class]
)
class BlocksSubCommand
/**
 * Instantiates a new Blocks sub command.
 *
 * @param rlpBlockImporter the RLP block importer
 * @param jsonBlockImporterFactory the Json block importer factory
 * @param era1BlockImporter the era1 block importer supplier
 * @param rlpBlockExporterFactory the RLP block exporter factory
 * @param out Instance of PrintWriter where command usage will be written.
 */(
    private val rlpBlockImporter: Supplier<RlpBlockImporter>,
    private val jsonBlockImporterFactory: Function<BesuController, JsonBlockImporter>,
    private val era1BlockImporter: Supplier<Era1BlockImporter>,
    private val rlpBlockExporterFactory: Function<Blockchain, RlpBlockExporter>,
    private val out: PrintWriter
) : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: BesuCommand? = null // Picocli injects reference to parent command

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    /**
     * blocks import sub-command
     *
     *
     * Imports blocks from a file into the database
     */
    @CommandLine.Command(
        name = "import",
        description = ["This command imports blocks from a file into the database."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class ImportSubCommand : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: BlocksSubCommand? = null // Picocli injects reference to parent command

        @CommandLine.Parameters(
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["Files containing blocks to import."],
            arity = "0..*"
        )
        private val blockImportFiles: MutableList<Path> = ArrayList()

        @CommandLine.Option(
            names = ["--from"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File containing blocks to import."],
            arity = "0..*"
        )
        private val blockImportFileOption: List<Path> = ArrayList()

        @CommandLine.Option(
            names = ["--format"],
            description = ["The type of data to be imported, possible values are: \${COMPLETION-CANDIDATES} (default: \${DEFAULT-VALUE})."],
            arity = "1..1"
        )
        private val format = BlockImportFormat.RLP

        @CommandLine.Option(
            names = ["--start-time"],
            description = ["The timestamp in seconds of the first block for JSON imports. Subsequent blocks will be 1 second later. (default: current time)"],
            arity = "1..1"
        )
        private val startTime = System.currentTimeMillis() / 1000

        @CommandLine.Option(
            names = ["--skip-pow-validation-enabled"],
            description = ["Skip proof of work validation when importing."]
        )
        private val skipPow = false

        @CommandLine.Option(names = ["--run"], description = ["Start besu after importing."])
        private val runBesu = false

        @CommandLine.Option(
            names = ["--start-block"],
            paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
            description = ["The starting index of the block, or block list to import.  If not specified all blocks before the end block will be imported"],
            arity = "1..1"
        )
        private val startBlock = 0L

        @CommandLine.Option(
            names = ["--end-block"],
            paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
            description = ["The ending index of the block list to import (exclusive).  If not specified all blocks after the start block will be imported."],
            arity = "1..1"
        )
        private val endBlock = Long.MAX_VALUE

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        override fun run() {
            parentCommand!!.parentCommand!!.configureLogging(false)
            blockImportFiles.addAll(blockImportFileOption)

            checkCommand(parentCommand)
            Preconditions.checkNotNull(
                parentCommand.rlpBlockImporter
            )
            Preconditions.checkNotNull(
                parentCommand.jsonBlockImporterFactory
            )
            Preconditions.checkNotNull(
                parentCommand.era1BlockImporter
            )
            if (blockImportFiles.isEmpty()) {
                throw CommandLine.ParameterException(spec!!.commandLine(), "No files specified to import.")
            }
            if (skipPow && format == BlockImportFormat.JSON) {
                throw CommandLine.ParameterException(
                    spec!!.commandLine(), "Can't skip proof of work validation for JSON blocks"
                )
            }
            LOG.info("Import {} block data from {} files", format, blockImportFiles.size)
            val metricsService = initMetrics(
                parentCommand
            )
            KZGPointEvalPrecompiledContract.init()

            try {
                createController().use { controller ->
                    for (path in blockImportFiles) {
                        try {
                            LOG.info("Importing from {}", path)
                            when (format) {
                                BlockImportFormat.RLP -> importRlpBlocks(controller!!, path)
                                BlockImportFormat.JSON -> importJsonBlocks(controller!!, path)
                                BlockImportFormat.ERA1 -> importEra1Blocks(controller!!, path)
                            }
                        } catch (e: FileNotFoundException) {
                            if (blockImportFiles.size == 1) {
                                throw CommandLine.ExecutionException(
                                    spec!!.commandLine(), "Could not find file to import: $path"
                                )
                            } else {
                                LOG.error("Could not find file to import: {}", path)
                            }
                        } catch (e: Exception) {
                            if (blockImportFiles.size == 1) {
                                throw CommandLine.ExecutionException(
                                    spec!!.commandLine(), "Unable to import blocks from $path", e
                                )
                            } else {
                                LOG.error("Unable to import blocks from $path", e)
                            }
                        }
                    }
                    if (runBesu) {
                        parentCommand.parentCommand!!.run()
                    }
                }
            } finally {
                metricsService.ifPresent { obj: MetricsService -> obj.stop() }
            }
        }

        private fun createController(): BesuController? {
            try {
                // Set some defaults
                return parentCommand!!
                    .parentCommand!!
                    .setupControllerBuilder() // set to mainnet genesis block so validation rules won't reject it.
                    .clock(Clock.fixed(Instant.ofEpochSecond(startTime), ZoneOffset.UTC))!!
                    .miningParameters(miningParameters)!!
                    .build()
            } catch (e: Exception) {
                throw CommandLine.ExecutionException(parentCommand!!.spec!!.commandLine(), e.message, e)
            }
        }

        private val miningParameters: MiningConfiguration
            get() {
                val minTransactionGasPrice =
                    Wei.ZERO
                // Extradata and coinbase can be configured on a per-block level via the json file
                val coinbase = Address.ZERO
                val extraData = Bytes.EMPTY
                return ImmutableMiningConfiguration.builder()
                    .mutableInitValues(
                        ImmutableMiningConfiguration.MutableInitValues.builder()
                            .nonceGenerator(IncrementingNonceGenerator(0))
                            .extraData(extraData)
                            .minTransactionGasPrice(minTransactionGasPrice)
                            .coinbase(coinbase)
                            .build()
                    )
                    .build()
            }

        @Throws(IOException::class)
        private fun importJsonBlocks(controller: BesuController, path: Path) {
            val importer = parentCommand!!.jsonBlockImporterFactory.apply(controller)
            val jsonData = Files.readString(path)
            importer.importChain(jsonData)
        }

        @Throws(IOException::class)
        private fun importRlpBlocks(controller: BesuController, path: Path) {
            parentCommand!!
                .rlpBlockImporter
                .get()
                .importBlockchain(path, controller, skipPow, startBlock, endBlock)
        }

        @Throws(
            IOException::class,
            ExecutionException::class,
            InterruptedException::class,
            TimeoutException::class
        )
        private fun importEra1Blocks(controller: BesuController, path: Path) {
            parentCommand!!.era1BlockImporter.get().importBlocks(controller, path)
        }

        companion object {
            private fun checkCommand(parentCommand: BlocksSubCommand?) {
                Preconditions.checkNotNull(parentCommand)
                Preconditions.checkNotNull(parentCommand!!.parentCommand)
            }
        }
    }

    /**
     * blocks export sub-command
     *
     *
     * Export a block list from storage
     */
    @CommandLine.Command(
        name = "export",
        description = ["This command exports a specific block, or list of blocks from storage."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class ExportSubCommand : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: BlocksSubCommand? = null // Picocli injects reference to parent command

        @CommandLine.Option(
            names = ["--start-block"],
            paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
            description = ["The starting index of the block, or block list to export."],
            arity = "1..1"
        )
        private val startBlock: Long? = null

        @CommandLine.Option(
            names = ["--end-block"],
            paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
            description = ["The ending index of the block list to export (exclusive). If not specified a single block will be exported."],
            arity = "1..1"
        )
        private val endBlock: Long? = null

        @CommandLine.Option(
            names = ["--format"],
            hidden = true,
            description = ["The format to export, possible values are: \${COMPLETION-CANDIDATES} (default: \${DEFAULT-VALUE})."],
            arity = "1..1"
        )
        private val format = BlockExportFormat.RLP

        @CommandLine.Option(
            names = ["--to"],
            required = true,
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File to write the block list to."],
            arity = "1..1"
        )
        private val blocksExportFile: @NotBlank File? = null

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        override fun run() {
            parentCommand!!.parentCommand!!.configureLogging(false)
            LOG.info("Export {} block data to file {}", format, blocksExportFile!!.toPath())

            checkCommand(this, startBlock!!, endBlock!!)
            val metricsService = initMetrics(
                parentCommand
            )

            val controller = createBesuController()
            try {
                if (format == BlockExportFormat.RLP) {
                    exportRlpFormat(controller!!)
                } else {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(), "Unsupported format: $format"
                    )
                }
            } catch (e: IOException) {
                throw CommandLine.ExecutionException(
                    spec!!.commandLine(), "An error occurred while exporting blocks.", e
                )
            } finally {
                metricsService.ifPresent { obj: MetricsService -> obj.stop() }
            }
        }

        private fun createBesuController(): BesuController? {
            return parentCommand!!
                .parentCommand!!
                .setupControllerBuilder()
                .miningParameters(MiningConfiguration.newDefault())!!
                .build()
        }

        @Throws(IOException::class)
        private fun exportRlpFormat(controller: BesuController) {
            val context = controller.protocolContext
            val exporter =
                parentCommand!!.rlpBlockExporterFactory.apply(context.blockchain)
            exporter.exportBlocks(blocksExportFile!!, getStartBlock(), getEndBlock())
        }

        private fun checkCommand(
            exportSubCommand: ExportSubCommand, startBlock: Long, endBlock: Long
        ) {
            Preconditions.checkNotNull(exportSubCommand.parentCommand)

            val maybeStartBlock = getStartBlock()
            val maybeEndBlock = getEndBlock()

            maybeStartBlock
                .filter { blockNum: Long -> blockNum < 0 }
                .ifPresent { blockNum: Long ->
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        ("Parameter --start-block ("
                                + blockNum
                                + ") must be greater than or equal to zero.")
                    )
                }

            maybeEndBlock
                .filter { blockNum: Long -> blockNum < 0 }
                .ifPresent { blockNum: Long ->
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        ("Parameter --end-block ("
                                + blockNum
                                + ") must be greater than or equal to zero.")
                    )
                }

            if (maybeStartBlock.isPresent && maybeEndBlock.isPresent) {
                if (endBlock <= startBlock) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        ("Parameter --end-block ("
                                + endBlock
                                + ") must be greater start block ("
                                + startBlock
                                + ").")
                    )
                }
            }

            // Error if data directory is empty
            val databasePath =
                Paths.get(
                    parentCommand!!.parentCommand!!.dataDir().toAbsolutePath().toString(),
                    BesuController.DATABASE_PATH
                )
            val databaseDirectory = File(databasePath.toString())
            if (!databaseDirectory.isDirectory || databaseDirectory.list().size == 0) {
                // Empty data directory, nothing to export
                throw CommandLine.ParameterException(
                    spec!!.commandLine(),
                    "Chain is empty.  Unable to export blocks from specified data directory: "
                            + databaseDirectory.toString()
                )
            }
        }

        private fun getStartBlock(): Optional<Long> {
            return Optional.ofNullable(startBlock)
        }

        private fun getEndBlock(): Optional<Long> {
            return Optional.ofNullable(endBlock)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BlocksSubCommand::class.java)

        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "blocks"

        private fun initMetrics(parentCommand: BlocksSubCommand): Optional<MetricsService> {
            val metricsConfiguration =
                parentCommand.parentCommand!!.metricsConfiguration()

            val metricsService =
                MetricsService.create(metricsConfiguration, parentCommand.parentCommand.metricsSystem)
            metricsService.ifPresent { obj: MetricsService -> obj.start() }
            return metricsService
        }
    }
}
