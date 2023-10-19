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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class AccountTransactionOrderTest {

  private static final KeyPair KEYS = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private final PendingTransaction pendingTx1 = new PendingTransaction.Remote((transaction(1)));
  private final PendingTransaction pendingTx2 = new PendingTransaction.Remote((transaction(2)));
  private final PendingTransaction pendingTx3 = new PendingTransaction.Remote((transaction(3)));
  private final PendingTransaction pendingTx4 = new PendingTransaction.Remote((transaction(4)));
  private final AccountTransactionOrder accountTransactionOrder =
      new AccountTransactionOrder(Stream.of(pendingTx1, pendingTx2, pendingTx3, pendingTx4));

  @Test
  public void shouldProcessATransactionImmediatelyIfItsTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx1))
        .containsExactly(pendingTx1);
  }

  @Test
  public void shouldDeferProcessingATransactionIfItIsNotTheLowestNonce() {
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx2)).isEmpty();
  }

  @Test
  public void shouldProcessDeferredTransactionsAfterPrerequisiteIsProcessed() {
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx3)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx1))
        .containsExactly(pendingTx1, pendingTx2, pendingTx3);
  }

  @Test
  public void shouldNotProcessDeferredTransactionsThatAreNotYetDue() {
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx2)).isEmpty();
    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx4)).isEmpty();

    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx1))
        .containsExactly(pendingTx1, pendingTx2);

    assertThat(accountTransactionOrder.transactionsToProcess(pendingTx3))
        .containsExactly(pendingTx3, pendingTx4);
  }

  private Transaction transaction(final int nonce) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(KEYS);
  }
}
