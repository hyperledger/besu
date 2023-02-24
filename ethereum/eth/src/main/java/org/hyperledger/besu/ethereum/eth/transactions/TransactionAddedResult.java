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
    DROPPED,
    TRY_NEXT_LAYER,
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
  public static final TransactionAddedResult TX_POOL_FULL =
      new TransactionAddedResult((TransactionInvalidReason.TX_POOL_FULL));
  public static final TransactionAddedResult ADDED_SPARSE =
      new TransactionAddedResult(Status.ADDED, false);
  public static final TransactionAddedResult ADDED = new TransactionAddedResult(Status.ADDED, true);
  public static final TransactionAddedResult TRY_NEXT_LAYER = new TransactionAddedResult(Status.TRY_NEXT_LAYER, false);

  private final Optional<TransactionInvalidReason> rejectReason;

  private final Optional<PendingTransaction> replacedTransaction;

  private final Status status;

  private final boolean prioritizable;

  private TransactionAddedResult(
      final PendingTransaction replacedTransaction) {
    this.replacedTransaction = Optional.of(replacedTransaction);
    this.rejectReason = Optional.empty();
    this.status = Status.REPLACED;
  }

  private TransactionAddedResult(final TransactionInvalidReason rejectReason) {
    this.replacedTransaction = Optional.empty();
    this.rejectReason = Optional.of(rejectReason);
    this.status = Status.INVALID;
    this.prioritizable = false;
  }

  private TransactionAddedResult(final Status status, final boolean prioritizable) {
    this.replacedTransaction = Optional.empty();
    this.rejectReason = Optional.empty();
    this.status = status;
    this.prioritizable = prioritizable;
  }

  public boolean isSuccess() {
    return !isRejected();
  }

  public boolean isRejected() {
    return status == Status.INVALID;
  }

  public boolean isPrioritizable() {
    return prioritizable;
  }

  public boolean isReplacement() {
    return replacedTransaction.isPresent();
  }

  public Optional<TransactionInvalidReason> maybeInvalidReason() {
    return rejectReason;
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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TransactionAddedResult that = (TransactionAddedResult) o;
    return prioritizable == that.prioritizable
        && Objects.equals(rejectReason, that.rejectReason)
        && Objects.equals(replacedTransaction, that.replacedTransaction)
        && status == that.status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(rejectReason, replacedTransaction, status, prioritizable);
  }

  @Override
  public String toString() {
    return "TransactionAddedResult{"
        + "rejectReason="
        + rejectReason
        + ", replacedTransaction="
        + replacedTransaction
        + ", status="
        + status
        + ", prioritizable="
        + prioritizable
        + '}';
  }
}
