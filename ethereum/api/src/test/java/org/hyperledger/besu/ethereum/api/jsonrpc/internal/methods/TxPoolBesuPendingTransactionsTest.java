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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.PendingTransactionsParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionPendingResult;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class TxPoolBesuPendingTransactionsTest {

  @Mock private PendingTransactions pendingTransactions;
  private TxPoolBesuPendingTransactions method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String TXPOOL_PENDING_TRANSACTIONS_METHOD = "txpool_besuPendingTransactions";

  @Before
  public void setUp() {
    final Set<PendingTransactions.TransactionInfo> listTrx = getPendingTransactions();
    method = new TxPoolBesuPendingTransactions(pendingTransactions);
    when(this.pendingTransactions.getTransactionInfo()).thenReturn(listTrx);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(TXPOOL_PENDING_TRANSACTIONS_METHOD);
  }

  @Test
  public void shouldReturnPendingTransactions() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {100}));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    final Set<TransactionPendingResult> result =
        (Set<TransactionPendingResult>) actualResponse.getResult();
    assertThat(result.size()).isEqualTo(4);
  }

  @Test
  public void shouldReturnPendingTransactionsWithLimit() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION, TXPOOL_PENDING_TRANSACTIONS_METHOD, new Object[] {1}));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);

    final Set<TransactionPendingResult> result =
        (Set<TransactionPendingResult>) actualResponse.getResult();
    assertThat(result.size()).isEqualTo(1);
  }

  @Test
  public void shouldReturnPendingTransactionsWithFilter() {

    final Map<String, String> fromFilter = new HashMap<>();
    fromFilter.put("eq", "0x0000000000000000000000000000000000000001");

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION,
                TXPOOL_PENDING_TRANSACTIONS_METHOD,
                new Object[] {
                  100,
                  new PendingTransactionsParams(
                      fromFilter,
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>())
                }));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);

    final Set<TransactionPendingResult> result =
        (Set<TransactionPendingResult>) actualResponse.getResult();
    assertThat(result.size()).isEqualTo(1);
  }

  @Test
  public void shouldReturnsErrorIfInvalidPredicate() {

    final Map<String, String> fromFilter = new HashMap<>();
    fromFilter.put("invalid", "0x0000000000000000000000000000000000000001");

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION,
                TXPOOL_PENDING_TRANSACTIONS_METHOD,
                new Object[] {
                  100,
                  new PendingTransactionsParams(
                      fromFilter,
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>())
                }));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Unknown field expected one of `eq`, `gt`, `lt`, `action`");
  }

  @Test
  public void shouldReturnsErrorIfInvalidNumberOfPredicate() {

    final Map<String, String> fromFilter = new HashMap<>();
    fromFilter.put("eq", "0x0000000000000000000000000000000000000001");
    fromFilter.put("lt", "0x0000000000000000000000000000000000000001");

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION,
                TXPOOL_PENDING_TRANSACTIONS_METHOD,
                new Object[] {
                  100,
                  new PendingTransactionsParams(
                      fromFilter,
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>())
                }));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Only one operator per filter type allowed");
  }

  @Test
  public void shouldReturnsErrorIfInvalidPredicateUsedForFromField() {

    final Map<String, String> fromFilter = new HashMap<>();
    fromFilter.put("lt", "0x0000000000000000000000000000000000000001");

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION,
                TXPOOL_PENDING_TRANSACTIONS_METHOD,
                new Object[] {
                  100,
                  new PendingTransactionsParams(
                      fromFilter,
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>())
                }));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("The `from` filter only supports the `eq` operator");
  }

  @Test
  public void shouldReturnsErrorIfInvalidPredicateUsedForToField() {

    final Map<String, String> toFilter = new HashMap<>();
    toFilter.put("lt", "0x0000000000000000000000000000000000000001");

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                JSON_RPC_VERSION,
                TXPOOL_PENDING_TRANSACTIONS_METHOD,
                new Object[] {
                  100,
                  new PendingTransactionsParams(
                      new HashMap<>(),
                      toFilter,
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>(),
                      new HashMap<>())
                }));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("The `to` filter only supports the `eq` or `action` operator");
  }

  private Set<PendingTransactions.TransactionInfo> getPendingTransactions() {
    final List<PendingTransactions.TransactionInfo> transactionInfoList = new ArrayList<>();
    for (int i = 1; i < 5; i++) {
      Transaction transaction = mock(Transaction.class);
      when(transaction.getGasPrice()).thenReturn(Wei.of(i));
      when(transaction.getValue()).thenReturn(Wei.of(i));
      when(transaction.getGasLimit()).thenReturn((long) i);
      when(transaction.getNonce()).thenReturn((long) i);
      when(transaction.getPayload()).thenReturn(Bytes.EMPTY);
      when(transaction.getV()).thenReturn(BigInteger.ONE);
      when(transaction.getR()).thenReturn(BigInteger.ONE);
      when(transaction.getS()).thenReturn(BigInteger.ONE);
      when(transaction.getSender()).thenReturn(Address.fromHexString(String.valueOf(i)));
      when(transaction.getTo())
          .thenReturn(Optional.of(Address.fromHexString(String.valueOf(i + 1))));
      when(transaction.getHash()).thenReturn(Hash.fromHexStringLenient(String.valueOf(i)));
      transactionInfoList.add(
          new PendingTransactions.TransactionInfo(
              transaction, true, Instant.ofEpochSecond(Integer.MAX_VALUE)));
    }
    return new LinkedHashSet<>(transactionInfoList);
  }
}
