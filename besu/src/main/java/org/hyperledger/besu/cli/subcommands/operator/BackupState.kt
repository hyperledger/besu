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
package org.hyperledger.besu.cli.subcommands.operator

import com.google.common.base.Preconditions
import jakarta.validation.constraints.NotBlank
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.api.query.StateBackupService
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem
import org.hyperledger.besu.util.BesuVersionUtils
import picocli.CommandLine
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.math.min
import kotlin.math.pow

/** Subcommand that performs back up of state and accounts at a specified block  */
@CommandLine.Command(
    name = "x-backup-state",
    description = ["Backs up the state and accounts at a specified block."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class BackupState
/** Default constructor.  */
    : Runnable {
    @CommandLine.Option(
        names = ["--block"],
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = ["The block to perform the backup at (default: calculated chain head)"],
        arity = "1..1"
    )
    private val block = Long.MAX_VALUE

    @CommandLine.Option(
        names = ["--backup-path"],
        required = true,
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = ["The path to store the backup files."],
        arity = "1..1"
    )
    private val backupDir: @NotBlank File? = null

    @CommandLine.Option(names = ["--compression-enabled"], description = ["Enable data compression"], arity = "1")
    private val compress = true

    @CommandLine.ParentCommand
    private val parentCommand: OperatorSubCommand? = null

    override fun run() {
        Preconditions.checkArgument(
            parentCommand!!.parentCommand!!.dataDir().toFile().exists(),
            "DataDir (the blockchain being backed up) does not exist."
        )
        Preconditions.checkArgument(
            backupDir!!.exists() || backupDir.mkdirs(),
            "Backup directory does not exist and cannot be created."
        )

        val besuController = createBesuController()
        val blockchain = besuController.protocolContext.blockchain
        val forestWorldStateKeyValueStorage =
            (besuController.protocolContext.worldStateArchive as ForestWorldStateArchive)
                .worldStateStorage
        val scheduler = EthScheduler(1, 1, 1, 1, NoOpMetricsSystem())
        try {
            val targetBlock = min(blockchain.chainHeadBlockNumber.toDouble(), block.toDouble()).toLong()
            val backup =
                StateBackupService(
                    BesuVersionUtils.version(),
                    blockchain,
                    backupDir.toPath(),
                    scheduler,
                    forestWorldStateKeyValueStorage
                )
            val status = backup.requestBackup(targetBlock, compress, Optional.empty())

            val refValue = 2.0.pow(256.0) / 100.0
            while (status.isBackingUp) {
                if (status.targetBlockNum != status.storedBlockNum) {
                    System.out.printf(
                        "Chain Progress - %,d of %,d (%5.2f%%)%n",
                        status.storedBlockNum,
                        status.targetBlockNum,
                        status.storedBlockNum * 100.0 / status.targetBlockNum
                    )
                } else {
                    System.out.printf(
                        "State Progress - %6.3f%% / %,d Accounts / %,d Storage Nodes%n",
                        status.currentAccountBytes.toUnsignedBigInteger().toDouble() / refValue,
                        status.accountCount,
                        status.storageCount
                    )
                }
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10))
            }

            System.out.printf(
                "Backup complete%n Accounts: %,d%n Code Size: %,d%nState Entries: %,d%n",
                status.accountCount, status.codeSize, status.storageCount
            )
        } finally {
            scheduler.stop()
            try {
                scheduler.awaitStop()
            } catch (e: InterruptedException) {
                // ignore
            }
        }
    }

    private fun createBesuController(): BesuController {
        return parentCommand!!.parentCommand!!.buildController()
    }
}
