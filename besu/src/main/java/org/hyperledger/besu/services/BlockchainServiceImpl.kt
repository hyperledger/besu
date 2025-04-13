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

import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket
import org.hyperledger.besu.plugin.Unstable
import org.hyperledger.besu.plugin.data.BlockBody
import org.hyperledger.besu.plugin.data.BlockContext
import org.hyperledger.besu.plugin.data.BlockHeader
import org.hyperledger.besu.plugin.data.TransactionReceipt
import org.hyperledger.besu.plugin.services.BlockchainService
import java.math.BigInteger
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors

/** The Blockchain service implementation.  */
@Unstable
class BlockchainServiceImpl
/** Instantiates a new Blockchain service implementation.  */
    : BlockchainService {
    private var protocolSchedule: ProtocolSchedule? = null
    private var blockchain: MutableBlockchain? = null

    /**
     * Initialize the Blockchain service.
     *
     * @param blockchain the blockchain
     * @param protocolSchedule the protocol schedule
     */
    fun init(blockchain: MutableBlockchain?, protocolSchedule: ProtocolSchedule?) {
        this.protocolSchedule = protocolSchedule
        this.blockchain = blockchain
    }

    /**
     * Gets block by number
     *
     * @param number the block number
     * @return the BlockContext if block exists otherwise empty
     */
    override fun getBlockByNumber(number: Long): Optional<BlockContext> {
        return blockchain!!
            .getBlockByNumber(number)
            .map { block: Block ->
                blockContext(
                    { block.header },
                    { block.body })
            }
    }

    /**
     * Gets block by hash
     *
     * @param hash the block hash
     * @return the BlockContext if block exists otherwise empty
     */
    override fun getBlockByHash(hash: Hash): Optional<BlockContext> {
        return blockchain!!
            .getBlockByHash(hash)
            .map { block: Block ->
                blockContext(
                    { block.header },
                    { block.body })
            }
    }

    override fun getChainHeadHash(): Hash {
        return blockchain!!.chainHeadHash
    }

    override fun getChainHeadHeader(): BlockHeader {
        return blockchain!!.chainHeadHeader
    }

    override fun getNextBlockBaseFee(): Optional<Wei> {
        val chainHeadHeader = blockchain!!.chainHeadHeader
        val protocolSpec =
            protocolSchedule!!.getForNextBlockHeader(chainHeadHeader, System.currentTimeMillis())
        return Optional.of(protocolSpec.feeMarket)
            .filter { obj: FeeMarket -> obj.implementsBaseFee() }
            .map { obj: FeeMarket? -> BaseFeeMarket::class.java.cast(obj) }
            .map { feeMarket: BaseFeeMarket ->
                feeMarket.computeBaseFee(
                    chainHeadHeader.number + 1,
                    chainHeadHeader.baseFee.orElse(Wei.ZERO),
                    chainHeadHeader.gasUsed,
                    feeMarket.targetGasUsed(chainHeadHeader)
                )
            }
    }

    override fun getReceiptsByBlockHash(blockHash: Hash): Optional<List<TransactionReceipt>> {
        return blockchain!!
            .getTxReceipts(blockHash)
            .map { list: List<org.hyperledger.besu.ethereum.core.TransactionReceipt?> ->
                list.stream()
                    .map { obj: org.hyperledger.besu.ethereum.core.TransactionReceipt? ->
                        TransactionReceipt::class.java.cast(obj)
                    }.collect(
                        Collectors.toList()
                    )
            }
    }

    override fun storeBlock(
        blockHeader: BlockHeader,
        blockBody: BlockBody,
        receipts: List<TransactionReceipt>
    ) {
        val coreHeader =
            blockHeader as org.hyperledger.besu.ethereum.core.BlockHeader
        val coreBody =
            blockBody as org.hyperledger.besu.ethereum.core.BlockBody
        val coreReceipts =
            receipts.stream()
                .map { obj: Any? -> org.hyperledger.besu.ethereum.core.TransactionReceipt::class.java.cast(obj) }
                .toList()
        blockchain!!.unsafeImportBlock(
            Block(coreHeader, coreBody),
            coreReceipts,
            Optional.ofNullable(blockchain!!.calculateTotalDifficulty(coreHeader))
        )
    }

    override fun getSafeBlock(): Optional<Hash> {
        return blockchain!!.safeBlock
    }

    override fun getFinalizedBlock(): Optional<Hash> {
        return blockchain!!.finalized
    }

    override fun setFinalizedBlock(blockHash: Hash) {
        val protocolSpec = getProtocolSpec(blockHash)

        if (protocolSpec.isPoS) {
            throw UnsupportedOperationException(
                "Marking block as finalized is not supported for PoS networks"
            )
        }
        blockchain!!.setFinalized(blockHash)
    }

    override fun setSafeBlock(blockHash: Hash) {
        val protocolSpec = getProtocolSpec(blockHash)

        if (protocolSpec.isPoS) {
            throw UnsupportedOperationException(
                "Marking block as safe is not supported for PoS networks"
            )
        }

        blockchain!!.setSafeBlock(blockHash)
    }

    private fun getProtocolSpec(blockHash: Hash): ProtocolSpec {
        return blockchain!!
            .getBlockByHash(blockHash)
            .map { obj: Block -> obj.header }
            .map { blockHeader: org.hyperledger.besu.ethereum.core.BlockHeader? ->
                protocolSchedule!!.getByBlockHeader(
                    blockHeader
                )
            }
            .orElseThrow {
                IllegalArgumentException(
                    "Block not found: $blockHash"
                )
            }
    }

    override fun getChainId(): Optional<BigInteger> {
        if (protocolSchedule == null) {
            return Optional.empty()
        }
        return protocolSchedule!!.chainId
    }

    companion object {
        private fun blockContext(
            blockHeaderSupplier: Supplier<BlockHeader>,
            blockBodySupplier: Supplier<BlockBody>
        ): BlockContext {
            return object : BlockContext {
                override fun getBlockHeader(): BlockHeader {
                    return blockHeaderSupplier.get()
                }

                override fun getBlockBody(): BlockBody {
                    return blockBodySupplier.get()
                }
            }
        }
    }
}
