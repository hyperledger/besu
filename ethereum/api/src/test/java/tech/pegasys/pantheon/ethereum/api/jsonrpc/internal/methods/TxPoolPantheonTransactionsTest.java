/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.PendingTransactionsResult;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.TransactionInfoResult;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

import java.time.Instant;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TxPoolPantheonTransactionsTest {

  @Mock private PendingTransactions pendingTransactions;
  private TxPoolPantheonTransactions method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_pantheonTransactions";
  private static final String TRANSACTION_HASH =
      "0xbac263fb39f2a51053fb5e1e52aeb4e980fba9e151aa7e4f12eca95a697aeac9";

  @Before
  public void setUp() {
    method = new TxPoolPantheonTransactions(pendingTransactions);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldReturnPendingTransactions() {
    Instant addedAt = Instant.ofEpochMilli(10_000_000);
    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {});

    TransactionInfo transactionInfo = mock(TransactionInfo.class);
    when(transactionInfo.getHash()).thenReturn(Hash.fromHexString(TRANSACTION_HASH));
    when(transactionInfo.isReceivedFromLocalSource()).thenReturn(true);
    when(transactionInfo.getAddedToPoolAt()).thenReturn(addedAt);
    when(pendingTransactions.getTransactionInfo()).thenReturn(Sets.newHashSet(transactionInfo));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final PendingTransactionsResult result = (PendingTransactionsResult) actualResponse.getResult();

    final TransactionInfoResult actualTransactionInfo =
        result.getResults().stream().findFirst().get();
    assertEquals(TRANSACTION_HASH, actualTransactionInfo.getHash());
    assertEquals(true, actualTransactionInfo.isReceivedFromLocalSource());
    assertEquals(addedAt.toString(), actualTransactionInfo.getAddedToPoolAt());
  }
}
