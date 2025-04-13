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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.hyperledger.besu.cli.options.storage.PathBasedExtraStorageOptions
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput
import org.hyperledger.besu.ethereum.rlp.RLP
import org.hyperledger.besu.ethereum.rlp.RLPOutput
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogFactoryImpl
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

/** Helper class for counting and pruning trie logs  */
open class TrieLogHelper
/** Default Constructor.  */
{
    fun prune(
        config: DataStorageConfiguration,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        blockchain: MutableBlockchain,
        dataDirectoryPath: Path
    ): Boolean {
        val batchFileNameBase =
            dataDirectoryPath.resolve(BesuController.DATABASE_PATH).resolve(TRIE_LOG_FILE).toString()

        validatePruneConfiguration(config)

        val layersToRetain = config.pathBasedExtraStorageConfiguration.maxLayersToLoad

        val chainHeight = blockchain.chainHeadBlockNumber

        val lastBlockNumberToRetainTrieLogsFor = chainHeight - layersToRetain + 1

        if (!validatePruneRequirements(
                blockchain,
                chainHeight,
                lastBlockNumberToRetainTrieLogsFor,
                rootWorldStateStorage,
                layersToRetain
            )
        ) {
            return false
        }

        val numberOfBatches = calculateNumberOfBatches(layersToRetain)
        LOG.info("Retain {} trie logs, processing in {} batches", layersToRetain, numberOfBatches)

        processTrieLogBatches(
            rootWorldStateStorage,
            blockchain,
            chainHeight,
            lastBlockNumberToRetainTrieLogsFor,
            numberOfBatches,
            batchFileNameBase
        )

        // Should only be layersToRetain left but loading extra just in case of an unforeseen bug
        val countAfterPrune =
            rootWorldStateStorage
                .streamTrieLogKeys(layersToRetain + PathBasedExtraStorageConfiguration.DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE)
                .count()
        if (countAfterPrune == layersToRetain) {
            if (deleteFiles(batchFileNameBase, numberOfBatches)) {
                return true
            } else {
                throw IllegalStateException(
                    "There was an error deleting the trie log backup files. Please ensure besu is working before deleting them manually."
                )
            }
        } else {
            throw IllegalStateException(
                String.format(
                    "Remaining trie logs (%d) did not match %s (%d). Trie logs backup files (in %s) have not been deleted, it is safe to rerun the subcommand.",
                    countAfterPrune, PathBasedExtraStorageOptions.MAX_LAYERS_TO_LOAD, layersToRetain, batchFileNameBase
                )
            )
        }
    }

    private fun processTrieLogBatches(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        blockchain: MutableBlockchain,
        chainHeight: Long,
        lastBlockNumberToRetainTrieLogsFor: Long,
        numberOfBatches: Long,
        batchFileNameBase: String
    ) {
        for (batchNumber in 1..numberOfBatches) {
            val batchFileName = "$batchFileNameBase-$batchNumber"
            val firstBlockOfBatch = chainHeight - ((batchNumber - 1) * BATCH_SIZE)
            val lastBlockOfBatch = max(
                (chainHeight - (batchNumber * BATCH_SIZE)).toDouble(),
                lastBlockNumberToRetainTrieLogsFor.toDouble()
            ).toLong()
            val trieLogKeys =
                getTrieLogKeysForBlocks(blockchain, firstBlockOfBatch, lastBlockOfBatch)

            LOG.info("Saving trie logs to retain in file {} (batch {})...", batchFileName, batchNumber)
            saveTrieLogBatches(batchFileName, rootWorldStateStorage, trieLogKeys)
        }

        LOG.info("Clear trie logs...")
        rootWorldStateStorage.clearTrieLog()

        for (batchNumber in 1..numberOfBatches) {
            restoreTrieLogBatches(rootWorldStateStorage, batchNumber, batchFileNameBase)
        }
    }

    private fun saveTrieLogBatches(
        batchFileName: String,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        trieLogKeys: List<Hash>
    ) {
        try {
            saveTrieLogsInFile(trieLogKeys, rootWorldStateStorage, batchFileName)
        } catch (e: IOException) {
            LOG.error("Error saving trie logs to file: {}", e.message)
            throw RuntimeException(e)
        }
    }

    private fun restoreTrieLogBatches(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        batchNumber: Long,
        batchFileNameBase: String
    ) {
        try {
            LOG.info("Restoring trie logs retained from batch {}...", batchNumber)
            recreateTrieLogs(rootWorldStateStorage, batchNumber, batchFileNameBase)
        } catch (e: IOException) {
            LOG.error("Error recreating trie logs from batch {}: {}", batchNumber, e.message)
            throw RuntimeException(e)
        }
    }

    private fun deleteFiles(batchFileNameBase: String, numberOfBatches: Long): Boolean {
        LOG.info("Deleting files...")

        try {
            for (batchNumber in 1..numberOfBatches) {
                val file = File("$batchFileNameBase-$batchNumber")
                if (file.exists()) {
                    file.delete()
                }
            }
            return true
        } catch (e: Exception) {
            LOG.error("Error deleting files", e)
            return false
        }
    }

    private fun getTrieLogKeysForBlocks(
        blockchain: MutableBlockchain,
        firstBlockOfBatch: Long,
        lastBlockOfBatch: Long
    ): List<Hash> {
        val trieLogKeys: MutableList<Hash> = ArrayList()
        for (i in firstBlockOfBatch downTo lastBlockOfBatch) {
            val header = blockchain.getBlockHeader(i)
            header.ifPresentOrElse(
                { blockHeader: BlockHeader -> trieLogKeys.add(blockHeader.hash) },
                { LOG.error("Error retrieving block") })
        }
        return trieLogKeys
    }

    private fun calculateNumberOfBatches(layersToRetain: Long): Long {
        return layersToRetain / BATCH_SIZE + (if (layersToRetain % BATCH_SIZE == 0L) 0 else 1)
    }

    private fun validatePruneRequirements(
        blockchain: MutableBlockchain,
        chainHeight: Long,
        lastBlockNumberToRetainTrieLogsFor: Long,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        layersToRetain: Long
    ): Boolean {
        require(lastBlockNumberToRetainTrieLogsFor >= 0) {
            ("Trying to retain more trie logs than chain length ("
                    + chainHeight
                    + "), skipping pruning")
        }

        // Need to ensure we're loading at least layersToRetain if they exist
        // plus extra threshold to account forks and orphans
        val clampedCountBeforePruning =
            rootWorldStateStorage
                .streamTrieLogKeys(layersToRetain + PathBasedExtraStorageConfiguration.DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE)
                .count()
        require(clampedCountBeforePruning >= layersToRetain) {
            String.format(
                "Trie log count (%d) is less than retention limit (%d), skipping pruning",
                clampedCountBeforePruning, layersToRetain
            )
        }

        val finalizedBlockHash = blockchain.finalized

        if (finalizedBlockHash.isEmpty) {
            throw RuntimeException("No finalized block present, can't safely run trie log prune")
        } else {
            val finalizedHash = finalizedBlockHash.get()
            val finalizedBlockHeader = blockchain.getBlockHeader(finalizedHash)
            require(
                !(finalizedBlockHeader.isPresent
                        && finalizedBlockHeader.get().number < lastBlockNumberToRetainTrieLogsFor)
            ) { "Trying to prune more layers than the finalized block height, skipping pruning" }
        }
        return true
    }

    @Throws(IOException::class)
    private fun recreateTrieLogs(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        batchNumber: Long,
        batchFileNameBase: String
    ) {
        // process in chunk to avoid OOM
        val batchFileName = "$batchFileNameBase-$batchNumber"
        val trieLogsToRetain = readTrieLogsFromFile(batchFileName)
        val chunkSize = ROCKSDB_MAX_INSERTS_PER_TRANSACTION
        val keys: List<ByteArray> = ArrayList(trieLogsToRetain.keys)

        var startIndex = 0
        while (startIndex < keys.size) {
            processTransactionChunk(startIndex, chunkSize, keys, trieLogsToRetain, rootWorldStateStorage)
            startIndex += chunkSize
        }
    }

    private fun processTransactionChunk(
        startIndex: Int,
        chunkSize: Int,
        keys: List<ByteArray>,
        trieLogsToRetain: IdentityHashMap<ByteArray, ByteArray>,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage
    ) {
        val updater = rootWorldStateStorage.updater()
        val endIndex = min((startIndex + chunkSize).toDouble(), keys.size.toDouble()).toInt()

        for (i in startIndex..<endIndex) {
            val key = keys[i]
            val value = trieLogsToRetain[key]
            updater.trieLogStorageTransaction.put(key, value)
            LOG.info("Key({}): {}", i, Bytes32.wrap(key).toShortHexString())
        }

        updater.trieLogStorageTransaction.commit()
    }

    @VisibleForTesting
    open fun validatePruneConfiguration(config: DataStorageConfiguration) {
        val subStorageConfiguration =
            config.pathBasedExtraStorageConfiguration
        Preconditions.checkArgument(
            subStorageConfiguration.maxLayersToLoad
                    >= PathBasedExtraStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT,
            String.format(
                PathBasedExtraStorageOptions.MAX_LAYERS_TO_LOAD + " minimum value is %d",
                PathBasedExtraStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT
            )
        )
        Preconditions.checkArgument(
            subStorageConfiguration.trieLogPruningWindowSize > 0,
            String.format(
                PathBasedExtraStorageOptions.TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than 0",
                subStorageConfiguration.trieLogPruningWindowSize
            )
        )
        Preconditions.checkArgument(
            subStorageConfiguration.trieLogPruningWindowSize
                    > subStorageConfiguration.maxLayersToLoad,
            String.format(
                PathBasedExtraStorageOptions.TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than " + PathBasedExtraStorageOptions.MAX_LAYERS_TO_LOAD + "=%d",
                subStorageConfiguration.trieLogPruningWindowSize,
                subStorageConfiguration.maxLayersToLoad
            )
        )
    }

    @Throws(IOException::class)
    private fun saveTrieLogsInFile(
        trieLogsKeys: List<Hash>,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        batchFileName: String
    ) {
        val file = File(batchFileName)
        if (file.exists()) {
            LOG.warn("File already exists {}, skipping file creation", batchFileName)
            return
        }

        try {
            FileOutputStream(file).use { fos ->
                val oos = ObjectOutputStream(fos)
                oos.writeObject(getTrieLogs(trieLogsKeys, rootWorldStateStorage))
            }
        } catch (e: IOException) {
            LOG.error(e.message)
            throw RuntimeException(e)
        }
    }

    fun readTrieLogsFromFile(batchFileName: String): IdentityHashMap<ByteArray, ByteArray> {
        val trieLogs: IdentityHashMap<ByteArray, ByteArray>
        try {
            FileInputStream(batchFileName).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    trieLogs = ois.readObject() as IdentityHashMap<ByteArray, ByteArray>
                }
            }
        } catch (e: IOException) {
            LOG.error(e.message)
            throw RuntimeException(e)
        } catch (e: ClassNotFoundException) {
            LOG.error(e.message)
            throw RuntimeException(e)
        }

        return trieLogs
    }

    private fun saveTrieLogsAsRlpInFile(
        trieLogsKeys: List<Hash>,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        batchFileName: String
    ) {
        val file = File(batchFileName)
        if (file.exists()) {
            LOG.warn("File already exists {}, skipping file creation", batchFileName)
            return
        }

        val trieLogs =
            getTrieLogs(trieLogsKeys, rootWorldStateStorage)
        val rlp =
            RLP.encode { o: RLPOutput ->
                o.writeList<Map.Entry<ByteArray, ByteArray>>(
                    trieLogs.entries
                ) { `val`: Map.Entry<ByteArray, ByteArray>, out: RLPOutput ->
                    out.writeRaw(
                        Bytes.wrap(`val`.value)
                    )
                }
            }
        try {
            Files.write(file.toPath(), rlp.toArrayUnsafe())
        } catch (e: IOException) {
            LOG.error(e.message)
            throw RuntimeException(e)
        }
    }

    fun readTrieLogsAsRlpFromFile(batchFileName: String): IdentityHashMap<ByteArray, ByteArray> {
        try {
            val file = Bytes.wrap(Files.readAllBytes(Path.of(batchFileName)))
            val input = BytesValueRLPInput(file, false)

            input.enterList()
            val trieLogs = IdentityHashMap<ByteArray, ByteArray>()
            while (!input.isEndOfCurrentList) {
                val trieLogBytes = input.currentListAsBytes()
                val trieLogLayer =
                    TrieLogFactoryImpl.readFrom(BytesValueRLPInput(Bytes.wrap(trieLogBytes), false))
                trieLogs[trieLogLayer.blockHash.toArrayUnsafe()] = trieLogBytes.toArrayUnsafe()
            }
            input.leaveList()

            return trieLogs
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun getTrieLogs(
        trieLogKeys: List<Hash>,
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage
    ): IdentityHashMap<ByteArray, ByteArray> {
        val trieLogsToRetain = IdentityHashMap<ByteArray, ByteArray>()

        LOG.info("Obtaining trielogs from db, this may take a few minutes...")
        trieLogKeys.forEach(
            Consumer { hash: Hash ->
                rootWorldStateStorage
                    .getTrieLog(hash)
                    .ifPresent { trieLog: ByteArray -> trieLogsToRetain[hash.toArrayUnsafe()] = trieLog }
            })
        return trieLogsToRetain
    }

    fun getCount(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        limit: Int,
        blockchain: Blockchain
    ): TrieLogCount {
        val total = AtomicInteger()
        val canonicalCount = AtomicInteger()
        val forkCount = AtomicInteger()
        val orphanCount = AtomicInteger()
        rootWorldStateStorage
            .streamTrieLogKeys(limit.toLong())
            .map<Bytes32> { bytes: ByteArray? -> Bytes32.wrap(bytes) }
            .map<Hash> { bytes: Bytes32? -> Hash.wrap(bytes) }
            .forEach { hash: Hash ->
                total.getAndIncrement()
                blockchain
                    .getBlockHeader(hash)
                    .ifPresentOrElse(
                        { header: BlockHeader ->
                            val number = header.number
                            val headerByNumber =
                                blockchain.getBlockHeader(number)
                            if (headerByNumber.isPresent
                                && headerByNumber.get().hash == hash
                            ) {
                                canonicalCount.getAndIncrement()
                            } else {
                                forkCount.getAndIncrement()
                            }
                        },
                        { orphanCount.getAndIncrement() })
            }

        return TrieLogCount(total.get(), canonicalCount.get(), forkCount.get(), orphanCount.get())
    }

    fun printCount(out: PrintWriter, count: TrieLogCount) {
        out.printf(
            "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
            count.total, count.canonicalCount, count.forkCount, count.orphanCount
        )
    }

    fun importTrieLog(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage, trieLogFilePath: Path
    ) {
        val trieLog = readTrieLogsAsRlpFromFile(trieLogFilePath.toString())

        val updater = rootWorldStateStorage.updater()
        trieLog.forEach { (key: ByteArray?, value: ByteArray?) -> updater.trieLogStorageTransaction.put(key, value) }
        updater.trieLogStorageTransaction.commit()
    }

    @Throws(IOException::class)
    fun exportTrieLog(
        rootWorldStateStorage: PathBasedWorldStateKeyValueStorage,
        trieLogHash: List<Hash>,
        directoryPath: Path
    ) {
        val trieLogFile = directoryPath.toString()

        saveTrieLogsAsRlpInFile(trieLogHash, rootWorldStateStorage, trieLogFile)
    }

    @JvmRecord
    data class TrieLogCount(val total: Int, val canonicalCount: Int, val forkCount: Int, val orphanCount: Int)
    companion object {
        private const val TRIE_LOG_FILE = "trieLogsToRetain"
        private const val BATCH_SIZE: Long = 20000
        private const val ROCKSDB_MAX_INSERTS_PER_TRANSACTION = 1000
        private val LOG: Logger = LoggerFactory.getLogger(TrieLogHelper::class.java)
    }
}
