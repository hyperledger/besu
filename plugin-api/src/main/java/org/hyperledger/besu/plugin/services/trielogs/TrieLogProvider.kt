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
package org.hyperledger.besu.plugin.services.trielogs

import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.plugin.services.trielogs.TrieLog.LogTuple
import java.util.*

/** Trielog provider interface for a given block hash.  */
interface TrieLogProvider {
    /**
     * Saves the TrieLog layer for the given block hash.
     *
     * @param blockHash the block hash
     * @param blockNumber the block number
     * @param trieLog the associated TrieLog layer
     */
    fun saveRawTrieLogLayer(blockHash: Hash?, blockNumber: Long, trieLog: Bytes?)

    /**
     * Returns the TrieLog layer for the given block hash.
     *
     * @param blockHash the block hash
     * @return the TrieLog layer for the given block hash
     * @param <T> the type of the TrieLog
    </T> */
    fun <T : LogTuple<*>?> getTrieLogLayer(blockHash: Hash?): Optional<TrieLog>?

    /**
     * Get the raw TrieLog layer for the given block hash.
     *
     * @param blockHash the block hash
     * @return the raw TrieLog layer bytes for the given block hash
     */
    fun getRawTrieLogLayer(blockHash: Hash?): Optional<Bytes>?

    /**
     * Returns the TrieLog layer for the given block number.
     *
     * @param blockNumber the block hash
     * @return the TrieLog layer for the given block hash
     * @param <T> the type of the TrieLog
    </T> */
    fun <T : LogTuple<*>?> getTrieLogLayer(blockNumber: Long): Optional<TrieLog>?

    /**
     * Get the raw TrieLog layer for the given block number.
     *
     * @param blockNumber the block number
     * @return the raw TrieLog layer bytes for the given block number
     */
    fun getRawTrieLogLayer(blockNumber: Long): Optional<Bytes>?

    /**
     * Returns the TrieLog layers for the given block number range.
     *
     * @param fromBlockNumber the from block number
     * @param toBlockNumber the to block number
     * @return the TrieLog layers for the given block number range
     * @param <T> the type of the TrieLog
    </T> */
    fun <T : LogTuple<*>?> getTrieLogsByRange(
        fromBlockNumber: Long, toBlockNumber: Long
    ): List<TrieLogRangeTuple?>?

    /**
     * Block and TrieLog layer composition, used for returning a range of TrieLog layers.
     *
     * @param blockHash the block hash
     * @param blockNumber the block number
     * @param trieLog the associated TrieLog layer
     */
    @JvmRecord
    data class TrieLogRangeTuple(val blockHash: Hash, val blockNumber: Long, val trieLog: TrieLog)
}
