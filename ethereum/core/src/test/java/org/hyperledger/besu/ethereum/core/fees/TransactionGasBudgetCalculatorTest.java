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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Arrays;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransactionGasBudgetCalculatorTest {

  private static final long EIP_1559_FORK_BLOCK = 1L;
  private static final TransactionGasBudgetCalculator FRONTIER_CALCULATOR =
      TransactionGasBudgetCalculator.frontier();
  private static final TransactionGasBudgetCalculator EIP1559_CALCULATOR =
      TransactionGasBudgetCalculator.eip1559(new EIP1559(EIP_1559_FORK_BLOCK));
  private static final FeeMarket FEE_MARKET = FeeMarket.eip1559();

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
          {EIP1559_CALCULATOR, false, 5L, EIP_1559_FORK_BLOCK, 10L, 0L, true},
          {
            EIP1559_CALCULATOR,
            false,
            (FEE_MARKET.getMaxGas() / 2) + 1,
            1L,
            FEE_MARKET.getMaxGas(),
            0L,
            false
          },
          {
            EIP1559_CALCULATOR,
            false,
            (FEE_MARKET.getMaxGas() / 2) - 1,
            EIP_1559_FORK_BLOCK,
            10L,
            2L,
            false
          },
          {
            EIP1559_CALCULATOR,
            true,
            (FEE_MARKET.getMaxGas() / 2) + 1,
            EIP_1559_FORK_BLOCK,
            FEE_MARKET.getMaxGas(),
            0L,
            false
          },
          {
            EIP1559_CALCULATOR,
            true,
            (FEE_MARKET.getMaxGas() / 2) - 1,
            EIP_1559_FORK_BLOCK,
            10L,
            2L,
            false
          },
          {
            EIP1559_CALCULATOR,
            true,
            (FEE_MARKET.getMaxGas() / 2) + FEE_MARKET.getGasIncrementAmount(),
            EIP_1559_FORK_BLOCK + 1,
            FEE_MARKET.getMaxGas(),
            0L,
            true
          },
          {
            EIP1559_CALCULATOR,
            true,
            (FEE_MARKET.getMaxGas() / 2) + FEE_MARKET.getGasIncrementAmount() + 1,
            EIP_1559_FORK_BLOCK + 1,
            FEE_MARKET.getMaxGas(),
            0L,
            false
          },
          {
            EIP1559_CALCULATOR,
            false,
            (FEE_MARKET.getMaxGas() / 2) - FEE_MARKET.getGasIncrementAmount(),
            EIP_1559_FORK_BLOCK + 1,
            FEE_MARKET.getMaxGas(),
            0L,
            true
          },
          {
            EIP1559_CALCULATOR,
            false,
            (FEE_MARKET.getMaxGas() / 2) - FEE_MARKET.getGasIncrementAmount() + 1,
            EIP_1559_FORK_BLOCK + 1,
            FEE_MARKET.getMaxGas(),
            0L,
            false
          }
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
                blockHeader(blockNumber, blockHeaderGasLimit),
                gasUsed))
        .isEqualTo(expectedHasBudget);
  }

  private BlockHeader blockHeader(final long blockNumber, final long gasLimit) {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    when(blockHeader.getGasLimit()).thenReturn(gasLimit);
    return blockHeader;
  }

  private Transaction transaction(final boolean isEIP1559, final long gasLimit) {
    final Transaction transaction = mock(Transaction.class);
    when(transaction.isEIP1559Transaction()).thenReturn(isEIP1559);
    when(transaction.getGasLimit()).thenReturn(gasLimit);
    return transaction;
  }
}
