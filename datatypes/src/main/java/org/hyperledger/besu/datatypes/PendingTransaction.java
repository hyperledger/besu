/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.datatypes;

/** Represent a transaction that has not confirmed yet, and stays in the transaction pool */
public interface PendingTransaction {
  /**
   * Get the underlying transaction
   *
   * @return the underlying transaction
   */
  Transaction getTransaction();

  /**
   * Has this transaction been received from the RPC API?
   *
   * @return true if it is a local sent transaction
   */
  boolean isReceivedFromLocalSource();

  /**
   * Should this transaction be prioritized?
   *
   * @return true if it is a transaction with priority
   */
  boolean hasPriority();

  /**
   * Timestamp in millisecond when this transaction has been added to the pool
   *
   * @return timestamp
   */
  long getAddedAt();
}
