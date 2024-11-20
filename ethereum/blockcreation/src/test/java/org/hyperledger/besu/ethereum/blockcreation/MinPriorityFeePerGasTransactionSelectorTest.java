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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionEvaluationContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.MinPriorityFeePerGasTransactionSelector;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import com.google.common.base.Stopwatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MinPriorityFeePerGasTransactionSelectorTest {
  private AbstractTransactionSelector transactionSelector;

  private final int minPriorityFeeParameter = 7;
  @Mock private ProcessableBlockHeader pendingBlockHeader;

  @BeforeEach
  public void initialize() {
    MiningConfiguration miningConfiguration =
        MiningConfiguration.newDefault().setMinPriorityFeePerGas(Wei.of(minPriorityFeeParameter));
    BlockSelectionContext context =
        new BlockSelectionContext(
            miningConfiguration, null, null, null, pendingBlockHeader, null, null, null, null);
    transactionSelector = new MinPriorityFeePerGasTransactionSelector(context);
  }

  @Test
  public void shouldNotSelectWhen_PriorityFeePerGas_IsLessThan_MinPriorityFeePerGas() {
    var transaction = mockTransactionEvaluationContext(minPriorityFeeParameter - 1);
    assertSelectionResult(
        transaction, TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN);
  }

  @Test
  public void shouldSelectWhen_PriorityFeePerGas_IsEqual_MinPriorityFeePerGas() {
    var transaction = mockTransactionEvaluationContext(minPriorityFeeParameter);
    assertSelectionResult(transaction, TransactionSelectionResult.SELECTED);
  }

  @Test
  public void shouldSelectWhen_PriorityFeePerGas_IsGreaterThan_MinPriorityFeePerGas() {
    var transaction = mockTransactionEvaluationContext(minPriorityFeeParameter + 1);
    assertSelectionResult(transaction, TransactionSelectionResult.SELECTED);
  }

  @Test
  public void shouldSelectWhenPrioritySender() {
    final var evaluationContext = mockTransactionEvaluationContext(minPriorityFeeParameter - 1);
    assertSelectionResult(
        evaluationContext, TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN);
    when(evaluationContext.getPendingTransaction().hasPriority()).thenReturn(true);
    assertSelectionResult(evaluationContext, TransactionSelectionResult.SELECTED);
  }

  private void assertSelectionResult(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult expectedResult) {
    var actualResult =
        transactionSelector.evaluateTransactionPreProcessing(evaluationContext, null);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  private TransactionEvaluationContext mockTransactionEvaluationContext(
      final int priorityFeePerGas) {
    PendingTransaction pendingTransaction = mock(PendingTransaction.class);
    Transaction transaction = mock(Transaction.class);
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    when(transaction.getEffectivePriorityFeePerGas(any())).thenReturn(Wei.of(priorityFeePerGas));
    return new TransactionEvaluationContext(
        pendingBlockHeader, pendingTransaction, Stopwatch.createStarted(), Wei.ONE, Wei.ONE);
  }
}
