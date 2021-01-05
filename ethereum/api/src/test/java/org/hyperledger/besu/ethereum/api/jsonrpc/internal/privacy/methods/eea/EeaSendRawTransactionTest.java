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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.PRIVATE_TRANSACTION_FAILED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EeaSendRawTransactionTest {

  static final String VALID_LEGACY_PRIVATE_TRANSACTION_RLP = validPrivateTransactionRlp();
  static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      validPrivateTransactionRlpPrivacyGroup();

  // RLP encode fails creating a transaction without privateFrom so must be manually encoded
  private static final String PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM =
      "0xf88b800182520894095e7baea6a6c7c4c2dfeb977efac326af55"
          + "2d8780801ba048b55bfa915ac795c431978d8a6a992b628d55"
          + "7da5ff759b307d495a36649353a01fffd310ac743f371de3b9"
          + "f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804a00f200e"
          + "885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981ff"
          + "b33a0b8c8a72657374726963746564";

  static final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.ZERO,
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.parseByte("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());
  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";

  final String MOCK_ORION_KEY = "";
  final User user = new JWTUser(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), "");
  final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Mock TransactionPool transactionPool;
  @Mock EeaSendRawTransaction method;
  @Mock PrivacyController privacyController;

  @Before
  public void before() {
    method =
        new EeaSendRawTransaction(transactionPool, privacyController, enclavePublicKeyProvider);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eea_sendRawTransaction", null));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {null}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {rawTransaction}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void validTransactionIsSentToTransactionPool() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class)))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final JsonRpcRequestContext request = getJsonRpcRequestContext();

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController)
        .sendTransaction(any(PrivateTransaction.class), eq(ENCLAVE_PUBLIC_KEY), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), eq(ENCLAVE_PUBLIC_KEY));
    verify(privacyController)
        .createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void validTransactionPrivacyGroupIsSentToTransactionPool() {
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.findOffChainPrivacyGroupByGroupId(any(String.class), any(String.class)))
        .thenReturn(
            Optional.of(
                new PrivacyGroup(
                    "", PrivacyGroup.Type.PANTHEON, "", "", singletonList(ENCLAVE_PUBLIC_KEY))));
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any(), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), anyString());
    verify(privacyController)
        .createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  private JsonRpcRequestContext getJsonRpcRequestContext() {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_LEGACY_PRIVATE_TRANSACTION_RLP}),
        user);
  }

  @Test
  public void transactionFailsIfPrivacyGroupDoesNotExist() {
    method =
        new EeaSendRawTransaction(transactionPool, privacyController, enclavePublicKeyProvider);

    when(privacyController.findOffChainPrivacyGroupByGroupId(any(String.class), any(String.class)))
        .thenThrow(
            new RuntimeException(JsonRpcError.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST.getMessage()));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "eea_sendRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
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
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyNoInteractions(privacyController);
  }

  @Test
  public void invalidTransactionIsNotSentToEnclaveAndIsNotAddedToTransactionPool() {
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.invalid(PRIVATE_TRANSACTION_FAILED));

    final JsonRpcRequestContext request = getJsonRpcRequestContext();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController, never()).sendTransaction(any(), any(), any());
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void invalidTransactionFailingWithMultiTenancyValidationErrorReturnsUnauthorizedError() {
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any(), any()))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final JsonRpcRequestContext request = getJsonRpcRequestContext();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyNoInteractions(transactionPool);
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
        JsonRpcError.PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT);
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

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final JsonRpcError expectedError) {

    when(privacyController.sendTransaction(any(PrivateTransaction.class), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));
    final JsonRpcRequestContext request = getJsonRpcRequestContext();

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any(), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), anyString());
    verify(privacyController)
        .createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eea_sendRawTransaction");
  }

  private static String validPrivateTransactionRlp() {
    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privateFor(
                List.of(
                    Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="),
                    Bytes.fromBase64String("QTFhVnRNeExDVUhtQlZIWG9aenpCZ1BiVy93ajVheER=")))
            .restriction(Restriction.RESTRICTED);
    return rlpEncodeTransaction(privateTransactionBuilder);
  }

  private static String validPrivateTransactionRlpPrivacyGroup() {
    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privacyGroupId(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="))
            .restriction(Restriction.RESTRICTED);
    return rlpEncodeTransaction(privateTransactionBuilder);
  }

  private static String rlpEncodeTransaction(
      final PrivateTransaction.Builder privateTransactionBuilder) {
    final SECP256K1.KeyPair keyPair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

    final PrivateTransaction privateTransaction = privateTransactionBuilder.signAndBuild(keyPair);
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    return bvrlp.encoded().toHexString();
  }
}
