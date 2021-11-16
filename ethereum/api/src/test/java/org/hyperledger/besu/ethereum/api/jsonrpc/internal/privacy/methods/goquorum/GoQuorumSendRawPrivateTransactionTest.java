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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.goquorum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.GoQuorumSendRawPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.GoQuorumSendRawTxArgs;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.Restriction;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoQuorumSendRawPrivateTransactionTest {

  static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  static final String INVALID_TRANSACTION_RLP = invalidGQPrivateTransactionRlp();

  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";
  final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Mock GoQuorumSendRawPrivateTransaction method;
  @Mock TransactionPool transactionPool;
  @Mock GoQuorumEnclave enclave;
  private static final String RAW_TRANSACTION_STRING = "someString";
  private static final String RAW_TRANSACTION = createValidTransactionRLP();
  private static final GoQuorumSendRawTxArgs GO_QUORUM_SEND_RAW_TX_ARGS =
      new GoQuorumSendRawTxArgs(
          ENCLAVE_PUBLIC_KEY, Collections.singletonList(ENCLAVE_PUBLIC_KEY), 0);

  @Before
  public void before() {
    boolean goQuorumCompatibilityMode = true;
    method =
        new GoQuorumSendRawPrivateTransaction(
            enclave, transactionPool, privacyIdProvider, goQuorumCompatibilityMode);
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_sendRawPrivateTransaction", new String[] {}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");

    final JsonRpcRequestContext request2 =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "eth_sendRawPrivateTransaction", new String[] {RAW_TRANSACTION_STRING}));

    assertThatThrownBy(() -> method.response(request2))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 1");
  }

  @Test
  public void requestHasNullObjectParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_sendRawPrivateTransaction", null));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "\"eth_sendRawPrivateTransaction\"", new String[] {null}));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void validTransactionIsSentToTransactionPool() {
    when(enclave.sendSignedTransaction(any(), any())).thenReturn(null);

    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "\"eth_sendRawPrivateTransaction\"",
                new Object[] {RAW_TRANSACTION, GO_QUORUM_SEND_RAW_TX_ARGS}));

    method.response(request);

    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  @Test
  public void invalidTransactionIsNotAddedToTransactionPool() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "\"eth_sendRawPrivateTransaction\"",
                new Object[] {INVALID_TRANSACTION_RLP, GO_QUORUM_SEND_RAW_TX_ARGS}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.DECODE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
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

    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.invalid(transactionInvalidReason));
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "\"eth_sendRawPrivateTransaction\"",
                new Object[] {RAW_TRANSACTION, GO_QUORUM_SEND_RAW_TX_ARGS}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), expectedError);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(transactionPool).addLocalTransaction(any(Transaction.class));
  }

  private static String invalidGQPrivateTransactionRlp() {
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

  private static String rlpEncodeTransaction(
      final PrivateTransaction.Builder privateTransactionBuilder) {
    final KeyPair keyPair =
        SIGNATURE_ALGORITHM
            .get()
            .createKeyPair(
                SIGNATURE_ALGORITHM
                    .get()
                    .createPrivateKey(
                        new BigInteger(
                            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
                            16)));

    final PrivateTransaction privateTransaction = privateTransactionBuilder.signAndBuild(keyPair);
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    return bvrlp.encoded().toHexString();
  }

  private static final String createValidTransactionRLP() {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    final Transaction publicTransaction =
        new Transaction(
            0L,
            Wei.of(1),
            21000L,
            Optional.of(
                Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
            Wei.ZERO,
            SIGNATURE_ALGORITHM
                .get()
                .createSignature(
                    new BigInteger(
                        "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
                    new BigInteger(
                        "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
                    Byte.parseByte("0")),
            Bytes.fromHexString("0x01"), // this is the enclave key for the private payload
            Address.wrap(
                Bytes.fromHexString(
                    "0x8411b12666f68ef74cace3615c9d5a377729d03f")), // sender public address
            Optional.empty(),
            Optional.of(BigInteger.valueOf(37)));
    publicTransaction.writeTo(bvrlp);
    return bvrlp.encoded().toHexString();
  }
}
