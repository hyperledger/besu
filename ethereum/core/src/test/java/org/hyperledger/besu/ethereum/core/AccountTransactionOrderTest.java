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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.util.stream.Stream;

import org.junit.Test;

public class AccountTransactionOrderTest {

  private static final KeyPair KEYS = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private final Transaction transaction1 = transaction(1);
  private final Transaction transaction2 = transaction(2);
  private final Transaction transaction3 = transaction(3);
  private final Transaction transaction4 = transaction(4);
  private final AccountTransactionOrder accountTransactionOrder =
      new AccountTransactionOrder(
          Stream.of(transaction1, transaction2, transaction3, transaction4));

  @Test
  public void shouldProcessATransactionImmediatelyIfItsTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1);
  }

  @Test
  public void shouldDeferProcessingATransactionIfItIsNotTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
  }

  @Test
  public void shouldProcessDeferredTransactionsAfterPrerequisiteIsProcessed() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(transaction3)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1, transaction2, transaction3);
  }

  @Test
  public void shouldNotProcessDeferredTransactionsThatAreNotYetDue() {
    assertThat(accountTransactionOrder.transactionsToProcess(transaction2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(transaction4)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(transaction1))
        .containsExactly(transaction1, transaction2);

    assertThat(accountTransactionOrder.transactionsToProcess(transaction3))
        .containsExactly(transaction3, transaction4);
  }

  private Transaction transaction(final int nonce) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(KEYS);
  }
}
