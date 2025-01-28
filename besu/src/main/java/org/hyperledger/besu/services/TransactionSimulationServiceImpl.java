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
package org.hyperledger.besu.services;

import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.transactionSimulator;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.transactionSimulatorAllowExceedingBalance;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.transactionSimulatorAllowExceedingBalanceAndFutureNonce;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams.transactionSimulatorAllowFutureNonce;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;

import java.util.Optional;

/** TransactionSimulationServiceImpl */
@Unstable
public class TransactionSimulationServiceImpl implements TransactionSimulationService {
  private Blockchain blockchain;
  private TransactionSimulator transactionSimulator;

  /** Create an instance to be configured */
  public TransactionSimulationServiceImpl() {}

  /**
   * Configure the service
   *
   * @param blockchain the blockchain
   * @param transactionSimulator transaction simulator
   */
  public void init(final Blockchain blockchain, final TransactionSimulator transactionSimulator) {
    this.blockchain = blockchain;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public ProcessableBlockHeader simulatePendingBlockHeader() {
    return transactionSimulator.simulatePendingBlockHeader();
  }

  @Override
  public Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final Hash blockHash,
      final OperationTracer operationTracer,
      final boolean isAllowExceedingBalance) {

    final CallParameter callParameter = CallParameter.fromTransaction(transaction);

    final var maybeBlockHeader =
        blockchain.getBlockHeader(blockHash).or(() -> blockchain.getBlockHeaderSafe(blockHash));

    if (maybeBlockHeader.isEmpty()) {
      return Optional.of(
          new TransactionSimulationResult(
              transaction,
              TransactionProcessingResult.invalid(
                  ValidationResult.invalid(TransactionInvalidReason.BLOCK_NOT_FOUND))));
    }

    return transactionSimulator
        .process(
            callParameter,
            isAllowExceedingBalance
                ? transactionSimulatorAllowExceedingBalance()
                : transactionSimulator(),
            operationTracer,
            maybeBlockHeader.get())
        .map(res -> new TransactionSimulationResult(transaction, res.result()));
  }

  @Override
  public Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final ProcessableBlockHeader pendingBlockHeader,
      final OperationTracer operationTracer,
      final boolean isAllowExceedingBalance,
      final boolean isAllowFutureNonce) {

    final CallParameter callParameter = CallParameter.fromTransaction(transaction);

    return transactionSimulator
        .processOnPending(
            callParameter,
            maybeStateOverrides,
            isAllowExceedingBalance
                ? isAllowFutureNonce
                    ? transactionSimulatorAllowExceedingBalanceAndFutureNonce()
                    : transactionSimulatorAllowExceedingBalance()
                : isAllowFutureNonce
                    ? transactionSimulatorAllowFutureNonce()
                    : transactionSimulator(),
            operationTracer,
            (org.hyperledger.besu.ethereum.core.ProcessableBlockHeader) pendingBlockHeader)
        .map(res -> new TransactionSimulationResult(transaction, res.result()));
  }
}
