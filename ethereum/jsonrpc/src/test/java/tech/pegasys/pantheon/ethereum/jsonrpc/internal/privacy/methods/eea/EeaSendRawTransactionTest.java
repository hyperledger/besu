/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EeaSendRawTransactionTest {

  private static final String VALUE_NON_ZERO_TRANSACTION_RLP =
      "0xf88b808203e8832dc6c0808203e880820fe8a08b89005561f31ce861"
          + "84949bf32087a9817b337ab1d6027d58ef4e48aea88bafa041b93a"
          + "e41a99fe662ad7fc1406ac90bf6bd498b5fe56fd6bfea15de15714"
          + "438eac41316156744d784c4355486d425648586f5a7a7a42675062"
          + "572f776a3561784470573958386c393153476f3dc08a7265737472"
          + "6963746564";

  private static final String VALID_PRIVATE_TRANSACTION_RLP =
      "0xf8f3800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "808025a048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804ac41316156744d784c"
          + "4355486d425648586f5a7a7a42675062572f776a356178447057"
          + "3958386c393153476f3df85aac41316156744d784c4355486d42"
          + "5648586f5a7a7a42675062572f776a3561784470573958386c39"
          + "3153476f3dac4b6f32625671442b6e4e6c4e594c354545377933"
          + "49644f6e766966746a69697a706a52742b4854754642733d8a72"
          + "657374726963746564";

  private static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      "0xf8ac800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "80801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804a0035695b4cc4b0941"
          + "e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486aa00f"
          + "200e885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981"
          + "ffb33a0b8c8a72657374726963746564";

  private static final String PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM =
      "0xf88b800182520894095e7baea6a6c7c4c2dfeb977efac326af55"
          + "2d8780801ba048b55bfa915ac795c431978d8a6a992b628d55"
          + "7da5ff759b307d495a36649353a01fffd310ac743f371de3b9"
          + "f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804a00f200e"
          + "885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981ff"
          + "b33a0b8c8a72657374726963746564";

  private static final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(BytesValue.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.ZERO,
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.valueOf("0")),
          BytesValue.fromHexString("0x"),
          Address.wrap(BytesValue.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());

  final String MOCK_ORION_KEY = "";
  final String MOCK_PRIVACY_GROUP = "";

  @Mock private TransactionPool transactionPool;

  @Mock private JsonRpcParameter parameter;

  @Mock private EeaSendRawTransaction method;

  @Mock private PrivateTransactionHandler privateTxHandler;

  @Before
  public void before() {
    method = new EeaSendRawTransaction(privateTxHandler, transactionPool, parameter);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "eea_sendRawTransaction", null);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {null});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";
    when(parameter.required(any(Object[].class), anyInt(), any())).thenReturn(rawTransaction);

    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {rawTransaction});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void valueNonZeroTransaction() {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALUE_NON_ZERO_TRANSACTION_RLP);

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALUE_NON_ZERO_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.VALUE_NOT_ZERO);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void validTransactionIsSentToTransactionPool() throws Exception {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP);
    when(privateTxHandler.sendToOrion(any(PrivateTransaction.class))).thenReturn(MOCK_ORION_KEY);
    when(privateTxHandler.getPrivacyGroup(any(String.class), any(PrivateTransaction.class)))
        .thenReturn(MOCK_PRIVACY_GROUP);
    when(privateTxHandler.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class)))
        .thenReturn(ValidationResult.valid());
    when(privateTxHandler.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getId(), "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTxHandler).sendToOrion(any(PrivateTransaction.class));
    verify(privateTxHandler).getPrivacyGroup(any(String.class), any(PrivateTransaction.class));
    verify(privateTxHandler)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class));
    verify(privateTxHandler)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void validTransactionPrivacyGroupIsSentToTransactionPool() throws Exception {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP);
    when(privateTxHandler.sendToOrion(any(PrivateTransaction.class))).thenReturn(MOCK_ORION_KEY);
    when(privateTxHandler.getPrivacyGroup(any(String.class), any(PrivateTransaction.class)))
        .thenReturn(MOCK_PRIVACY_GROUP);
    when(privateTxHandler.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class)))
        .thenReturn(ValidationResult.valid());
    when(privateTxHandler.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0",
            "eea_sendRawTransaction",
            new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getId(), "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTxHandler).sendToOrion(any(PrivateTransaction.class));
    verify(privateTxHandler).getPrivacyGroup(any(String.class), any(PrivateTransaction.class));
    verify(privateTxHandler)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class));
    verify(privateTxHandler)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void transactionPrivacyGroupNoPrivateFromReturnsError() throws Exception {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM);

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0",
            "eea_sendRawTransaction",
            new String[] {PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP_NO_PRIVATE_FROM});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verifyZeroInteractions(privateTxHandler);
  }

  @Test
  public void invalidTransactionIsSentToTransactionPool() throws Exception {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP);
    when(privateTxHandler.sendToOrion(any(PrivateTransaction.class)))
        .thenThrow(new IOException("enclave failed to execute"));

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void transactionWithNonceBelowAccountNonceIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW);
  }

  @Test
  public void transactionWithNonceAboveAccountNonceIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INCORRECT_NONCE, JsonRpcError.INCORRECT_NONCE);
  }

  @Test
  public void transactionWithInvalidSignatureIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INVALID_SIGNATURE, JsonRpcError.INVALID_TRANSACTION_SIGNATURE);
  }

  @Test
  public void transactionWithIntrinsicGasExceedingGasLimitIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
        JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);
  }

  @Test
  public void transactionWithUpfrontGasExceedingAccountBalanceIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
        JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);
  }

  @Test
  public void transactionWithGasLimitExceedingBlockGasLimitIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void transactionWithNotWhitelistedSenderAccountIsRejected() throws Exception {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, JsonRpcError.TX_SENDER_NOT_AUTHORIZED);
  }

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final JsonRpcError expectedError)
      throws Exception {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP);
    when(privateTxHandler.sendToOrion(any(PrivateTransaction.class))).thenReturn(MOCK_ORION_KEY);
    when(privateTxHandler.getPrivacyGroup(any(String.class), any(PrivateTransaction.class)))
        .thenReturn(MOCK_PRIVACY_GROUP);
    when(privateTxHandler.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class)))
        .thenReturn(ValidationResult.valid());
    when(privateTxHandler.createPrivacyMarkerTransaction(
            any(String.class), any(PrivateTransaction.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));
    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTxHandler).sendToOrion(any(PrivateTransaction.class));
    verify(privateTxHandler).getPrivacyGroup(any(String.class), any(PrivateTransaction.class));
    verify(privateTxHandler)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class));
    verify(privateTxHandler)
        .createPrivacyMarkerTransaction(any(String.class), any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eea_sendRawTransaction");
  }
}
