/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import static tech.pegasys.pantheon.ethereum.vm.OperationTracer.NO_TRACING;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.OperationTracer;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

/** Processes transactions. */
public interface TransactionProcessor {

  /** A transaction processing result. */
  interface Result {

    /** The status of the transaction after being processed. */
    enum Status {

      /** The transaction was invalid for processing. */
      INVALID,

      /** The transaction was successfully processed. */
      SUCCESSFUL,

      /** The transaction failed to be completely processed. */
      FAILED
    }

    /**
     * Return the logs produced by the transaction.
     *
     * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
     *
     * @return the logs produced by the transaction
     */
    LogSeries getLogs();

    /**
     * Returns the status of the transaction after being processed.
     *
     * @return the status of the transaction after being processed
     */
    Status getStatus();

    /**
     * Returns the gas remaining after the transaction was processed.
     *
     * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
     *
     * @return the gas remaining after the transaction was processed
     */
    long getGasRemaining();

    BytesValue getOutput();

    /**
     * Returns whether or not the transaction was invalid.
     *
     * @return {@code true} if the transaction was invalid; otherwise {@code false}
     */
    default boolean isInvalid() {
      return getStatus() == Status.INVALID;
    }

    /**
     * Returns whether or not the transaction was successfully processed.
     *
     * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
     */
    default boolean isSuccessful() {
      return getStatus() == Status.SUCCESSFUL;
    }

    /**
     * Returns the transaction validation result.
     *
     * @return the validation result, with the reason for failure (if applicable.)
     */
    ValidationResult<TransactionInvalidReason> getValidationResult();

    /**
     * Returns the reason why a transaction was reverted (if applicable).
     *
     * @return the revert reason.
     */
    Optional<BytesValue> getRevertReason();
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingState Whether the state will be modified by this process
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     TransactionValidator}
   * @return the transaction result
   * @see TransactionValidator
   * @see TransactionValidationParams
   */
  default Result processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingState,
      final TransactionValidationParams transactionValidationParams) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        NO_TRACING,
        blockHashLookup,
        isPersistingState,
        transactionValidationParams);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param operationTracer The tracer to record results of each EVM operation
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingState Whether the state will be modified by this process
   * @return the transaction result
   */
  default Result processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingState) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingState,
        new TransactionValidationParams.Builder().build());
  }

  Result processTransaction(
      Blockchain blockchain,
      WorldUpdater worldState,
      ProcessableBlockHeader blockHeader,
      Transaction transaction,
      Address miningBeneficiary,
      OperationTracer operationTracer,
      BlockHashLookup blockHashLookup,
      Boolean isPersistingState,
      TransactionValidationParams transactionValidationParams);
}
