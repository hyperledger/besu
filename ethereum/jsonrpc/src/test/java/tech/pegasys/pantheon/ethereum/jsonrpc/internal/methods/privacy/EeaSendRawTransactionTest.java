/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.core.Wei;
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

  private static final String VALID_PRIVATE_TRANSACTION_RLP =
      "0xf90113800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "a0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          + "ffff801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d"
          + "495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab94"
          + "9f53faa07bd2c804ac41316156744d784c4355486d425648586f5a7a7a4267"
          + "5062572f776a3561784470573958386c393153476f3df85aac41316156744d"
          + "784c4355486d425648586f5a7a7a42675062572f776a356178447057395838"
          + "6c393153476f3dac4b6f32625671442b6e4e6c4e594c35454537793349644f"
          + "6e766966746a69697a706a52742b4854754642733d8a726573747269637465"
          + "64";

  private static final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(BytesValue.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.of(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129639935")),
          SECP256K1.Signature.create(
              new BigInteger(
                  "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
              new BigInteger(
                  "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
              Byte.valueOf("0")),
          BytesValue.fromHexString("0x"),
          Address.wrap(BytesValue.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          0);

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
  public void invalidTransactionRlpDecoding() {
    final String rawTransaction = "0x00";
    when(parameter.required(any(Object[].class), anyInt(), any())).thenReturn(rawTransaction);

    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "eea_sendRawTransaction", new String[] {rawTransaction});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void validTransactionIsSentToTransactionPool() throws IOException {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP);
    when(privateTxHandler.handle(any(PrivateTransaction.class))).thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getId(), "0xa86e8a2324e3abccd52afd6913c4c8a5d91f5d1855c0aa075568416c0a3ff7b2");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTxHandler).handle(any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void transactionWithNonceBelowAccountNonceIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.NONCE_TOO_LOW, JsonRpcError.NONCE_TOO_LOW);
  }

  @Test
  public void transactionWithNonceAboveAccountNonceIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INCORRECT_NONCE, JsonRpcError.INCORRECT_NONCE);
  }

  @Test
  public void transactionWithInvalidSignatureIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INVALID_SIGNATURE, JsonRpcError.INVALID_TRANSACTION_SIGNATURE);
  }

  @Test
  public void transactionWithIntrinsicGasExceedingGasLimitIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
        JsonRpcError.INTRINSIC_GAS_EXCEEDS_LIMIT);
  }

  @Test
  public void transactionWithUpfrontGasExceedingAccountBalanceIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
        JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);
  }

  @Test
  public void transactionWithGasLimitExceedingBlockGasLimitIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT, JsonRpcError.EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void transactionWithNotWhitelistedSenderAccountIsRejected() throws IOException {
    verifyErrorForInvalidTransaction(
        TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED, JsonRpcError.TX_SENDER_NOT_AUTHORIZED);
  }

  private void verifyErrorForInvalidTransaction(
      final TransactionInvalidReason transactionInvalidReason, final JsonRpcError expectedError)
      throws IOException {
    when(parameter.required(any(Object[].class), anyInt(), any()))
        .thenReturn(VALID_PRIVATE_TRANSACTION_RLP);
    when(privateTxHandler.handle(any(PrivateTransaction.class))).thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));

    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", "eea_sendRawTransaction", new String[] {VALID_PRIVATE_TRANSACTION_RLP});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privateTxHandler).handle(any(PrivateTransaction.class));
    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).matches("eea_sendRawTransaction");
  }
}
