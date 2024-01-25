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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.PendingTransactionResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.PendingTransactionsResult;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.time.Instant;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TxPoolBesuTransactionsTest {

  @Mock private TransactionPool transactionPool;
  private TxPoolBesuTransactions method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_besuTransactions";
  private static final String TRANSACTION_HASH =
      "0xbac263fb39f2a51053fb5e1e52aeb4e980fba9e151aa7e4f12eca95a697aeac9";

  @BeforeEach
  public void setUp() {
    method = new TxPoolBesuTransactions(transactionPool);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldReturnPendingTransactions() {
    long addedAt = 10_000_000;
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {}));

    PendingTransaction pendingTransaction = mock(PendingTransaction.class);
    when(pendingTransaction.getHash()).thenReturn(Hash.fromHexString(TRANSACTION_HASH));
    when(pendingTransaction.isReceivedFromLocalSource()).thenReturn(true);
    when(pendingTransaction.getAddedAt()).thenReturn(addedAt);
    when(transactionPool.getPendingTransactions()).thenReturn(Sets.newHashSet(pendingTransaction));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final PendingTransactionsResult result = (PendingTransactionsResult) actualResponse.getResult();

    final PendingTransactionResult actualResult = result.getResults().stream().findFirst().get();

    assertThat(actualResult.getHash()).isEqualTo(TRANSACTION_HASH);
    assertThat(actualResult.isReceivedFromLocalSource()).isTrue();
    assertThat(actualResult.getAddedToPoolAt()).isEqualTo(Instant.ofEpochMilli(addedAt).toString());
  }
}
