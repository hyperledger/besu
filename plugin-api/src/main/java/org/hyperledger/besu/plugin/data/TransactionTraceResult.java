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

import org.hyperledger.besu.datatypes.Hash;

import java.util.Objects;
import java.util.Optional;

public class TransactionTraceResult {
  public enum Status {
    /* the transaction was traced successfully. This might include transactions that have been reverted */
    SUCCESS,
    /* there was an internal error while generating the trace */
    ERROR
  }

  private final Hash txHash;
  private final Status status;
  private final String errorMessage;

  private TransactionTraceResult(
      final Hash txHash, final Status status, final String errorMessage) {
    this.txHash = txHash;
    this.status = status;
    this.errorMessage = errorMessage;
  }

  public static TransactionTraceResult success(final Hash txHash) {
    return new TransactionTraceResult(txHash, Status.SUCCESS, null);
  }

  public static TransactionTraceResult error(final Hash txHash, final String errorMessage) {
    return new TransactionTraceResult(txHash, Status.ERROR, errorMessage);
  }

  public Hash getTxHash() {
    return txHash;
  }

  public Status getStatus() {
    return status;
  }

  public Optional<String> errorMessage() {
    return Optional.ofNullable(errorMessage);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransactionTraceResult that = (TransactionTraceResult) o;
    return Objects.equals(txHash, that.txHash)
        && status == that.status
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(txHash, status, errorMessage);
  }

  @Override
  public String toString() {
    return "TransactionTraceResult{"
        + "txHash="
        + txHash
        + ", status="
        + status
        + ", errorMessage='"
        + errorMessage
        + '\''
        + '}';
  }
}
