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
package org.hyperledger.besu.ethereum.core.fees;

import org.hyperledger.besu.ethereum.core.Transaction;

@FunctionalInterface
public interface TransactionGasBudgetCalculator {

  boolean hasBudget(
      final Transaction transaction,
      final long blockNumber,
      final long gasLimit,
      final long gasUsed);

  static TransactionGasBudgetCalculator frontier() {
    return gasBudgetCalculator((blockNumber, gasLimit, ignored) -> gasLimit);
  }

  static TransactionGasBudgetCalculator eip1559(final EIP1559 eip1559) {
    return gasBudgetCalculator(
        (blockNumber, gasLimit, transaction) ->
            eip1559.isEIP1559(blockNumber)
                ? gasLimit * eip1559.getFeeMarket().getSlackCoefficient()
                : gasLimit);
  }

  static TransactionGasBudgetCalculator gasBudgetCalculator(
      final BlockGasLimitCalculator blockGasLimitCalculator) {
    return (transaction, blockNumber, gasLimit, gasUsed) -> {
      final long remainingGasBudget =
          blockGasLimitCalculator.apply(blockNumber, gasLimit, transaction) - gasUsed;
      return Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) <= 0;
    };
  }

  @FunctionalInterface
  interface BlockGasLimitCalculator {
    long apply(final long blockNumber, final long gasLimit, Transaction transaction);
  }
}
