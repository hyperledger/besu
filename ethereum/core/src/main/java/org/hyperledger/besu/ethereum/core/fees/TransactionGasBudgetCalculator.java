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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

@FunctionalInterface
public interface TransactionGasBudgetCalculator {

  boolean hasBudget(
      final Transaction transaction, final BlockHeader blockHeader, final long gasUsed);

  static TransactionGasBudgetCalculator frontier() {
    return gasBudgetCalculator((blockHeader, ignored) -> blockHeader.getGasLimit());
  }

  static TransactionGasBudgetCalculator eip1559(final EIP1559 eip1559) {
    return gasBudgetCalculator(
        (blockHeader, transaction) ->
            transaction.isEIP1559Transaction()
                ? eip1559.eip1559GasPool(blockHeader.getNumber())
                : eip1559.legacyGasPool(blockHeader.getNumber()));
  }

  static TransactionGasBudgetCalculator gasBudgetCalculator(
      final BlockGasLimitCalculator blockGasLimitCalculator) {
    return (transaction, blockHeader, gasUsed) -> {
      final long remainingGasBudget =
          blockGasLimitCalculator.apply(blockHeader, transaction) - gasUsed;
      return Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) <= 0;
    };
  }

  @FunctionalInterface
  interface BlockGasLimitCalculator {
    long apply(BlockHeader blockHeader, Transaction transaction);
  }
}
