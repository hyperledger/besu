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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransactionReplacementByPriceRuleTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return asList(
        new Object[][] {
          {5L, 6L, true},
          {5L, 5L, false},
          {5L, 4L, false},
        });
  }

  private final long oldPrice;
  private final long newPrice;
  private final boolean expected;

  public TransactionReplacementByPriceRuleTest(
      final long oldPrice, final long newPrice, final boolean expected) {
    this.oldPrice = oldPrice;
    this.newPrice = newPrice;
    this.expected = expected;
  }

  @Test
  public void shouldReplace() {
    assertThat(
            new TransactionReplacementByPriceRule()
                .shouldReplace(
                    transactionInfoWithPrice(oldPrice), transactionInfoWithPrice(newPrice)))
        .isEqualTo(expected);
  }

  private static TransactionInfo transactionInfoWithPrice(final long price) {
    final TransactionInfo transactionInfo = mock(TransactionInfo.class);
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getGasPrice()).thenReturn(Wei.of(price));
    when(transactionInfo.getTransaction()).thenReturn(transaction);
    return transactionInfo;
  }
}
