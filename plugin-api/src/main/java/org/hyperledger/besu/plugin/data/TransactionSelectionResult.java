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

public class TransactionSelectionResult {

  private enum Status {
    SELECTED,
    BLOCK_FULL(true, false),
    BLOCK_OCCUPANCY_ABOVE_THRESHOLD(true, false),
    TX_TOO_LARGE,
    TX_PRICE_BELOW_MIN,
    DATA_PRICE_BELOW_MIN,
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

  public static final TransactionSelectionResult SELECTED =
      new TransactionSelectionResult(Status.SELECTED);
  public static final TransactionSelectionResult BLOCK_FULL =
      new TransactionSelectionResult(Status.BLOCK_FULL);
  public static final TransactionSelectionResult BLOCK_OCCUPANCY_ABOVE_THRESHOLD =
      new TransactionSelectionResult(Status.BLOCK_OCCUPANCY_ABOVE_THRESHOLD);
  public static final TransactionSelectionResult TX_TOO_LARGE =
      new TransactionSelectionResult(Status.TX_TOO_LARGE);
  public static final TransactionSelectionResult TX_PRICE_BELOW_MIN =
      new TransactionSelectionResult(Status.TX_PRICE_BELOW_MIN);
  public static final TransactionSelectionResult DATA_PRICE_BELOW_MIN =
      new TransactionSelectionResult(Status.DATA_PRICE_BELOW_MIN);

  private final Status status;
  private final Optional<String> maybeInvalidReason;

  public TransactionSelectionResult(final Status status) {
    this.status = status;
    this.maybeInvalidReason = Optional.empty();
  }

  public TransactionSelectionResult(final Status status, final String invalidReason) {
    this.status = status;
    this.maybeInvalidReason = Optional.of(invalidReason);
  }

  public static TransactionSelectionResult invalidTransient(final String invalidReason) {
    return new TransactionSelectionResult(Status.INVALID_TRANSIENT, invalidReason);
  }

  public static TransactionSelectionResult invalid(final String invalidReason) {
    return new TransactionSelectionResult(Status.INVALID, invalidReason);
  }

  public boolean stop() {
    return status.stop;
  }

  public boolean discard() {
    return status.discard;
  }

  public boolean skip() {
    return !Status.SELECTED.equals(status);
  }

  @Override
  public String toString() {
    return status + maybeInvalidReason.map(ir -> "(" + ir + ")").orElse("");
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
