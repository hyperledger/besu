/*
 * Copyright contributors to Besu.
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

import com.google.common.base.Suppliers
import org.hyperledger.besu.crypto.SECPSignature
import org.hyperledger.besu.crypto.SignatureAlgorithm
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory
import org.hyperledger.besu.datatypes.StateOverrideMap
import org.hyperledger.besu.datatypes.Transaction
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.MutableWorldState
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.transaction.*
import org.hyperledger.besu.ethereum.transaction.BlockSimulationParameter.BlockSimulationParameterBuilder
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.plugin.Unstable
import org.hyperledger.besu.plugin.data.BlockOverrides
import org.hyperledger.besu.plugin.data.PluginBlockSimulationResult
import org.hyperledger.besu.plugin.data.TransactionSimulationResult
import org.hyperledger.besu.plugin.services.BlockSimulationService
import java.util.function.Supplier

/** This class is a service that simulates the processing of a block  */
class BlockSimulatorServiceImpl(
    private val worldStateArchive: WorldStateArchive,
    miningConfiguration: MiningConfiguration?,
    transactionSimulator: TransactionSimulator?,
    protocolSchedule: ProtocolSchedule?,
    private val blockchain: Blockchain
) : BlockSimulationService {
    private val blockSimulator = BlockSimulator(
        worldStateArchive,
        protocolSchedule,
        transactionSimulator,
        miningConfiguration,
        blockchain,
        0
    )

    /**
     * Simulate the processing of a block given a header, a list of transactions, and blockOverrides.
     *
     * @param blockNumber the block number
     * @param transactions the transactions to include in the block
     * @param blockOverrides the blockSimulationOverride of the block
     * @param stateOverrides state overrides of the block
     * @return the block context
     */
    override fun simulate(
        blockNumber: Long,
        transactions: List<Transaction>,
        blockOverrides: BlockOverrides,
        stateOverrides: StateOverrideMap
    ): PluginBlockSimulationResult {
        return processSimulation(blockNumber, transactions, blockOverrides, stateOverrides, false)
    }

    /**
     * This method is experimental and should be used with caution. Simulate the processing of a block
     * given a header, a list of transactions, and blockOverrides and persist the WorldState
     *
     * @param blockNumber the block number
     * @param transactions the transactions to include in the block
     * @param blockOverrides block overrides for the block
     * @param stateOverrides state overrides of the block
     * @return the PluginBlockSimulationResult
     */
    @Unstable
    override fun simulateAndPersistWorldState(
        blockNumber: Long,
        transactions: List<Transaction>,
        blockOverrides: BlockOverrides,
        stateOverrides: StateOverrideMap
    ): PluginBlockSimulationResult {
        return processSimulation(blockNumber, transactions, blockOverrides, stateOverrides, true)
    }

    private fun processSimulation(
        blockNumber: Long,
        transactions: List<Transaction>,
        blockOverrides: BlockOverrides,
        stateOverrides: StateOverrideMap,
        persistWorldState: Boolean
    ): PluginBlockSimulationResult {
        val header = getBlockHeader(blockNumber)
        val callParameters =
            transactions.stream().map { tx: Transaction? -> CallParameter.fromTransaction(tx) }.toList()
        val blockStateCall =
            BlockStateCall(callParameters, blockOverrides, stateOverrides)
        try {
            getWorldState(header, persistWorldState).use { ws ->
                val blockSimulationParameter =
                    BlockSimulationParameterBuilder()
                        .blockStateCalls(java.util.List.of(blockStateCall))
                        .validation(true)
                        .fakeSignature(FAKE_SIGNATURE)
                        .build()
                val results =
                    blockSimulator.process(header, blockSimulationParameter, ws)
                val result: BlockSimulationResult = results.first()
                if (persistWorldState) {
                    ws.persist(result.block.header)
                }
                return response(result)
            }
        } catch (e: Exception) {
            throw RuntimeException("Error simulating block", e)
        }
    }

    private fun getBlockHeader(blockNumber: Long): BlockHeader {
        return blockchain
            .getBlockHeader(blockNumber)
            .orElseThrow {
                IllegalArgumentException(
                    "Block header not found for block number: $blockNumber"
                )
            }
    }

    private fun getWorldState(header: BlockHeader, isPersisting: Boolean): MutableWorldState {
        val worldStateQueryParams =
            WorldStateQueryParams.newBuilder()
                .withBlockHeader(header)
                .withShouldWorldStateUpdateHead(isPersisting)
                .build()
        return worldStateArchive
            .getWorldState(worldStateQueryParams)
            .orElseThrow {
                IllegalArgumentException(
                    "World state not available for block number (block hash): "
                            + header.toLogString()
                )
            }
    }

    private fun response(result: BlockSimulationResult): PluginBlockSimulationResult {
        return PluginBlockSimulationResult(
            result.blockHeader,
            result.blockBody,
            result.receipts,
            result.transactionSimulations.stream()
                .map { simulation: TransactionSimulatorResult ->
                    TransactionSimulationResult(
                        simulation.transaction,
                        simulation.result
                    )
                }
                .toList())
    }

    companion object {
        private val SIGNATURE_ALGORITHM: Supplier<SignatureAlgorithm> =
            Suppliers.memoize { SignatureAlgorithmFactory.getInstance() }

        // Dummy signature for transactions to not fail being processed.
        private val FAKE_SIGNATURE: SECPSignature = SIGNATURE_ALGORITHM
            .get()
            .createSignature(
                SIGNATURE_ALGORITHM.get().halfCurveOrder,
                SIGNATURE_ALGORITHM.get().halfCurveOrder,
                0.toByte()
            )
    }
}
