/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.Objects;
import java.util.Optional;

public final class TransactionAddedResult {
  private enum Status {
    INVALID,
    REPLACED,
    POSTPONED,
    ADDED
  }

  public static final TransactionAddedResult ALREADY_KNOWN =
      new TransactionAddedResult(TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN);
  public static final TransactionAddedResult REJECTED_UNDERPRICED_REPLACEMENT =
      new TransactionAddedResult(TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED);
  public static final TransactionAddedResult NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER =
      new TransactionAddedResult(TransactionInvalidReason.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER);
  public static final TransactionAddedResult LOWER_NONCE_INVALID_TRANSACTION_KNOWN =
      new TransactionAddedResult(TransactionInvalidReason.LOWER_NONCE_INVALID_TRANSACTION_EXISTS);
  public static final TransactionAddedResult POSTPONED =
      new TransactionAddedResult(Status.POSTPONED);
  public static final TransactionAddedResult ADDED = new TransactionAddedResult(Status.ADDED);

  private final Optional<TransactionInvalidReason> invalidReason;

  private final Optional<PendingTransaction> replacedTransaction;

  private final Status status;

  private TransactionAddedResult(final PendingTransaction replacedTransaction) {
    this.replacedTransaction = Optional.of(replacedTransaction);
    this.invalidReason = Optional.empty();
    this.status = Status.REPLACED;
  }

  private TransactionAddedResult(final TransactionInvalidReason invalidReason) {
    this.replacedTransaction = Optional.empty();
    this.invalidReason = Optional.of(invalidReason);
    this.status = Status.INVALID;
  }

  private TransactionAddedResult(final Status status) {
    this.replacedTransaction = Optional.empty();
    this.invalidReason = Optional.empty();
    this.status = status;
  }

  public boolean isSuccess() {
    return !isInvalid();
  }

  public boolean isInvalid() {
    return status == Status.INVALID;
  }

  public boolean isReplacement() {
    return replacedTransaction.isPresent();
  }

  public Optional<TransactionInvalidReason> maybeInvalidReason() {
    return invalidReason;
  }

  public Optional<PendingTransaction> maybeReplacedTransaction() {
    return replacedTransaction;
  }

  public static TransactionAddedResult createForReplacement(
      final PendingTransaction replacedTransaction) {
    return new TransactionAddedResult(replacedTransaction);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransactionAddedResult that = (TransactionAddedResult) o;

    if (!Objects.equals(invalidReason, that.invalidReason)) {
      return false;
    }
    if (!Objects.equals(replacedTransaction, that.replacedTransaction)) {
      return false;
    }
    return status == that.status;
  }

  @Override
  public int hashCode() {
    int result = invalidReason != null ? invalidReason.hashCode() : 0;
    result = 31 * result + (replacedTransaction != null ? replacedTransaction.hashCode() : 0);
    result = 31 * result + (status != null ? status.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "status="
        + status
        + ", invalidReason="
        + invalidReason
        + ", replacedTransaction="
        + replacedTransaction;
  }
}
