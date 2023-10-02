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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.NONCE_TOO_LOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.AbstractTransactionPoolTest;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public abstract class AbstractLegacyTransactionPoolTest extends AbstractTransactionPoolTest {

  @Test
  public void shouldNotAddRemoteTransactionsWhenThereIsALowestInvalidNonceForTheSender() {
    givenTransactionIsValid(transaction1);
    when(transactionValidatorFactory.get().validate(eq(transaction0), any(Optional.class), any()))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));

    transactionPool.addRemoteTransactions(asList(transaction0, transaction1));

    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transaction1);
    verify(transactionBroadcaster, never()).onTransactionsAdded(singletonList(transaction1));
  }

  @Test
  public void shouldRejectRemoteTransactionsWhenAnInvalidTransactionWithLowerNonceExists() {
    final Transaction invalidTx =
        createBaseTransaction(0).gasLimit(blockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(invalidTx);
    givenTransactionIsValid(transaction1);

    addAndAssertRemoteTransactionInvalid(invalidTx);
    addAndAssertRemoteTransactionInvalid(transaction1);
  }
}
