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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.PendingTransactionsStatisticsResult;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TxPoolPantheonStatisticsTest {

  @Mock private PendingTransactions pendingTransactions;
  private TxPoolPantheonStatistics method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_pantheonStatistics";

  @Before
  public void setUp() {
    method = new TxPoolPantheonStatistics(pendingTransactions);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldGiveStatistics() {
    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {});

    final TransactionInfo local = createTransactionInfo(true);
    final TransactionInfo secondLocal = createTransactionInfo(true);
    final TransactionInfo remote = createTransactionInfo(false);
    when(pendingTransactions.maxSize()).thenReturn(123L);
    when(pendingTransactions.getTransactionInfo())
        .thenReturn(Sets.newHashSet(local, secondLocal, remote));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final PendingTransactionsStatisticsResult result =
        (PendingTransactionsStatisticsResult) actualResponse.getResult();
    assertEquals(1, result.getRemoteCount());
    assertEquals(2, result.getLocalCount());
    assertEquals(123, result.getMaxSize());
  }

  private TransactionInfo createTransactionInfo(final boolean local) {
    final TransactionInfo transactionInfo = mock(TransactionInfo.class);
    when(transactionInfo.isReceivedFromLocalSource()).thenReturn(local);
    return transactionInfo;
  }
}
