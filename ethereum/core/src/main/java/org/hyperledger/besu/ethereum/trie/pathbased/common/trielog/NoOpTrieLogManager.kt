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

import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator
import org.hyperledger.besu.plugin.services.trielogs.TrieLog
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent.TrieLogObserver
import java.util.*

class NoOpTrieLogManager : TrieLogManager(null, null, 0, null) {
    @Synchronized
    override fun saveTrieLog(
        localUpdater: PathBasedWorldStateUpdateAccumulator<*>,
        forWorldStateRootHash: Hash,
        forBlockHeader: BlockHeader,
        forWorldState: PathBasedWorldState
    ) {
        // notify trie log added observers, synchronously
        val trieLog = trieLogFactory.create(localUpdater, forBlockHeader)
        trieLogObservers.forEach { o: TrieLogObserver -> o.onTrieLogAdded(TrieLogAddedEvent(trieLog)) }
    }

    override val maxLayersToLoad: Long
        get() = 0

    override fun getTrieLogLayer(blockHash: Hash): Optional<TrieLog> {
        return Optional.empty()
    }
}
