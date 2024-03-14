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

import org.hyperledger.besu.datatypes.Transaction;

/**
 * TransactionSimulationResult
 *
 * @param transaction tx
 * @param result res
 */
public record TransactionSimulationResult(
    Transaction transaction, TransactionProcessingResult result) {

  /**
   * Was the simulation successful?
   *
   * @return boolean
   */
  public boolean isSuccessful() {
    return result.isSuccessful();
  }

  /**
   * Was the transaction invalid?
   *
   * @return invalid
   */
  public boolean isInvalid() {
    return result.isInvalid();
  }

  /**
   * Estimated gas used by the transaction
   *
   * @return estimated gas used
   */
  public long getGasEstimate() {
    return transaction.getGasLimit() - result.getGasRemaining();
  }
}
