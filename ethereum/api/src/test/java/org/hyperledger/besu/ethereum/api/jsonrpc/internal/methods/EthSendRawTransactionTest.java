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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthSendRawTransactionTest {

  private static final String VALID_TRANSACTION =
      "0xf86d0485174876e800830222e0945aae326516b4f8fe08074b7e972e40a713048d62880de0b6b3a7640000801ba05d4e7998757264daab67df2ce6f7e7a0ae36910778a406ca73898c9899a32b9ea0674700d5c3d1d27f2e6b4469957dfd1a1c49bf92383d80717afc84eb05695d5b";
  @Mock private TransactionPool transactionPool;
  private EthSendRawTransaction method;

  @Before
  public void before() {
    method = new EthSendRawTransaction(transactionPool);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawTransaction", new String[] {}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_sendRawTransaction", null));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawTransaction", new String[] {null}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawTransaction", new String[] {rawTransaction}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void validTransactionIsSentToTransactionPool() {
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawTransaction", new String[] {VALID_TRANSACTION}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0xbaabcc1bd699e7378451e4ce5969edb9bdcae76cb79bdacae793525c31e423c7");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void transactionWithNonceBelowAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW);
  }

  @Test
  public void transactionWithNonceAboveAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INCORRECT_NONCE, JsonRpcError.INCORRECT_NONCE);
  }

  @Test
  public void transactionWithInvalidSignatureIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INVALID_SIGNATURE, JsonRpcError.INVALID_TRANSACTION_SIGNATURE);
  }

  @Test
  public void transactionWithIntrinsicGasExceedingGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
        JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);
  }

  @Test
  public void transactionWithUpfrontGasExceedingAccountBalanceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
        JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);
  }

  @Test
  public void transactionWithGasLimitExceedingBlockGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void transactionWithNotWhitelistedSenderAccountIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, JsonRpcError.TX_SENDER_NOT_AUTHORIZED);
  }

  @Test
  public void transactionWithFeeCapExceededIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_FEECAP_EXCEEDED, JsonRpcError.TX_FEECAP_EXCEEDED);
  }

  @Test
  public void transactionWithNonZeroGasWithGoQuorumCompatibilityIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.GAS_PRICE_MUST_BE_ZERO, JsonRpcError.GAS_PRICE_MUST_BE_ZERO);
  }

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final JsonRpcError expectedError) {
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawTransaction", new String[] {VALID_TRANSACTION}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eth_sendRawTransaction");
  }
}
