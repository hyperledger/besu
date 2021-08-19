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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.PendingTransactionsStatisticsResult;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TxPoolBesuStatisticsTest {

  @Mock private GasPricePendingTransactionsSorter pendingTransactions;
  private TxPoolBesuStatistics method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_besuStatistics";

  @Before
  public void setUp() {
    method = new TxPoolBesuStatistics(pendingTransactions);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldGiveStatistics() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {}));

    final TransactionInfo local = createTransactionInfo(true);
    final TransactionInfo secondLocal = createTransactionInfo(true);
    final TransactionInfo remote = createTransactionInfo(false);
    when(pendingTransactions.maxSize()).thenReturn(123L);
    when(pendingTransactions.getTransactionInfo())
        .thenReturn(Sets.newHashSet(local, secondLocal, remote));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final PendingTransactionsStatisticsResult result =
        (PendingTransactionsStatisticsResult) actualResponse.getResult();
    assertThat(result.getRemoteCount()).isEqualTo(1);
    assertThat(result.getLocalCount()).isEqualTo(2);
    assertThat(result.getMaxSize()).isEqualTo(123);
  }

  private TransactionInfo createTransactionInfo(final boolean local) {
    final TransactionInfo transactionInfo = mock(TransactionInfo.class);
    when(transactionInfo.isReceivedFromLocalSource()).thenReturn(local);
    return transactionInfo;
  }
}
