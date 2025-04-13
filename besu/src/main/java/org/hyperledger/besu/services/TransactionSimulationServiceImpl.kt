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
import org.hyperledger.besu.datatypes.StateOverrideMap
import org.hyperledger.besu.datatypes.Transaction
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams
import org.hyperledger.besu.ethereum.mainnet.ValidationResult
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult
import org.hyperledger.besu.ethereum.transaction.CallParameter
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult
import org.hyperledger.besu.evm.tracing.OperationTracer
import org.hyperledger.besu.plugin.Unstable
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader
import org.hyperledger.besu.plugin.data.TransactionSimulationResult
import org.hyperledger.besu.plugin.services.TransactionSimulationService
import java.util.*

/** TransactionSimulationServiceImpl  */
@Unstable
class TransactionSimulationServiceImpl
/** Create an instance to be configured  */
    : TransactionSimulationService {
    private var blockchain: Blockchain? = null
    private var transactionSimulator: TransactionSimulator? = null

    /**
     * Configure the service
     *
     * @param blockchain the blockchain
     * @param transactionSimulator transaction simulator
     */
    fun init(blockchain: Blockchain?, transactionSimulator: TransactionSimulator?) {
        this.blockchain = blockchain
        this.transactionSimulator = transactionSimulator
    }

    override fun simulatePendingBlockHeader(): ProcessableBlockHeader {
        return transactionSimulator!!.simulatePendingBlockHeader()
    }

    override fun simulate(
        transaction: Transaction,
        maybeStateOverrides: Optional<StateOverrideMap>,
        blockHash: Hash,
        operationTracer: OperationTracer,
        isAllowExceedingBalance: Boolean
    ): Optional<TransactionSimulationResult> {
        val callParameter = CallParameter.fromTransaction(transaction)

        val maybeBlockHeader =
            blockchain!!.getBlockHeader(blockHash).or { blockchain!!.getBlockHeaderSafe(blockHash) }

        if (maybeBlockHeader.isEmpty) {
            return Optional.of(
                TransactionSimulationResult(
                    transaction,
                    TransactionProcessingResult.invalid(
                        ValidationResult.invalid(TransactionInvalidReason.BLOCK_NOT_FOUND)
                    )
                )
            )
        }

        return transactionSimulator!!
            .process(
                callParameter,
                if (isAllowExceedingBalance)
                    TransactionValidationParams.transactionSimulatorAllowExceedingBalance()
                else
                    TransactionValidationParams.transactionSimulator(),
                operationTracer,
                maybeBlockHeader.get()
            )
            .map { res: TransactionSimulatorResult ->
                TransactionSimulationResult(
                    transaction,
                    res.result
                )
            }
    }

    override fun simulate(
        transaction: Transaction,
        maybeStateOverrides: Optional<StateOverrideMap>,
        pendingBlockHeader: ProcessableBlockHeader,
        operationTracer: OperationTracer,
        isAllowExceedingBalance: Boolean,
        isAllowFutureNonce: Boolean
    ): Optional<TransactionSimulationResult> {
        val callParameter = CallParameter.fromTransaction(transaction)

        return transactionSimulator!!
            .processOnPending(
                callParameter,
                maybeStateOverrides,
                if (isAllowExceedingBalance)
                    if (isAllowFutureNonce)
                        TransactionValidationParams.transactionSimulatorAllowExceedingBalanceAndFutureNonce()
                    else
                        TransactionValidationParams.transactionSimulatorAllowExceedingBalance()
                else
                    if (isAllowFutureNonce)
                        TransactionValidationParams.transactionSimulatorAllowFutureNonce()
                    else
                        TransactionValidationParams.transactionSimulator(),
                operationTracer,
                pendingBlockHeader as org.hyperledger.besu.ethereum.core.ProcessableBlockHeader
            )
            .map { res: TransactionSimulatorResult ->
                TransactionSimulationResult(
                    transaction,
                    res.result
                )
            }
    }
}
