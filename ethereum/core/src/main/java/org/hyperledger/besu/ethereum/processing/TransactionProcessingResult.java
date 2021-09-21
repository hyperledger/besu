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
package org.hyperledger.besu.ethereum.processing;

import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransactionProcessingResult {

  /** The status of the transaction after being processed. */
  public enum Status {

    /** The transaction was invalid for processing. */
    INVALID,

    /** The transaction was successfully processed. */
    SUCCESSFUL,

    /** The transaction failed to be completely processed. */
    FAILED
  }

  private final Status status;

  private final long estimateGasUsedByTransaction;

  private final long gasRemaining;

  private final List<Log> logs;

  private final Bytes output;

  private final ValidationResult<TransactionInvalidReason> validationResult;
  private final Optional<Bytes> revertReason;

  public static TransactionProcessingResult invalid(
      final ValidationResult<TransactionInvalidReason> validationResult) {
    return new TransactionProcessingResult(
        Status.INVALID, new ArrayList<>(), -1, -1, Bytes.EMPTY, validationResult, Optional.empty());
  }

  public static TransactionProcessingResult failed(
      final long gasUsedByTransaction,
      final long gasRemaining,
      final ValidationResult<TransactionInvalidReason> validationResult,
      final Optional<Bytes> revertReason) {
    return new TransactionProcessingResult(
        Status.FAILED,
        new ArrayList<>(),
        gasUsedByTransaction,
        gasRemaining,
        Bytes.EMPTY,
        validationResult,
        revertReason);
  }

  public static TransactionProcessingResult successful(
      final List<Log> logs,
      final long gasUsedByTransaction,
      final long gasRemaining,
      final Bytes output,
      final ValidationResult<TransactionInvalidReason> validationResult) {
    return new TransactionProcessingResult(
        Status.SUCCESSFUL,
        logs,
        gasUsedByTransaction,
        gasRemaining,
        output,
        validationResult,
        Optional.empty());
  }

  public TransactionProcessingResult(
      final Status status,
      final List<Log> logs,
      final long estimateGasUsedByTransaction,
      final long gasRemaining,
      final Bytes output,
      final ValidationResult<TransactionInvalidReason> validationResult,
      final Optional<Bytes> revertReason) {
    this.status = status;
    this.logs = logs;
    this.estimateGasUsedByTransaction = estimateGasUsedByTransaction;
    this.gasRemaining = gasRemaining;
    this.output = output;
    this.validationResult = validationResult;
    this.revertReason = revertReason;
  }

  /**
   * Return the logs produced by the transaction.
   *
   * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
   *
   * @return the logs produced by the transaction
   */
  public List<Log> getLogs() {
    return logs;
  }

  /**
   * Returns the gas remaining after the transaction was processed.
   *
   * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
   *
   * @return the gas remaining after the transaction was processed
   */
  public long getGasRemaining() {
    return gasRemaining;
  }

  /**
   * Returns the estimate gas used by the transaction Difference between the gas limit and the
   * remaining gas
   *
   * @return the estimate gas used
   */
  public long getEstimateGasUsedByTransaction() {
    return estimateGasUsedByTransaction;
  }

  /**
   * Returns the status of the transaction after being processed.
   *
   * @return the status of the transaction after being processed
   */
  public Status getStatus() {
    return status;
  }

  public Bytes getOutput() {
    return output;
  }

  /**
   * Returns whether or not the transaction was invalid.
   *
   * @return {@code true} if the transaction was invalid; otherwise {@code false}
   */
  public boolean isInvalid() {
    return getStatus() == Status.INVALID;
  }

  /**
   * Returns whether or not the transaction was successfully processed.
   *
   * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
   */
  public boolean isSuccessful() {
    return getStatus() == Status.SUCCESSFUL;
  }

  /**
   * Returns the transaction validation result.
   *
   * @return the validation result, with the reason for failure (if applicable.)
   */
  public ValidationResult<TransactionInvalidReason> getValidationResult() {
    return validationResult;
  }

  /**
   * Returns the reason why a transaction was reverted (if applicable).
   *
   * @return the revert reason.
   */
  public Optional<Bytes> getRevertReason() {
    return revertReason;
  }
}
