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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Pending transaction result. */
@JsonPropertyOrder({"hash", "isReceivedFromLocalSource"})
public class PendingTransactionResult implements TransactionResult {

  private final String hash;
  private final boolean isReceivedFromLocalSource;
  private final Instant addedToPoolAt;

  /**
   * Instantiates a new Pending transaction result.
   *
   * @param pendingTransaction the pending transaction
   */
  public PendingTransactionResult(final PendingTransaction pendingTransaction) {
    hash = pendingTransaction.getHash().toString();
    isReceivedFromLocalSource = pendingTransaction.isReceivedFromLocalSource();
    addedToPoolAt = Instant.ofEpochMilli(pendingTransaction.getAddedAt());
  }

  /**
   * Gets hash.
   *
   * @return the hash
   */
  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  /**
   * Gets added to pool at.
   *
   * @return the added to pool at
   */
  @JsonGetter(value = "addedToPoolAt")
  public String getAddedToPoolAt() {
    return addedToPoolAt.toString();
  }

  /**
   * Is received from local source boolean.
   *
   * @return the boolean
   */
  @JsonGetter(value = "isReceivedFromLocalSource")
  public boolean isReceivedFromLocalSource() {
    return isReceivedFromLocalSource;
  }
}
