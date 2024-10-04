/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionCompleteResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionPendingResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthGetTransactionByHashTest {

  private static final String VALID_TRANSACTION =
      "0xf86d0485174876e800830222e0945aae326516b4f8fe08074b7e972e40a713048d62880de0b6b3a7640000801ba05d4e7998757264daab67df2ce6f7e7a0ae36910778a406ca73898c9899a32b9ea0674700d5c3d1d27f2e6b4469957dfd1a1c49bf92383d80717afc84eb05695d5b";

  @Mock private BlockchainQueries blockchainQueries;
  private EthGetTransactionByHash method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_getTransactionByHash";

  @Mock private TransactionPool transactionPool;

  @BeforeEach
  public void setUp() {
    method = new EthGetTransactionByHash(blockchainQueries, transactionPool);
  }

  @Test
  void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  void shouldReturnErrorResponseIfMissingRequiredParameter() {
    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, method.getName(), new Object[] {});
    final JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), RpcErrorType.INVALID_PARAM_COUNT);

    final JsonRpcResponse actualResponse = method.response(context);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void shouldReturnNullResultWhenTransactionDoesNotExist() {
    final String transactionHash =
        "0xf9ef5f0cf02685711cdf687b72d4754901729b942f4ea7f956e7fb206cae2f9e";
    when(transactionPool.getTransactionByHash(Hash.fromHexString(transactionHash)))
        .thenReturn(Optional.empty());
    when(blockchainQueries.transactionByHash(Hash.fromHexString(transactionHash)))
        .thenReturn(Optional.empty());

    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, method.getName(), new Object[] {transactionHash});
    final JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), null);

    final JsonRpcResponse actualResponse = method.response(context);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void shouldReturnPendingTransactionWhenTransactionExistsAndIsPending() {
    final org.hyperledger.besu.ethereum.core.Transaction transaction =
        org.hyperledger.besu.ethereum.core.Transaction.readFrom(
            Bytes.fromHexString(VALID_TRANSACTION));

    when(transactionPool.getTransactionByHash(transaction.getHash()))
        .thenReturn(Optional.of(transaction));
    verifyNoInteractions(blockchainQueries);

    final JsonRpcRequest request =
        new JsonRpcRequest(
            JSON_RPC_VERSION, method.getName(), new Object[] {transaction.getHash().toHexString()});
    final JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), new TransactionPendingResult(transaction));

    final JsonRpcResponse actualResponse = method.response(context);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void shouldReturnCompleteTransactionWhenTransactionExistsInBlockchain() {
    final org.hyperledger.besu.ethereum.core.Transaction transaction =
        org.hyperledger.besu.ethereum.core.Transaction.readFrom(
            Bytes.fromHexString(VALID_TRANSACTION));
    final TransactionWithMetadata transactionWithMetadata =
        new TransactionWithMetadata(transaction, 1, Optional.empty(), Hash.ZERO, 0);

    when(transactionPool.getTransactionByHash(transaction.getHash())).thenReturn(Optional.empty());
    verifyNoMoreInteractions(transactionPool);
    when(blockchainQueries.transactionByHash(transaction.getHash()))
        .thenReturn(Optional.of(transactionWithMetadata));

    final JsonRpcRequest request =
        new JsonRpcRequest(
            JSON_RPC_VERSION, method.getName(), new Object[] {transaction.getHash().toHexString()});
    final JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getId(), new TransactionCompleteResult(transactionWithMetadata));

    final JsonRpcResponse actualResponse = method.response(context);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void validateResultSpec() {

    PendingTransaction pendingTx = getTransactionPool().stream().findFirst().get();
    Hash hash = pendingTx.getHash();
    when(this.transactionPool.getTransactionByHash(hash))
        .thenReturn(Optional.of(pendingTx.getTransaction()));
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, new Object[] {hash}));

    final JsonRpcSuccessResponse actualResponse = (JsonRpcSuccessResponse) method.response(request);
    TransactionPendingResult result = (TransactionPendingResult) actualResponse.getResult();

    assertThat(result.getBlockHash()).isNull();
    assertThat(result.getBlockNumber()).isNull();
    assertThat(result.getTransactionIndex()).isNull();

    assertThat(result.getFrom()).isNotNull();
    assertThat(result.getGas()).isNotNull();
    assertThat(result.getGasPrice()).isNotNull();
    assertThat(result.getHash()).isNotNull();
    assertThat(result.getInput()).isNotNull();
    assertThat(result.getNonce()).isNotNull();
    assertThat(result.getPublicKey()).isNotNull();
    assertThat(result.getRaw()).isNotNull();
    assertThat(result.getTo()).isNotNull();
    assertThat(result.getValue()).isNotNull();
    switch (result.getType()) {
      case "0x0":
        assertThat(result.getYParity()).isNull();
        assertThat(result.getV()).isNotNull();
        break;
      case "0x1":
      case "0x2":
        assertThat(result.getYParity()).isNotNull();
        assertThat(result.getV()).isNotNull();
        break;
      case "0x3":
        assertThat(result.getYParity()).isNotNull();
        assertThat(result.getV()).isNull();
        break;
      default:
        fail("unknownType " + result.getType());
    }
    assertThat(result.getR()).isNotNull();
    assertThat(result.getS()).isNotNull();
  }

  private Set<PendingTransaction> getTransactionPool() {

    final BlockDataGenerator gen = new BlockDataGenerator();
    Transaction pendingTransaction = gen.transaction();
    System.out.println(pendingTransaction.getHash());
    return gen.transactionsWithAllTypes(4).stream()
        .map(PendingTransaction.Local::new)
        .collect(Collectors.toUnmodifiableSet());
  }
}
