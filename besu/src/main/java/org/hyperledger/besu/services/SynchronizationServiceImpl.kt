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
package org.hyperledger.besu.services

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.hyperledger.besu.consensus.merge.MergeContext
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.MutableWorldState
import org.hyperledger.besu.ethereum.core.Synchronizer
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.PathBasedWorldStateProvider
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.plugin.data.BlockBody
import org.hyperledger.besu.plugin.data.BlockHeader
import org.hyperledger.besu.plugin.services.sync.SynchronizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/** Synchronization service.  */
class SynchronizationServiceImpl(
    private val synchronizer: Synchronizer,
    private val protocolContext: ProtocolContext,
    private val protocolSchedule: ProtocolSchedule,
    private val syncState: SyncState,
    worldStateArchive: WorldStateArchive?
) : SynchronizationService {
    private val worldStateArchive: Optional<PathBasedWorldStateProvider> = Optional.ofNullable(worldStateArchive)
        .filter { z: WorldStateArchive? -> z is PathBasedWorldStateProvider }
        .map { obj: WorldStateArchive? -> PathBasedWorldStateProvider::class.java.cast(obj) }

    override fun fireNewUnverifiedForkchoiceEvent(
        head: Hash, safeBlock: Hash, finalizedBlock: Hash
    ) {
        protocolContext
            .safeConsensusContext(MergeContext::class.java)
            .ifPresent { mc: MergeContext -> mc.fireNewUnverifiedForkchoiceEvent(head, safeBlock, finalizedBlock) }
        protocolContext.blockchain.setFinalized(finalizedBlock)
        protocolContext.blockchain.setSafeBlock(safeBlock)
    }

    override fun setHead(blockHeader: BlockHeader, blockBody: BlockBody): Boolean {
        val blockImporter =
            protocolSchedule
                .getByBlockHeader(blockHeader as org.hyperledger.besu.ethereum.core.BlockHeader)
                .blockImporter
        return blockImporter
            .importBlock(
                protocolContext,
                Block(
                    blockHeader,
                    blockBody as org.hyperledger.besu.ethereum.core.BlockBody
                ),
                HeaderValidationMode.SKIP_DETACHED
            )
            .isImported
    }

    override fun setHeadUnsafe(blockHeader: BlockHeader, blockBody: BlockBody): Boolean {
        val coreHeader =
            blockHeader as org.hyperledger.besu.ethereum.core.BlockHeader

        val blockchain = protocolContext.blockchain

        if (worldStateArchive
                .flatMap<MutableWorldState> { archive: PathBasedWorldStateProvider ->
                    archive.getWorldState(
                        WorldStateQueryParams.withBlockHeaderAndUpdateNodeHead(coreHeader)
                    )
                }
                .isPresent
        ) {
            if (coreHeader.parentHash == blockchain.chainHeadHash) {
                LOG.atDebug()
                    .setMessage(
                        "Forwarding chain head to the block {} saved from a previous newPayload invocation"
                    )
                    .addArgument { coreHeader.toLogString() }
                    .log()
                return blockchain.forwardToBlock(coreHeader)
            } else {
                LOG.atDebug()
                    .setMessage("New head {} is a chain reorg, rewind chain head to it")
                    .addArgument { coreHeader.toLogString() }
                    .log()
                return blockchain.rewindToBlock(coreHeader.blockHash)
            }
        } else {
            LOG.atWarn()
                .setMessage("The world state is unavailable, setting of head cannot be performed.")
                .log()
        }
        return false
    }

    override fun isInitialSyncPhaseDone(): Boolean {
        return syncState.isInitialSyncPhaseDone
    }

    override fun disableWorldStateTrie() {
        // TODO maybe find a best way in the future to delete and disable trie
        worldStateArchive.ifPresent { archive: PathBasedWorldStateProvider ->
            archive.worldStateSharedSpec.isTrieDisabled = true
            val worldStateStorage =
                archive.worldStateKeyValueStorage
            val worldStateBlockHash = worldStateStorage.worldStateBlockHash
            val worldStateRootHash = worldStateStorage.worldStateRootHash
            if (worldStateRootHash.isPresent && worldStateBlockHash.isPresent) {
                worldStateStorage.clearTrie()
                // keep root and block hash in the trie branch
                val updater = worldStateStorage.updater()
                updater.saveWorldState(
                    worldStateBlockHash.get(),
                    Bytes32.wrap(worldStateRootHash.get()),
                    Bytes.EMPTY
                )
                updater.commit()

                // currently only bonsai needs an explicit upgrade to full flat db
                if (worldStateStorage is BonsaiWorldStateKeyValueStorage) {
                    worldStateStorage.upgradeToFullFlatDbMode()
                }
            }
        }
    }

    override fun stop() {
        synchronizer.stop()
    }

    override fun start() {
        synchronizer.start()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SynchronizationServiceImpl::class.java)
    }
}
