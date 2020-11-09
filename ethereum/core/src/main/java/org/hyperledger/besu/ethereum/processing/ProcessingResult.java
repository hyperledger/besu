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

import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** A transaction processing result. */
public interface ProcessingResult {

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
  List<Log> getLogs();

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

  Bytes getOutput();

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
  ValidationResult<TransactionValidator.TransactionInvalidReason> getValidationResult();

  /**
   * Returns the reason why a transaction was reverted (if applicable).
   *
   * @return the revert reason.
   */
  Optional<Bytes> getRevertReason();

  /**
   * Returns the estimate gas used by the transaction Difference between the gas limit and the
   * remaining gas
   *
   * @return the estimate gas used
   */
  long getEstimateGasUsedByTransaction();
}
