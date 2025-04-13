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
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.subcommands.storage.RevertMetadataSubCommand
import org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand
import org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand.RevertVariablesStorage
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.chain.VariablesStorage
import org.hyperledger.besu.ethereum.rlp.RLP
import org.hyperledger.besu.ethereum.rlp.RLPOutput
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.PrintWriter

/** The Storage sub command.  */
@CommandLine.Command(
    name = StorageSubCommand.COMMAND_NAME,
    description = ["This command provides storage related actions."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [RevertVariablesStorage::class, RocksDbSubCommand::class, TrieLogSubCommand::class, RevertMetadataSubCommand::class, PrunePreMergeBlockDataSubCommand::class
    ]
)
class StorageSubCommand
/**
 * Instantiates a new Storage sub command.
 *
 * @param out The PrintWriter where the usage will be reported.
 */(private val out: PrintWriter) : Runnable {
    @JvmField
    @Suppress("unused")
    @CommandLine.ParentCommand
    var besuCommand: BesuCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    /** The Hash sub command for password.  */
    @CommandLine.Command(
        name = "revert-variables",
        description = ["This command revert the modifications done by the variables storage feature."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class RevertVariablesStorage : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: StorageSubCommand? = null

        override fun run() {
            Preconditions.checkNotNull(parentCommand)

            val storageProvider = createBesuController().storageProvider

            revert(storageProvider)
        }

        private fun createBesuController(): BesuController {
            return parentCommand!!.besuCommand!!.buildController()
        }

        private fun revert(storageProvider: StorageProvider) {
            val variablesStorage = storageProvider.createVariablesStorage()
            val blockchainStorage =
                storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN)
            val blockchainUpdater = blockchainStorage.startTransaction()
            val variablesUpdater = variablesStorage.updater()

            variablesStorage
                .chainHead
                .ifPresent { v: Hash ->
                    setBlockchainVariable(
                        blockchainUpdater,
                        VARIABLES_PREFIX,
                        VariablesStorage.Keys.CHAIN_HEAD_HASH.bytes,
                        v
                    )
                    LOG.info(
                        "Reverted variable storage for key {}",
                        VariablesStorage.Keys.CHAIN_HEAD_HASH
                    )
                }

            variablesStorage
                .finalized
                .ifPresent { v: Hash ->
                    setBlockchainVariable(
                        blockchainUpdater,
                        VARIABLES_PREFIX,
                        VariablesStorage.Keys.FINALIZED_BLOCK_HASH.bytes,
                        v
                    )
                    LOG.info(
                        "Reverted variable storage for key {}",
                        VariablesStorage.Keys.FINALIZED_BLOCK_HASH
                    )
                }

            variablesStorage
                .safeBlock
                .ifPresent { v: Hash ->
                    setBlockchainVariable(
                        blockchainUpdater,
                        VARIABLES_PREFIX,
                        VariablesStorage.Keys.SAFE_BLOCK_HASH.bytes,
                        v
                    )
                    LOG.info(
                        "Reverted variable storage for key {}",
                        VariablesStorage.Keys.SAFE_BLOCK_HASH
                    )
                }

            val forkHeads = variablesStorage.forkHeads
            if (!forkHeads.isEmpty()) {
                setBlockchainVariable(
                    blockchainUpdater,
                    VARIABLES_PREFIX,
                    VariablesStorage.Keys.FORK_HEADS.bytes,
                    RLP.encode { o: RLPOutput ->
                        o.writeList(
                            forkHeads
                        ) { `val`: Hash?, out: RLPOutput -> out.writeBytes(`val`) }
                    })
                LOG.info("Reverted variable storage for key {}", VariablesStorage.Keys.FORK_HEADS)
            }

            variablesStorage
                .localEnrSeqno
                .ifPresent { v: Bytes ->
                    setBlockchainVariable(
                        blockchainUpdater,
                        Bytes.EMPTY,
                        VariablesStorage.Keys.SEQ_NO_STORE.bytes,
                        v
                    )
                    LOG.info(
                        "Reverted variable storage for key {}",
                        VariablesStorage.Keys.SEQ_NO_STORE
                    )
                }

            variablesUpdater.removeAll()

            variablesUpdater.commit()
            blockchainUpdater.commit()
        }

        private fun setBlockchainVariable(
            blockchainTransaction: KeyValueStorageTransaction,
            prefix: Bytes,
            key: Bytes,
            value: Bytes
        ) {
            blockchainTransaction.put(
                Bytes.concatenate(prefix, key).toArrayUnsafe(), value.toArrayUnsafe()
            )
        }

        companion object {
            private val LOG: Logger = LoggerFactory.getLogger(RevertVariablesStorage::class.java)
            private val VARIABLES_PREFIX: Bytes = Bytes.of(1)
        }
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "storage"
    }
}
