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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.MinPriorityFeePerGasTransactionSelector;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MinPriorityFeePerGasTransactionSelectorTest {
  private AbstractTransactionSelector transactionSelector;

  private final int minPriorityFeeParameter = 7;

  @BeforeEach
  public void initialize() {
    MiningParameters miningParameters =
        MiningParameters.newDefault().setMinPriorityFeePerGas(Wei.of(minPriorityFeeParameter));
    BlockSelectionContext context =
        new BlockSelectionContext(
            miningParameters,
            null,
            null,
            mock(ProcessableBlockHeader.class),
            null,
            null,
            null,
            null);
    transactionSelector = new MinPriorityFeePerGasTransactionSelector(context);
  }

  @Test
  public void shouldNotSelectWhen_PriorityFeePerGas_IsLessThan_MinPriorityFeePerGas() {
    var transaction = mockTransactionWithPriorityFee(minPriorityFeeParameter - 1);
    assertSelectionResult(
        transaction, TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN);
  }

  @Test
  public void shouldSelectWhen_PriorityFeePerGas_IsEqual_MinPriorityFeePerGas() {
    var transaction = mockTransactionWithPriorityFee(minPriorityFeeParameter);
    assertSelectionResult(transaction, TransactionSelectionResult.SELECTED);
  }

  @Test
  public void shouldSelectWhen_PriorityFeePerGas_IsGreaterThan_MinPriorityFeePerGas() {
    var transaction = mockTransactionWithPriorityFee(minPriorityFeeParameter + 1);
    assertSelectionResult(transaction, TransactionSelectionResult.SELECTED);
  }

  @Test
  public void shouldSelectWhenPrioritySender() {
    var prioritySenderTransaction = mockTransactionWithPriorityFee(minPriorityFeeParameter - 1);
    assertSelectionResult(
        prioritySenderTransaction,
        TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN);
    when(prioritySenderTransaction.hasPriority()).thenReturn(true);
    assertSelectionResult(prioritySenderTransaction, TransactionSelectionResult.SELECTED);
  }

  private void assertSelectionResult(
      final PendingTransaction transaction, final TransactionSelectionResult expectedResult) {
    var actualResult = transactionSelector.evaluateTransactionPreProcessing(transaction, null);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  private PendingTransaction mockTransactionWithPriorityFee(final int priorityFeePerGas) {
    PendingTransaction mockTransaction = mock(PendingTransaction.class);
    Transaction transaction = mock(Transaction.class);
    when(mockTransaction.getTransaction()).thenReturn(transaction);
    when(transaction.getEffectivePriorityFeePerGas(any())).thenReturn(Wei.of(priorityFeePerGas));
    return mockTransaction;
  }
}
