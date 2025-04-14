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
package org.hyperledger.besu.ethereum.trie.pathbased.common.trielog

import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogFactoryImpl
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuService
import org.hyperledger.besu.plugin.services.TrieLogService
import org.hyperledger.besu.plugin.services.trielogs.TrieLog
import org.hyperledger.besu.plugin.services.trielogs.TrieLog.LogTuple
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent.TrieLogObserver
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider.TrieLogRangeTuple
import org.hyperledger.besu.util.Subscribers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class TrieLogManager(
    protected val blockchain: Blockchain?,
    protected val rootWorldStateStorage: PathBasedWorldStateKeyValueStorage?,
    open val maxLayersToLoad: Long,
    pluginContext: ServiceManager?
) {
    @JvmField
    protected val trieLogObservers: Subscribers<TrieLogObserver> = Subscribers.create()

    @JvmField
    protected val trieLogFactory: TrieLogFactory

    init {
        this.trieLogFactory = setupTrieLogFactory(pluginContext!!)
    }

    @Synchronized
    open fun saveTrieLog(
        localUpdater: PathBasedWorldStateUpdateAccumulator<*>,
        forWorldStateRootHash: Hash,
        forBlockHeader: BlockHeader,
        forWorldState: PathBasedWorldState
    ) {
        // do not overwrite a trielog layer that already exists in the database.
        // if it's only in memory we need to save it
        // for example, in case of reorg we don't replace a trielog layer
        if (rootWorldStateStorage!!.getTrieLog(forBlockHeader.hash).isEmpty) {
            val stateUpdater =
                forWorldState.worldStateStorage.updater()
            var success = false
            try {
                val trieLog = prepareTrieLog(forBlockHeader, localUpdater)
                persistTrieLog(forBlockHeader, forWorldStateRootHash, trieLog, stateUpdater)

                // notify trie log added observers, synchronously
                trieLogObservers.forEach { o: TrieLogObserver -> o.onTrieLogAdded(TrieLogAddedEvent(trieLog)) }

                success = true
            } finally {
                if (success) {
                    stateUpdater.commitTrieLogOnly()
                } else {
                    stateUpdater.rollback()
                }
            }
        }
    }

    private fun prepareTrieLog(
        blockHeader: BlockHeader, localUpdater: PathBasedWorldStateUpdateAccumulator<*>
    ): TrieLog {
        LOG.atDebug()
            .setMessage("Adding layered world state for {}")
            .addArgument { blockHeader.toLogString() }
            .log()
        val trieLog = trieLogFactory.create(localUpdater, blockHeader)
        trieLog.freeze()
        return trieLog
    }

    private fun persistTrieLog(
        blockHeader: BlockHeader,
        worldStateRootHash: Hash,
        trieLog: TrieLog,
        stateUpdater: PathBasedWorldStateKeyValueStorage.Updater
    ) {
        LOG.atDebug()
            .setMessage("Persisting trie log for block hash {} and world state root {}")
            .addArgument { blockHeader.toLogString() }
            .addArgument { worldStateRootHash.toHexString() }
            .log()

        stateUpdater
            .trieLogStorageTransaction
            .put(blockHeader.hash.toArrayUnsafe(), trieLogFactory.serialize(trieLog))
    }

    open fun getTrieLogLayer(blockHash: Hash): Optional<TrieLog> {
        return rootWorldStateStorage!!.getTrieLog(blockHash)!!.map { bytes: ByteArray? ->
            trieLogFactory.deserialize(
                bytes
            )
        }
    }

    @Synchronized
    fun subscribe(sub: TrieLogObserver): Long {
        return trieLogObservers.subscribe(sub)
    }

    @Synchronized
    fun unsubscribe(id: Long) {
        trieLogObservers.unsubscribe(id)
    }

    private fun setupTrieLogFactory(pluginContext: ServiceManager): TrieLogFactory {
        // if we have a TrieLogService from pluginContext, use it.
        val trieLogServicez: Optional<out BesuService?> =
            Optional.ofNullable(pluginContext)
                .flatMap { context: ServiceManager ->
                    context.getService(
                        TrieLogService::class.java
                    )
                }

        if (trieLogServicez.isPresent) {
            val trieLogService: TrieLogService = trieLogServicez.get()!! as TrieLogService
            // push the TrieLogProvider into the TrieLogService
            trieLogService.configureTrieLogProvider(trieLogProvider)

            // configure plugin observers:
            trieLogService.observers!!.forEach { subscriber -> trieLogObservers.subscribe(subscriber) }

            // return the TrieLogFactory implementation from the TrieLogService
            if (trieLogService.trieLogFactory!!.isPresent) {
                return trieLogService.trieLogFactory!!.get()
            }
        }
        // Otherwise default to TrieLogFactoryImpl
        return TrieLogFactoryImpl()
    }

    private val trieLogProvider: TrieLogProvider
        get() = object : TrieLogProvider {
            override fun getRawTrieLogLayer(blockHash: Hash?): Optional<Bytes>? {
                return rootWorldStateStorage!!.getTrieLog(blockHash).map { value: ByteArray? ->
                    Bytes.wrap(
                        value
                    )
                }
            }

            override fun getRawTrieLogLayer(blockNumber: Long): Optional<Bytes>? {
                return blockchain!!
                    .getBlockHeader(blockNumber)
                    .map { obj: BlockHeader -> obj.hash }
                    .flatMap { blockHash: Hash ->
                        this.getRawTrieLogLayer(
                            blockHash
                        )
                    }
            }

            override fun saveRawTrieLogLayer(
                blockHash: Hash?, blockNumber: Long, trieLog: Bytes?
            ) {
                val updater = rootWorldStateStorage!!.updater()
                updater
                    .trieLogStorageTransaction
                    .put(blockHash!!.toArrayUnsafe(), trieLog!!.toArrayUnsafe())
                updater.commitTrieLogOnly()
                // TODO maybe find a way to have a clean and complete trielog for observers
                trieLogObservers.forEach { o: TrieLogObserver ->
                    o.onTrieLogAdded(
                        TrieLogAddedEvent(
                            TrieLogLayer().setBlockHash(blockHash).setBlockNumber(blockNumber)
                        )
                    )
                }
            }

            override fun <T : LogTuple<*>?> getTrieLogLayer(blockHash: Hash?): Optional<TrieLog>? {
                return this@TrieLogManager.getTrieLogLayer(blockHash!!)
            }

            override fun <T : LogTuple<*>?> getTrieLogLayer(blockNumber: Long): Optional<TrieLog> {
                return blockchain!!
                    .getBlockHeader(blockNumber)
                    .map { obj: BlockHeader -> obj.hash }
                    .flatMap { blockHash: Hash ->
                        this@TrieLogManager.getTrieLogLayer(
                            blockHash
                        )
                    }
            }

            override fun <T : LogTuple<*>?> getTrieLogsByRange(
                fromBlockNumber: Long, toBlockNumber: Long
            ): List<TrieLogRangeTuple> {
                return rangeAsStream(fromBlockNumber, toBlockNumber)
                    .map { blockNumber: Long? ->
                        blockchain!!.getBlockHeader(
                            blockNumber!!
                        )
                    }
                    .map { headerOpt: Optional<BlockHeader> ->
                        headerOpt.flatMap { header: BlockHeader ->
                            this@TrieLogManager
                                .getTrieLogLayer(header.blockHash)
                                .map { layer: TrieLog? ->
                                    TrieLogRangeTuple(
                                        header.blockHash, header.number, layer!!
                                    )
                                }
                        }
                    }
                    .filter { obj: Optional<TrieLogRangeTuple> -> obj.isPresent }
                    .map { obj: Optional<TrieLogRangeTuple> -> obj.get() }
                    .toList()
            }

            fun rangeAsStream(fromBlockNumber: Long, toBlockNumber: Long): Stream<Long> {
                require(abs((toBlockNumber - fromBlockNumber).toDouble()) <= LOG_RANGE_LIMIT) { "Requested Range too large" }
                val left = min(fromBlockNumber.toDouble(), toBlockNumber.toDouble()).toLong()
                val right = max(fromBlockNumber.toDouble(), toBlockNumber.toDouble()).toLong()
                return LongStream.range(left, right).boxed()
            }
        }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TrieLogManager::class.java)
        const val LOG_RANGE_LIMIT: Long = 1000 // restrict trielog range queries to 1k logs
    }
}
