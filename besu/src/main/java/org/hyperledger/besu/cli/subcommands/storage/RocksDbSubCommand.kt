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

import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.ColumnFamilyUsage
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.forEachColumnFamily
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.forFilteredColumnFamily
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.getAndPrintUsageForColumnFamily
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.printStatsForColumnFamily
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.printTableHeader
import org.hyperledger.besu.cli.subcommands.storage.RocksDbHelper.printTotals
import org.hyperledger.besu.cli.subcommands.storage.RocksDbSubCommand.RocksDbStats
import org.hyperledger.besu.cli.subcommands.storage.RocksDbSubCommand.RocksDbUsage
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException
import picocli.CommandLine

/** The RocksDB subcommand.  */
@CommandLine.Command(
    name = "rocksdb",
    description = ["Print RocksDB information"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [RocksDbUsage::class, RocksDbStats::class]
)
class RocksDbSubCommand
/** Default Constructor.  */
    : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val storageSubCommand: StorageSubCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    override fun run() {
        spec!!.commandLine().usage(System.out)
    }

    @CommandLine.Command(
        name = "usage",
        description = ["Print disk usage"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class RocksDbUsage : Runnable {
        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        @Suppress("unused")
        @CommandLine.ParentCommand
        private val rocksDbSubCommand: RocksDbSubCommand? = null

        override fun run() {
            val out = spec!!.commandLine().out

            val dbPath =
                rocksDbSubCommand!!
                    .storageSubCommand!!
                    .besuCommand
                    .dataDir()
                    .resolve(BesuController.DATABASE_PATH)
                    .toString()

            printTableHeader(out)

            val columnFamilyUsages: MutableList<ColumnFamilyUsage?> = ArrayList()
            forEachColumnFamily(
                dbPath
            ) { rocksdb: RocksDB?, cfHandle: ColumnFamilyHandle? ->
                try {
                    columnFamilyUsages.add(
                        getAndPrintUsageForColumnFamily(rocksdb!!, cfHandle!!, out)
                    )
                } catch (e: RocksDBException) {
                    throw RuntimeException(e)
                }
            }
            printTotals(out, columnFamilyUsages)
        }
    }

    @CommandLine.Command(
        name = "x-stats",
        description = ["Print rocksdb stats"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class RocksDbStats : Runnable {
        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        @Suppress("unused")
        @CommandLine.ParentCommand
        private val rocksDbSubCommand: RocksDbSubCommand? = null

        @CommandLine.Option(
            names = ["--column-family-filter", "-c"],
            description = ["Comma separated list of column family names for which to display stats"],
            split = " {0,1}, {0,1}",
            arity = "1..*"
        )
        private val columnFamilyFilter: List<String?> = ArrayList()

        override fun run() {
            val out = spec!!.commandLine().out

            val dbPath =
                rocksDbSubCommand!!
                    .storageSubCommand!!
                    .besuCommand
                    .dataDir()
                    .resolve(BesuController.DATABASE_PATH)
                    .toString()

            if (!columnFamilyFilter.isEmpty()) {
                out.println("Using column family filter: $columnFamilyFilter")
                forFilteredColumnFamily(
                    dbPath,
                    { rocksdb: RocksDB?, cfHandle: ColumnFamilyHandle? ->
                        try {
                            printStatsForColumnFamily(rocksdb!!, cfHandle!!, out)
                        } catch (e: RocksDBException) {
                            throw RuntimeException(e)
                        }
                    },
                    columnFamilyFilter
                )
            } else {
                out.println("Stats for All Column Families...")
                forEachColumnFamily(
                    dbPath
                ) { rocksdb: RocksDB?, cfHandle: ColumnFamilyHandle? ->
                    try {
                        printStatsForColumnFamily(rocksdb!!, cfHandle!!, out)
                    } catch (e: RocksDBException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }
}
