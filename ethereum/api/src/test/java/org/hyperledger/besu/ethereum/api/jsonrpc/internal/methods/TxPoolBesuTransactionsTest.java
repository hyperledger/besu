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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.PendingTransactionsResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionInfoResult;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;

import java.time.Instant;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TxPoolBesuTransactionsTest {

  @Mock private GasPricePendingTransactionsSorter pendingTransactions;
  private TxPoolBesuTransactions method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_besuTransactions";
  private static final String TRANSACTION_HASH =
      "0xbac263fb39f2a51053fb5e1e52aeb4e980fba9e151aa7e4f12eca95a697aeac9";

  @Before
  public void setUp() {
    method = new TxPoolBesuTransactions(pendingTransactions);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldReturnPendingTransactions() {
    Instant addedAt = Instant.ofEpochMilli(10_000_000);
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {}));

    TransactionInfo transactionInfo = mock(TransactionInfo.class);
    when(transactionInfo.getHash()).thenReturn(Hash.fromHexString(TRANSACTION_HASH));
    when(transactionInfo.isReceivedFromLocalSource()).thenReturn(true);
    when(transactionInfo.getAddedToPoolAt()).thenReturn(addedAt);
    when(pendingTransactions.getTransactionInfo()).thenReturn(Sets.newHashSet(transactionInfo));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final PendingTransactionsResult result = (PendingTransactionsResult) actualResponse.getResult();

    final TransactionInfoResult actualTransactionInfo =
        result.getResults().stream().findFirst().get();

    assertThat(actualTransactionInfo.getHash()).isEqualTo(TRANSACTION_HASH);
    assertThat(actualTransactionInfo.isReceivedFromLocalSource()).isTrue();
    assertThat(actualTransactionInfo.getAddedToPoolAt()).isEqualTo(addedAt.toString());
  }
}
