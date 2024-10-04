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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_TRANSACTION_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EeaSendRawTransactionTest extends BaseEeaSendRawTransaction {

  // RLP encode fails creating a transaction without privateFrom so must be manually encoded
  private static final String PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM =
      "0xf88b800182520894095e7baea6a6c7c4c2dfeb977efac326af55"
          + "2d8780801ba048b55bfa915ac795c431978d8a6a992b628d55"
          + "7da5ff759b307d495a36649353a01fffd310ac743f371de3b9"
          + "f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804a00f200e"
          + "885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981ff"
          + "b33a0b8c8a72657374726963746564";

  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";
  final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  RestrictedOffchainEeaSendRawTransaction method;

  @BeforeEach
  public void before() {

    method =
        new RestrictedOffchainEeaSendRawTransaction(
            transactionPool,
            privacyIdProvider,
            privateMarkerTransactionFactory,
            address -> 0,
            privacyController);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid transaction parameter (index 0)");
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eea_sendRawTransaction", null));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid transaction parameter (index 0)");
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {null}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid transaction parameter (index 0)");
  }

  @Test
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {rawTransaction}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void invalidTransactionWithoutPrivateFromFieldFailsWithDecodeError() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verifyNoInteractions(privacyController);
  }

  @Test
  public void invalidTransactionIsNotSentToEnclaveAndIsNotAddedToTransactionPool() {
    when(privacyController.validatePrivateTransaction(any(), anyString()))
        .thenReturn(ValidationResult.invalid(PRIVATE_TRANSACTION_INVALID));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivateForTransactionRequest.getRequest().getId(),
            RpcErrorType.PRIVATE_TRANSACTION_INVALID);

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(privacyController, never()).createPrivateMarkerTransactionPayload(any(), any(), any());
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void invalidTransactionFailingWithMultiTenancyValidationErrorReturnsUnauthorizedError() {
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivateMarkerTransactionPayload(
            any(PrivateTransaction.class), any(), any()))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivateForTransactionRequest.getRequest().getId(), RpcErrorType.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void transactionWithNonceBelowAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_LOW, RpcErrorType.NONCE_TOO_LOW);
  }

  @Test
  public void transactionWithNonceAboveAccountNonceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_HIGH, RpcErrorType.NONCE_TOO_HIGH);
  }

  @Test
  public void transactionWithInvalidSignatureIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INVALID_SIGNATURE, RpcErrorType.INVALID_TRANSACTION_SIGNATURE);
  }

  @Test
  public void transactionWithIntrinsicGasExceedingGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
        RpcErrorType.PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT);
  }

  @Test
  public void transactionWithUpfrontGasExceedingAccountBalanceIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
        RpcErrorType.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);
  }

  @Test
  public void transactionWithGasLimitExceedingBlockGasLimitIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, RpcErrorType.EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void transactionWithNotWhitelistedSenderAccountIsRejected() {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, RpcErrorType.TX_SENDER_NOT_AUTHORIZED);
  }

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final RpcErrorType expectedError) {

    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(any(), anyString()))
        .thenReturn(ValidationResult.valid());
    when(transactionPool.addTransactionViaApi(any()))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivateForTransactionRequest.getRequest().getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eea_sendRawTransaction");
  }
}
