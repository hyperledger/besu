/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.ethereum.crosschain;

import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

public class SubordinateViewExecutor {
  private TransactionSimulator transactionSimulator;

  public SubordinateViewExecutor(final TransactionSimulator transactionSimulator) {
    this.transactionSimulator = transactionSimulator;
  }

  /**
   * Process the Subordinate View for this block number.
   *
   * @param subordinateView Subordinate view to execute.
   * @param blockNumber block number to execute the view call at.
   * @return TransactionSimulatorResult if the execution completed. TransactionInvalidReason if
   *     there was an error.
   */
  public Object getResult(final CrosschainTransaction subordinateView, final long blockNumber) {
    Object returnValue =
        this.transactionSimulator
            .process(subordinateView, blockNumber)
            .map(result -> result.getValidationResult().either((() -> result), reason -> reason))
            .orElse(null);
    return returnValue;
  }
}
