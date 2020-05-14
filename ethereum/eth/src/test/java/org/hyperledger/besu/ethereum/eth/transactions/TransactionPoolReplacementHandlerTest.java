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
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

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
          {emptyList(), mock(TransactionInfo.class), mock(TransactionInfo.class), empty(), false},
          {
            singletonList(constantRule(false)),
            mock(TransactionInfo.class),
            mock(TransactionInfo.class),
            empty(),
            false
          },
          {
            singletonList(constantRule(true)),
            mock(TransactionInfo.class),
            mock(TransactionInfo.class),
            empty(),
            true
          },
          {
            constantRules(asList(false, false, false, true)),
            mock(TransactionInfo.class),
            mock(TransactionInfo.class),
            empty(),
            true
          },
        });
  }

  private final List<TransactionPoolReplacementRule> rules;
  private final TransactionInfo oldTransactionInfo;
  private final TransactionInfo newTransactionInfo;
  private final Optional<Long> baseFee;
  private final boolean expectedResult;

  public TransactionPoolReplacementHandlerTest(
      final List<TransactionPoolReplacementRule> rules,
      final TransactionInfo oldTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final Optional<Long> baseFee,
      final boolean expectedResult) {
    this.rules = rules;
    this.oldTransactionInfo = oldTransactionInfo;
    this.newTransactionInfo = newTransactionInfo;
    this.baseFee = baseFee;
    this.expectedResult = expectedResult;
  }

  @Test
  public void shouldReplace() {
    assertThat(
            new TransactionPoolReplacementHandler(rules)
                .shouldReplace(oldTransactionInfo, newTransactionInfo, baseFee))
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
}
