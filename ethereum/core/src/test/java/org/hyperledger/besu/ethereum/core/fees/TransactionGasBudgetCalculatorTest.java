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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Arrays;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransactionGasBudgetCalculatorTest {

  private static final TransactionGasBudgetCalculator FRONTIER_CALCULATOR =
      TransactionGasBudgetCalculator.frontier();

  private final TransactionGasBudgetCalculator gasBudgetCalculator;
  private final boolean isEIP1559;
  private final long transactionGasLimit;
  private final long blockNumber;
  private final long blockHeaderGasLimit;
  private final long gasUsed;
  private final boolean expectedHasBudget;

  public TransactionGasBudgetCalculatorTest(
      final TransactionGasBudgetCalculator gasBudgetCalculator,
      final boolean isEIP1559,
      final long transactionGasLimit,
      final long blockNumber,
      final long blockHeaderGasLimit,
      final long gasUsed,
      final boolean expectedHasBudget) {
    this.gasBudgetCalculator = gasBudgetCalculator;
    this.isEIP1559 = isEIP1559;
    this.transactionGasLimit = transactionGasLimit;
    this.blockNumber = blockNumber;
    this.blockHeaderGasLimit = blockHeaderGasLimit;
    this.gasUsed = gasUsed;
    this.expectedHasBudget = expectedHasBudget;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {

    return Arrays.asList(
        new Object[][] {
          {FRONTIER_CALCULATOR, false, 5L, 1L, 10L, 0L, true},
          {FRONTIER_CALCULATOR, false, 11L, 1L, 10L, 0L, false},
          {FRONTIER_CALCULATOR, false, 5L, 1L, 10L, 6L, false},
        });
  }

  @BeforeClass
  public static void initialize() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @AfterClass
  public static void tearDown() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void assertThatCalculatorWorks() {
    assertThat(
            gasBudgetCalculator.hasBudget(
                transaction(isEIP1559, transactionGasLimit),
                blockNumber,
                blockHeaderGasLimit,
                gasUsed))
        .isEqualTo(expectedHasBudget);
  }

  private Transaction transaction(final boolean isEIP1559, final long gasLimit) {
    final Transaction transaction = mock(Transaction.class);
    if (isEIP1559) {
      when(transaction.getType()).thenReturn(TransactionType.EIP1559);
    } else {
      when(transaction.getType()).thenReturn(TransactionType.FRONTIER);
    }
    when(transaction.getGasLimit()).thenReturn(gasLimit);
    return transaction;
  }
}
