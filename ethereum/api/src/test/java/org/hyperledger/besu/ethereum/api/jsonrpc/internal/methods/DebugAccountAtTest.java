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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableDebugAccountAtResult;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.Collections;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DebugAccountAtTest {
  private final BlockTracer blockTracer = Mockito.mock(BlockTracer.class);
  private final BlockchainQueries blockchainQueries = Mockito.mock(BlockchainQueries.class);

  @SuppressWarnings("unchecked")
  private final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
      Mockito.mock(BlockWithMetadata.class);

  private final TransactionWithMetadata transactionWithMetadata =
      Mockito.mock(TransactionWithMetadata.class);
  private final BlockTrace blockTrace = Mockito.mock(BlockTrace.class);
  private final TransactionTrace transactionTrace = Mockito.mock(TransactionTrace.class);
  private final DebugAccountAt debugAccountAt =
      new DebugAccountAt(blockchainQueries, () -> blockTracer);
  private final Transaction transaction = Mockito.mock(Transaction.class);

  @Test
  public void nameShouldBeDebugAccountAt() {
    Assertions.assertThat(debugAccountAt.getName()).isEqualTo("debug_accountAt");
  }

  @Test
  public void testBlockNotFoundResponse() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any())).thenReturn(Optional.empty());

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), 0, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));
    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.BLOCK_NOT_FOUND);
  }

  @Test
  public void testInvalidParamsResponseEmptyList() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockWithMetadata.getTransactions()).thenReturn(Collections.emptyList());

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), 0, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));
    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void testInvalidParamsResponseNegative() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockWithMetadata.getTransactions())
        .thenReturn(Collections.singletonList(transactionWithMetadata));

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), -1, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));
    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void testInvalidParamsResponseTooHigh() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockWithMetadata.getTransactions())
        .thenReturn(Collections.singletonList(transactionWithMetadata));

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), 2, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));
    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void testTransactionNotFoundResponse() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockWithMetadata.getTransactions())
        .thenReturn(Collections.singletonList(transactionWithMetadata));

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), 0, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));
    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.TRANSACTION_NOT_FOUND);
  }

  @Test
  public void testNoAccountFoundResponse() {
    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockWithMetadata.getTransactions())
        .thenReturn(Collections.singletonList(transactionWithMetadata));
    Mockito.when(blockTracer.trace(Mockito.any(Hash.class), Mockito.any()))
        .thenReturn(Optional.of(blockTrace));
    Mockito.when(blockTrace.getTransactionTraces())
        .thenReturn(Collections.singletonList(transactionTrace));
    Mockito.when(transactionTrace.getTransaction()).thenReturn(transaction);
    Mockito.when(transactionWithMetadata.getTransaction()).thenReturn(transaction);
    Mockito.when(transaction.getHash()).thenReturn(Hash.ZERO);

    final Object[] params = new Object[] {Hash.ZERO.toHexString(), 0, Address.ZERO.toHexString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_accountAt", params));

    final JsonRpcResponse response = debugAccountAt.response(request);

    Assertions.assertThat(response instanceof JsonRpcErrorResponse).isTrue();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualByComparingTo(JsonRpcError.NO_ACCOUNT_FOUND);
  }

  @Test
  public void testResult() {
    final Bytes code = Bytes.fromHexString(
        "0x608060405234801561001057600080fd5b506004361061002b5760003560e01c8063b27b880414610030575b");
    final String nonce = "0x1";
    final String balance = "0xffff";
    final String codeHash = "0xf5f334d41776ed2828fc910d488a05c57fe7c2352aab2d16e30539d7726e1562";
    ImmutableDebugAccountAtResult result =
        debugAccountAt.debugAccountAtResult(code, nonce, balance, codeHash);
    Assertions.assertThat(result.getBalance()).isEqualTo(balance);
    Assertions.assertThat(result.getNonce()).isEqualTo(nonce);
    Assertions.assertThat(result.getCode()).isEqualTo(code);
    Assertions.assertThat(result.getCodehash()).isEqualTo(codeHash);
  }
}
