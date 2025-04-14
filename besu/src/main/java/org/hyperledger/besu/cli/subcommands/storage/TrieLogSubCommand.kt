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
package org.hyperledger.besu.cli.subcommands.storage

import com.google.common.base.Preconditions
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.forEachColumnFamily
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.formatOutputSize
import org.hyperledger.besu.cli.subcommands.storage.TrieLogSubCommand.*
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogPruner
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong

/** The Trie Log subcommand.  */
@CommandLine.Command(
    name = "trie-log",
    aliases = ["x-trie-log"],
    description = ["Manipulate trie logs"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [CountTrieLog::class, PruneTrieLog::class, ExportTrieLog::class, ImportTrieLog::class
    ]
)
class TrieLogSubCommand
/** Default Constructor.  */
internal constructor() : Runnable {
    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

    override fun run() {
        val out = spec!!.commandLine().out
        spec.commandLine().usage(out)
    }

    @CommandLine.Command(
        name = "count",
        description = ["This command counts all the trie logs"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class CountTrieLog : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: TrieLogSubCommand? = null

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

        override fun run() {
            val context = trieLogContext

            val out = spec!!.commandLine().out

            out.println("Counting trie logs...")
            val trieLogHelper = TrieLogHelper()
            trieLogHelper.printCount(
                out,
                trieLogHelper.getCount(
                    context.rootWorldStateStorage, Int.MAX_VALUE, context.blockchain
                )
            )
        }
    }

    @CommandLine.Command(
        name = "prune",
        description = ["This command prunes all trie log layers below the retention limit, including orphaned trie logs."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class PruneTrieLog : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: TrieLogSubCommand? = null

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

        override fun run() {
            val context = trieLogContext
            val dataDirectoryPath =
                Paths.get(
                    Companion.parentCommand!!.besuCommand!!.dataDir().toAbsolutePath().toString()
                )

            LOG.info("Estimating trie logs size before pruning...")
            val sizeBefore = estimatedSizeOfTrieLogs()
            LOG.info("Estimated trie logs size before pruning: {}", formatOutputSize(sizeBefore))
            LOG.info("Starting pruning...")
            val trieLogHelper = TrieLogHelper()
            val success =
                trieLogHelper.prune(
                    context.config,
                    context.rootWorldStateStorage,
                    context.blockchain,
                    dataDirectoryPath
                )

            if (success) {
                LOG.info("Finished pruning. Re-estimating trie logs size...")
                val sizeAfter = estimatedSizeOfTrieLogs()
                LOG.info(
                    "Estimated trie logs size after pruning: {} (0 B estimate is normal when using default settings)",
                    formatOutputSize(sizeAfter)
                )
                val estimatedSaving = sizeBefore - sizeAfter
                LOG.info(
                    "Prune ran successfully. We estimate you freed up {}! \uD83D\uDE80",
                    formatOutputSize(estimatedSaving)
                )
                spec!!.commandLine()
                    .out
                    .printf(
                        "Prune ran successfully. We estimate you freed up %s! \uD83D\uDE80\n",
                        formatOutputSize(estimatedSaving)
                    )
            }
        }

        private fun estimatedSizeOfTrieLogs(): Long {
            val dbPath =
                Companion.parentCommand!!
                    .besuCommand!!
                    .dataDir()!!
                    .toString() + "/" + BesuController.DATABASE_PATH

            val estimatedSaving = AtomicLong(0L)
            try {
                forEachColumnFamily(
                    dbPath
                ) { rocksdb: RocksDB?, cfHandle: ColumnFamilyHandle? ->
                    try {
                        if (cfHandle!!.name.contentEquals(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE.id)) {
                            val sstSize = rocksdb!!.getProperty(cfHandle, "rocksdb.total-sst-files-size").toLong()
                            val blobSize = rocksdb.getProperty(cfHandle, "rocksdb.total-blob-file-size").toLong()

                            estimatedSaving.set(sstSize + blobSize)
                        }
                    } catch (e: RocksDBException) {
                        throw RuntimeException(e)
                    } catch (e: NumberFormatException) {
                        throw RuntimeException(e)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Error while estimating trie log size, returning 0 for estimate", e)
                return 0L
            }

            return estimatedSaving.get()
        }
    }

    @CommandLine.Command(
        name = "export",
        description = ["This command exports the trie log of a determined block to a binary file"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class ExportTrieLog : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: TrieLogSubCommand? = null

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

        @Suppress("unused")
        @CommandLine.Option(
            names = ["--trie-log-block-hash"],
            description = ["Comma separated list of hashes from the blocks you want to export the trie logs of"],
            split = " {0,1}, {0,1}",
            arity = "1..*"
        )
        private val trieLogBlockHashList: List<String>? = null

        @CommandLine.Option(
            names = ["--trie-log-file-path"],
            description = ["The file you want to export the trie logs to"],
            arity = "1..1"
        )
        private var trieLogFilePath: Path? = null

        override fun run() {
            if (trieLogFilePath == null) {
                trieLogFilePath =
                    Paths.get(
                        Companion.parentCommand!!
                            .besuCommand!!
                            .dataDir()
                            .resolve("trie-logs.bin")
                            .toAbsolutePath()
                            .toString()
                    )
            }

            val context = trieLogContext

            val listOfBlockHashes =
                trieLogBlockHashList!!.stream().map { str: String? -> Hash.fromHexString(str) }.toList()

            val trieLogHelper = TrieLogHelper()

            try {
                trieLogHelper.exportTrieLog(
                    context.rootWorldStateStorage, listOfBlockHashes, trieLogFilePath!!
                )
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    @CommandLine.Command(
        name = "import",
        description = ["This command imports a trie log exported by another besu node"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class ImportTrieLog : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: TrieLogSubCommand? = null

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

        @CommandLine.Option(
            names = ["--trie-log-file-path"],
            description = ["The file you want to import the trie logs from"],
            arity = "1..1"
        )
        private var trieLogFilePath: Path? = null

        override fun run() {
            if (trieLogFilePath == null) {
                trieLogFilePath =
                    Paths.get(
                        Companion.parentCommand!!
                            .besuCommand!!
                            .dataDir()
                            .resolve("trie-logs.bin")
                            .toAbsolutePath()
                            .toString()
                    )
            }

            val context = trieLogContext
            val trieLogHelper = TrieLogHelper()
            trieLogHelper.importTrieLog(context.rootWorldStateStorage, trieLogFilePath!!)
        }
    }

    @JvmRecord
    internal data class TrieLogContext(
        val config: DataStorageConfiguration,
        val rootWorldStateStorage: BonsaiWorldStateKeyValueStorage,
        val blockchain: MutableBlockchain
    )

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TrieLogSubCommand::class.java)

        @CommandLine.ParentCommand
        private val parentCommand: StorageSubCommand? = null

        private fun createBesuController(): BesuController? {
            val config = parentCommand!!.besuCommand!!.dataStorageConfiguration
            // disable limit trie logs to avoid preloading during subcommand execution
            return parentCommand
                .besuCommand!!
                .setupControllerBuilder()
                .dataStorageConfiguration(
                    ImmutableDataStorageConfiguration.copyOf(config)
                        .withPathBasedExtraStorageConfiguration(
                            ImmutablePathBasedExtraStorageConfiguration.copyOf(
                                config.pathBasedExtraStorageConfiguration
                            )
                                .withLimitTrieLogsEnabled(false)
                        )
                )!!
                .build()
        }

        private val trieLogContext: TrieLogContext
            get() {
                Configurator.setLevel(
                    LoggerFactory.getLogger(
                        TrieLogPruner::class.java
                    ).name, Level.DEBUG
                )
                Preconditions.checkNotNull(parentCommand)
                val besuController = createBesuController()
                val config =
                    besuController!!.dataStorageConfiguration
                Preconditions.checkArgument(
                    DataStorageFormat.BONSAI == config.dataStorageFormat,
                    "Subcommand only works with data-storage-format=BONSAI"
                )

                val storageProvider = besuController.storageProvider
                val rootWorldStateStorage =
                    storageProvider.createWorldStateStorage(config) as BonsaiWorldStateKeyValueStorage
                val blockchain = besuController.protocolContext.blockchain
                return TrieLogContext(config, rootWorldStateStorage, blockchain)
            }
    }
}
