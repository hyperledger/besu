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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;

import java.util.OptionalLong;

import org.junit.Test;

public class EthGetTransactionCountTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final PendingTransactions pendingTransactions = mock(PendingTransactions.class);

  private final EthGetTransactionCount ethGetTransactionCount =
      new EthGetTransactionCount(blockchain, pendingTransactions, parameters);
  private final String pendingTransactionString = "0x00000000000000000000000000000000000000AA";
  private final Object[] pendingParams = new Object[] {pendingTransactionString, "pending"};

  @Test
  public void shouldUsePendingTransactionsWhenToldTo() {
    when(pendingTransactions.getNextNonceForSender(Address.fromHexString(pendingTransactionString)))
        .thenReturn(OptionalLong.of(12));
    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eth_getTransactionCount", pendingParams);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionCount.response(request);
    assertThat(response.getResult()).isEqualTo("0xc");
  }

  @Test
  public void shouldUseLatestTransactionsWhenNoPendingTransactions() {
    final Address address = Address.fromHexString(pendingTransactionString);
    when(pendingTransactions.getNextNonceForSender(address)).thenReturn(OptionalLong.empty());
    when(blockchain.headBlockNumber()).thenReturn(1L);
    when(blockchain.getTransactionCount(address, 1L)).thenReturn(7L);
    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eth_getTransactionCount", pendingParams);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) ethGetTransactionCount.response(request);
    assertThat(response.getResult()).isEqualTo("0x7");
  }
}
