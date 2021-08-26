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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransactionPoolReplacementHandlerTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return asList(
        new Object[][] {
          {emptyList(), mockTransactionInfo(), mockTransactionInfo(), false},
          {singletonList(constantRule(false)), mockTransactionInfo(), mockTransactionInfo(), false},
          {singletonList(constantRule(true)), mockTransactionInfo(), mockTransactionInfo(), true},
          {
            constantRules(asList(false, false, false, true)),
            mockTransactionInfo(),
            mockTransactionInfo(),
            true
          },
        });
  }

  private final List<TransactionPoolReplacementRule> rules;
  private final TransactionInfo oldTransactionInfo;
  private final TransactionInfo newTransactionInfo;
  private final boolean expectedResult;
  private final BlockHeader header;

  public TransactionPoolReplacementHandlerTest(
      final List<TransactionPoolReplacementRule> rules,
      final TransactionInfo oldTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final boolean expectedResult) {
    this.rules = rules;
    this.oldTransactionInfo = oldTransactionInfo;
    this.newTransactionInfo = newTransactionInfo;
    this.expectedResult = expectedResult;
    header = mock(BlockHeader.class);
    when(header.getBaseFee()).thenReturn(Optional.empty());
  }

  @Test
  public void shouldReplace() {
    assertThat(
            new TransactionPoolReplacementHandler(rules)
                .shouldReplace(oldTransactionInfo, newTransactionInfo, header))
        .isEqualTo(expectedResult);
  }

  private static TransactionPoolReplacementRule constantRule(final boolean returnValue) {
    return (ot, nt, bf) -> returnValue;
  }

  private static List<TransactionPoolReplacementRule> constantRules(
      final List<Boolean> returnValues) {
    return returnValues.stream()
        .map(TransactionPoolReplacementHandlerTest::constantRule)
        .collect(Collectors.toList());
  }

  private static TransactionInfo mockTransactionInfo() {
    final TransactionInfo transactionInfo = mock(TransactionInfo.class);
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getType()).thenReturn(TransactionType.FRONTIER);
    when(transactionInfo.getTransaction()).thenReturn(transaction);
    return transactionInfo;
  }
}
