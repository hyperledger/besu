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

import jakarta.validation.constraints.NotBlank
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.config.JsonUtil
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.api.query.StateBackupService
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput
import org.hyperledger.besu.ethereum.rlp.RLPInput
import org.hyperledger.besu.ethereum.trie.CompactEncoding
import org.hyperledger.besu.ethereum.trie.PersistVisitor
import org.hyperledger.besu.ethereum.trie.RestoreVisitor
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage
import org.hyperledger.besu.util.io.RollingFileReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** The Restore state subcommand.  */
@CommandLine.Command(
    name = "x-restore-state",
    description = ["Restores the chain from a previously generated backup-state."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class RestoreState
/** Default Constructor.  */
internal constructor() : Runnable {
    @CommandLine.Option(
        names = ["--backup-path"],
        required = true,
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = ["The path to store the backup files."],
        arity = "1..1"
    )
    private val backupDir: @NotBlank Path? = null

    @CommandLine.ParentCommand
    private val parentCommand: OperatorSubCommand? = null

    private var targetBlock: Long = 0
    private var accountCount: Long = 0
    private var trieNodeCount: Long = 0
    private var compressed = false
    private var besuController: BesuController? = null
    private var updater: ForestWorldStateKeyValueStorage.Updater? = null

    private fun accountFileName(fileNumber: Int, compressed: Boolean): Path {
        return StateBackupService.accountFileName(backupDir, targetBlock, fileNumber, compressed)
    }

    private fun headerFileName(fileNumber: Int, compressed: Boolean): Path {
        return StateBackupService.headerFileName(backupDir, fileNumber, compressed)
    }

    private fun bodyFileName(fileNumber: Int, compressed: Boolean): Path {
        return StateBackupService.bodyFileName(backupDir, fileNumber, compressed)
    }

    private fun receiptFileName(fileNumber: Int, compressed: Boolean): Path {
        return StateBackupService.receiptFileName(backupDir, fileNumber, compressed)
    }

    override fun run() {
        try {
            val manifest =
                JsonUtil.objectNodeFromString(
                    Files.readString(backupDir!!.resolve("besu-backup-manifest.json"))
                )

            compressed = manifest["compressed"].asBoolean(false)
            targetBlock = manifest["targetBlock"].asLong()
            accountCount = manifest["accountCount"].asLong()
            besuController = createBesuController()

            restoreBlocks()
            restoreAccounts()

            LOG.info("Restore complete")
        } catch (e: IOException) {
            LOG.error("Error restoring state", e)
        }
    }

    @Throws(IOException::class)
    private fun restoreBlocks() {
        RollingFileReader(
            { fileNumber: Int, compressed: Boolean -> this.headerFileName(fileNumber, compressed) },
            compressed
        ).use { headerReader ->
            RollingFileReader(
                { fileNumber: Int, compressed: Boolean -> this.bodyFileName(fileNumber, compressed) }, compressed
            ).use { bodyReader ->
                RollingFileReader({ fileNumber: Int, compressed: Boolean ->
                    this.receiptFileName(
                        fileNumber,
                        compressed
                    )
                }, compressed).use { receiptReader ->
                    val blockchain = besuController!!.protocolContext.blockchain
                    // target block is "including" the target block, so LE test not LT.
                    for (i in 0..targetBlock) {
                        if (i % 100000 == 0L) {
                            LOG.info("Loading chain data {} / {}", i, targetBlock)
                        }

                        val headerEntry = headerReader.readBytes()
                        val bodyEntry = bodyReader.readBytes()
                        val receiptEntry = receiptReader.readBytes()
                        val functions: BlockHeaderFunctions = MainnetBlockHeaderFunctions()

                        val header =
                            BlockHeader.readFrom(
                                BytesValueRLPInput(Bytes.wrap(headerEntry), false, true), functions
                            )
                        val body =
                            BlockBody.readWrappedBodyFrom(
                                BytesValueRLPInput(Bytes.wrap(bodyEntry), false, true), functions
                            )
                        val receiptsRlp: RLPInput = BytesValueRLPInput(Bytes.wrap(receiptEntry), false, true)
                        val receiptsCount = receiptsRlp.enterList()
                        val receipts: MutableList<TransactionReceipt> = ArrayList(receiptsCount)
                        for (j in 0..<receiptsCount) {
                            receipts.add(TransactionReceiptDecoder.readFrom(receiptsRlp, true))
                        }
                        receiptsRlp.leaveList()

                        blockchain.appendBlock(Block(header, body), receipts)
                    }
                }
            }
        }
        LOG.info("Chain data loaded")
    }

    @Throws(IOException::class)
    private fun restoreAccounts() {
        newWorldStateUpdater()
        var storageBranchCount = 0
        var storageExtensionCount = 0
        var storageLeafCount = 0

        val accountPersistVisitor =
            PersistVisitor<Bytes> { key: Bytes32, value: Bytes ->
                this.updateAccountState(
                    key,
                    value
                )
            }
        var root = accountPersistVisitor.initialRoot()

        RollingFileReader(
            { fileNumber: Int, compressed: Boolean -> this.accountFileName(fileNumber, compressed) },
            compressed
        ).use { reader ->
            for (i in 0..<accountCount) {
                if (i % 100000 == 0L) {
                    LOG.info("Loading account data {} / {}", i, accountCount)
                }
                val accountEntry = reader.readBytes()
                val accountInput =
                    BytesValueRLPInput(Bytes.of(*accountEntry), false, true)
                val length = accountInput.enterList()
                if (length != 3) {
                    throw RuntimeException("Unexpected account length $length")
                }
                val trieKey = accountInput.readBytes32()
                val accountRlp = accountInput.readBytes()
                val code = accountInput.readBytes()

                val trieAccount =
                    PmtStateTrieAccountValue.readFrom(BytesValueRLPInput(accountRlp, false, true))
                if (trieAccount.codeHash != Hash.hash(code)) {
                    throw RuntimeException("Code hash doesn't match")
                }
                if (code.size() > 0) {
                    updateCode(code)
                }

                val accountTrieWriteVisitor =
                    RestoreVisitor({ t: Bytes? -> t }, accountRlp, accountPersistVisitor)

                root = root.accept(accountTrieWriteVisitor, CompactEncoding.bytesToPath(trieKey))

                val storagePersistVisitor =
                    PersistVisitor<Bytes> { key: Bytes32, value: Bytes ->
                        this.updateAccountStorage(
                            key,
                            value
                        )
                    }
                var storageRoot = storagePersistVisitor.initialRoot()

                while (true) {
                    val trieEntry = reader.readBytes()
                    val trieInput =
                        BytesValueRLPInput(Bytes.of(*trieEntry), false, true)
                    val len = trieInput.enterList()
                    if (len == 0) {
                        break
                    }
                    if (len != 2) {
                        throw RuntimeException("Unexpected storage trie entry length $len")
                    }
                    val storageTrieKey = Bytes32.wrap(trieInput.readBytes())
                    val storageTrieValue = Bytes.wrap(trieInput.readBytes())
                    val storageTrieWriteVisitor =
                        RestoreVisitor({ t: Bytes? -> t }, storageTrieValue, storagePersistVisitor)
                    storageRoot =
                        storageRoot.accept(storageTrieWriteVisitor, CompactEncoding.bytesToPath(storageTrieKey))

                    trieInput.leaveList()
                }
                storagePersistVisitor.persist(storageRoot)
                storageBranchCount += storagePersistVisitor.branchNodeCount
                storageExtensionCount += storagePersistVisitor.extensionNodeCount
                storageLeafCount += storagePersistVisitor.leafNodeCount

                accountInput.leaveList()
            }
        }
        accountPersistVisitor.persist(root)
        updater!!.commit()
        LOG.info("Account BranchNodes: {} ", accountPersistVisitor.branchNodeCount)
        LOG.info("Account ExtensionNodes: {} ", accountPersistVisitor.extensionNodeCount)
        LOG.info("Account LeafNodes: {} ", accountPersistVisitor.leafNodeCount)
        LOG.info("Storage BranchNodes: {} ", storageBranchCount)
        LOG.info("Storage LeafNodes: {} ", storageExtensionCount)
        LOG.info("Storage ExtensionNodes: {} ", storageLeafCount)
        LOG.info("Account data loaded")
    }

    private fun newWorldStateUpdater() {
        if (updater != null) {
            updater!!.commit()
        }
        val worldStateKeyValueStorage =
            (besuController!!.protocolContext.worldStateArchive as ForestWorldStateArchive)
                .worldStateStorage
        updater = worldStateKeyValueStorage.updater()
    }

    private fun maybeCommitUpdater() {
        if (trieNodeCount % TRIE_NODE_COMMIT_BATCH_SIZE == 0L) {
            newWorldStateUpdater()
        }
    }

    private fun updateCode(code: Bytes) {
        maybeCommitUpdater()
        updater!!.putCode(code)
    }

    private fun updateAccountState(key: Bytes32, value: Bytes) {
        maybeCommitUpdater()
        // restore by path not supported
        updater!!.putAccountStateTrieNode(key, value)
        trieNodeCount++
    }

    private fun updateAccountStorage(key: Bytes32, value: Bytes) {
        maybeCommitUpdater()
        // restore by path not supported
        updater!!.putAccountStorageTrieNode(key, value)
        trieNodeCount++
    }

    private fun createBesuController(): BesuController {
        return parentCommand!!.parentCommand!!.buildController()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RestoreState::class.java)

        private const val TRIE_NODE_COMMIT_BATCH_SIZE = 100
    }
}
