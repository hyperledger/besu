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

package org.hyperledger.besu.plugin.data;

import java.util.Objects;
import java.util.Optional;

/**
 * Represent the result of the selection process of a candidate transaction, during the block
 * creation phase.
 */
public class TransactionSelectionResult {

  private enum Status {
    SELECTED,
    BLOCK_FULL(true, false),
    BLOCK_OCCUPANCY_ABOVE_THRESHOLD(true, false),
    INVALID_TRANSIENT(false, false),
    INVALID(false, true);

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

  /** The transaction has been selected to be included in the new block */
  public static final TransactionSelectionResult SELECTED =
      new TransactionSelectionResult(Status.SELECTED);
  /** The transaction has not been selected since the block is full. */
  public static final TransactionSelectionResult BLOCK_FULL =
      new TransactionSelectionResult(Status.BLOCK_FULL);
  /**
   * The transaction has not been selected since too large and the occupancy of the block is enough
   * to stop the selection.
   */
  public static final TransactionSelectionResult BLOCK_OCCUPANCY_ABOVE_THRESHOLD =
      new TransactionSelectionResult(Status.BLOCK_OCCUPANCY_ABOVE_THRESHOLD);
  /**
   * The transaction has not been selected since its gas limit is greater than the block remaining
   * gas, but the selection should continue.
   */
  public static final TransactionSelectionResult TX_TOO_LARGE_FOR_REMAINING_GAS =
      TransactionSelectionResult.invalidTransient("TX_TOO_LARGE_FOR_REMAINING_GAS");
  /**
   * The transaction has not been selected since its current price is below the configured min
   * price, but the selection should continue.
   */
  public static final TransactionSelectionResult CURRENT_TX_PRICE_BELOW_MIN =
      TransactionSelectionResult.invalidTransient("CURRENT_TX_PRICE_BELOW_MIN");
  /**
   * The transaction has not been selected since its blob price is below the current network blob
   * price, but the selection should continue.
   */
  public static final TransactionSelectionResult BLOB_PRICE_BELOW_CURRENT_MIN =
      TransactionSelectionResult.invalidTransient("BLOB_PRICE_BELOW_CURRENT_MIN");

  /**
   * The transaction has not been selected since its priority fee is below the configured min
   * priority fee per gas, but the selection should continue.
   */
  public static final TransactionSelectionResult PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN =
      TransactionSelectionResult.invalidTransient("PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN");

  private final Status status;
  private final Optional<String> maybeInvalidReason;

  private TransactionSelectionResult(final Status status) {
    this(status, null);
  }

  private TransactionSelectionResult(final Status status, final String invalidReason) {
    this.status = status;
    this.maybeInvalidReason = Optional.ofNullable(invalidReason);
  }

  /**
   * Return a selection result that identify the candidate transaction as temporarily invalid, this
   * means that the transaction could become valid at a later time.
   *
   * @param invalidReason the reason why transaction is invalid
   * @return the selection result
   */
  public static TransactionSelectionResult invalidTransient(final String invalidReason) {
    return new TransactionSelectionResult(Status.INVALID_TRANSIENT, invalidReason);
  }

  /**
   * Return a selection result that identify the candidate transaction as permanently invalid, this
   * means that it could be removed safely from the transaction pool.
   *
   * @param invalidReason the reason why transaction is invalid
   * @return the selection result
   */
  public static TransactionSelectionResult invalid(final String invalidReason) {
    return new TransactionSelectionResult(Status.INVALID, invalidReason);
  }

  /**
   * Is the block creation done and the selection process should stop?
   *
   * @return true if the selection process should stop, false otherwise
   */
  public boolean stop() {
    return status.stop;
  }

  /**
   * Should the candidate transaction removed from the transaction pool?
   *
   * @return true if the candidate transaction should be removed from transaction pool, false
   *     otherwise
   */
  public boolean discard() {
    return status.discard;
  }

  /**
   * Is the candidate transaction selected for block inclusion?
   *
   * @return true if the candidate transaction is included in the new block, false otherwise
   */
  public boolean selected() {
    return Status.SELECTED.equals(status);
  }

  /**
   * Optionally return the reason why the transaction is invalid if present
   *
   * @return an optional with the invalid reason
   */
  public Optional<String> maybeInvalidReason() {
    return maybeInvalidReason;
  }

  @Override
  public String toString() {
    return status.name() + maybeInvalidReason.map(ir -> "(" + ir + ")").orElse("");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final TransactionSelectionResult that = (TransactionSelectionResult) o;
    return status == that.status && Objects.equals(maybeInvalidReason, that.maybeInvalidReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, maybeInvalidReason);
  }
}
