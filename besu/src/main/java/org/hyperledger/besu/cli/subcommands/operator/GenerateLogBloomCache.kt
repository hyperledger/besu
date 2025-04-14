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
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem
import picocli.CommandLine
import kotlin.math.min

/** The generate-log-bloom-cache CLI command.  */
@CommandLine.Command(
    name = "generate-log-bloom-cache",
    description = ["Generate cached values of block log bloom filters."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class GenerateLogBloomCache
/** Default constructor.  */
    : Runnable {
    @CommandLine.Option(
        names = ["--start-block"],
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = [("The block to start generating the cache.  Must be an increment of "
                + TransactionLogBloomCacher.BLOCKS_PER_BLOOM_CACHE
                + " (default: \${DEFAULT-VALUE})")],
        arity = "1..1"
    )
    private val startBlock = 0L

    @CommandLine.Option(
        names = ["--end-block"],
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = ["The block to stop generating the cache (default is last block of the chain)."],
        arity = "1..1"
    )
    private val endBlock = Long.MAX_VALUE

    @CommandLine.ParentCommand
    private val parentCommand: OperatorSubCommand? = null

    override fun run() {
        checkPreconditions()
        val cacheDir = parentCommand!!.parentCommand!!.dataDir().resolve(BesuController.CACHE_PATH)
        cacheDir.toFile().mkdirs()
        val blockchain = createBesuController().protocolContext.blockchain
        val scheduler = EthScheduler(1, 1, 1, 1, NoOpMetricsSystem())
        try {
            val finalBlock = min(blockchain.chainHeadBlockNumber.toDouble(), endBlock.toDouble()).toLong()
            val cacher =
                TransactionLogBloomCacher(blockchain, cacheDir, scheduler)
            cacher.generateLogBloomCache(startBlock, finalBlock)
        } finally {
            scheduler.stop()
            try {
                scheduler.awaitStop()
            } catch (e: InterruptedException) {
                // ignore
            }
        }
    }

    private fun checkPreconditions() {
        Preconditions.checkNotNull(parentCommand!!.parentCommand!!.dataDir())
        Preconditions.checkState(
            startBlock % TransactionLogBloomCacher.BLOCKS_PER_BLOOM_CACHE == 0L,
            "Start block must be an even increment of %s",
            TransactionLogBloomCacher.BLOCKS_PER_BLOOM_CACHE
        )
    }

    private fun createBesuController(): BesuController {
        return parentCommand!!.parentCommand!!.buildController()
    }
}
