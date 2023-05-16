/*
 * Copyright Hyperledger Besu Contributors.
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

import java.util.Optional;

public class TransactionSelectionResult {

  private enum Status {
    DELETE_TRANSACTION_AND_CONTINUE,
    CONTINUE,
    BLOCK_OCCUPANCY_ABOVE_THRESHOLD(true, false),
    TX_TOO_LARGE,
    TX_PRICE_BELOW_MIN,
    DATA_PRICE_BELOW_MIN,
    INVALID_TRANSIENT(false, false),
    INVALID(false, true),
    SELECTED,
    COMPLETE_OPERATION;

    private final boolean stop;
    private final boolean discard;

    Status() {
      this.stop = false;
      this.discard = false;
    }

    Status(final boolean stop, final boolean discard) {
      this.stop = stop;
      this.discard = discard;
    }

    @Override
    public String toString() {
      return name() + " (stop=" + stop + ", discard=" + discard + ")";
    }
  }

  public static final TransactionSelectionResult SELECTED =
      new TransactionSelectionResult(Status.SELECTED);
  public static final TransactionSelectionResult BLOCK_OCCUPANCY_ABOVE_THRESHOLD =
      new TransactionSelectionResult(Status.BLOCK_OCCUPANCY_ABOVE_THRESHOLD);
  public static final TransactionSelectionResult TX_TOO_LARGE =
      new TransactionSelectionResult(Status.TX_TOO_LARGE);
  public static final TransactionSelectionResult TX_PRICE_BELOW_MIN =
      new TransactionSelectionResult(Status.TX_PRICE_BELOW_MIN);
  public static final TransactionSelectionResult DATA_PRICE_BELOW_MIN =
      new TransactionSelectionResult(Status.DATA_PRICE_BELOW_MIN);

  private final Status status;
  private final Optional<TransactionInvalidReason> maybeInvalidReason;

  public TransactionSelectionResult(final Status status) {
    this.status = status;
    this.maybeInvalidReason = Optional.empty();
  }

  public TransactionSelectionResult(
      final Status status, final TransactionInvalidReason invalidReason) {
    this.status = status;
    this.maybeInvalidReason = Optional.of(invalidReason);
  }

  public static TransactionSelectionResult invalidTransient(
      final TransactionInvalidReason invalidReason) {
    return new TransactionSelectionResult(Status.INVALID_TRANSIENT, invalidReason);
  }

  public static TransactionSelectionResult invalid(final TransactionInvalidReason invalidReason) {
    return new TransactionSelectionResult(Status.INVALID, invalidReason);
  }

  public boolean stop() {
    return status.stop;
  }

  public boolean discard() {
    return status.discard;
  }

  @Override
  public String toString() {
    return status + maybeInvalidReason.map(ir -> "(" + ir.name() + ")").orElse("");
  }
}
