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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_BASE64_ENCLAVE_KEY;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateMarkerTransaction;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionBesu;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privateTransactionLegacy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionReceiptResult;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.Collections;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivGetTransactionReceiptTest {

  final Bytes internalPrivacyGroupId = Bytes.random(32);

  @Mock private PrivateStateStorage privateStateStorage;
  @Mock private PrivacyController privacyController;

  private PrivGetTransactionReceipt privGetTransactionReceipt;

  private final User user =
      new UserImpl(
          new JsonObject().put("privacyPublicKey", VALID_BASE64_ENCLAVE_KEY.toBase64String()),
          new JsonObject());
  private final PrivacyIdProvider privacyIdProvider =
      (user) -> VALID_BASE64_ENCLAVE_KEY.toBase64String();

  @Before
  public void setUp() {
    final PrivateTransactionReceipt receipt =
        new PrivateTransactionReceipt(1, Collections.emptyList(), Bytes.EMPTY, Optional.empty());
    when(privateStateStorage.getTransactionReceipt(any(Bytes32.class), any(Bytes32.class)))
        .thenReturn(Optional.of(receipt));

    privGetTransactionReceipt =
        new PrivGetTransactionReceipt(privateStateStorage, privacyController, privacyIdProvider);
  }

  @Test
  public void returnReceiptIfLegacyTransactionExists() {
    final Transaction pmt = privateMarkerTransaction();
    final PrivateTransaction legacyPrivateTransaction = privateTransactionLegacy();
    final ExecutedPrivateTransaction executedPrivateTransaction =
        createExecutedPrivateTransaction(pmt, legacyPrivateTransaction);

    when(privacyController.findPrivateTransactionByPmtHash(eq(pmt.getHash()), any()))
        .thenReturn(Optional.of(executedPrivateTransaction));

    final PrivateTransactionReceiptResult expectedResult =
        privateTransactionReceiptResultFor(legacyPrivateTransaction, pmt);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(requestContext(pmt.getHash()));
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
  }

  @Test
  public void returnReceiptIfBesuTransactionExists() {
    final Transaction pmt = privateMarkerTransaction();
    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final ExecutedPrivateTransaction executedPrivateTransaction =
        createExecutedPrivateTransaction(pmt, privateTransaction);

    when(privacyController.findPrivateTransactionByPmtHash(eq(pmt.getHash()), any()))
        .thenReturn(Optional.of(executedPrivateTransaction));

    final PrivateTransactionReceiptResult expectedResult =
        privateTransactionReceiptResultFor(privateTransaction, pmt);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(requestContext(pmt.getHash()));
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
  }

  @Test
  public void markerTransactionNotAvailableResultsInNullResponse() {
    final Hash pmtHash = Hash.hash(Bytes.random(32));
    when(privacyController.findPrivateTransactionByPmtHash(eq(pmtHash), any()))
        .thenReturn(Optional.empty());

    final JsonRpcRequestContext request = requestContext(pmtHash);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);

    assertThat(response.getResult()).isNull();
  }

  @Test
  public void enclaveConnectionIssueThrowsRuntimeException() {
    final Hash pmtHash = Hash.hash(Bytes.random(32));
    when(privacyController.findPrivateTransactionByPmtHash(eq(pmtHash), any()))
        .thenThrow(EnclaveServerException.class);

    final JsonRpcRequestContext request = requestContext(pmtHash);

    final Throwable t = catchThrowable(() -> privGetTransactionReceipt.response(request));
    assertThat(t).isInstanceOf(RuntimeException.class);
  }

  @Test
  public void transactionReceiptContainsRevertReasonWhenInvalidTransactionOccurs() {
    final Transaction pmt = privateMarkerTransaction();
    final PrivateTransaction privateTransaction = privateTransactionBesu();
    final ExecutedPrivateTransaction executedPrivateTransaction =
        createExecutedPrivateTransaction(pmt, privateTransaction);
    when(privacyController.findPrivateTransactionByPmtHash(eq(pmt.getHash()), any()))
        .thenReturn(Optional.of(executedPrivateTransaction));

    final PrivateTransactionReceipt privateTransactionReceiptWithRevertReason =
        new PrivateTransactionReceipt(
            1,
            Collections.emptyList(),
            Bytes.EMPTY,
            Optional.of(Bytes.wrap(new byte[] {(byte) 0x01})));
    when(privateStateStorage.getTransactionReceipt(any(Bytes32.class), any(Bytes32.class)))
        .thenReturn(Optional.of(privateTransactionReceiptWithRevertReason));

    final JsonRpcRequestContext request = requestContext(pmt.getHash());

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result.getRevertReason()).isEqualTo("0x01");
  }

  @Test
  public void enclaveKeysCannotDecryptPayloadThrowsRuntimeException() {
    final Hash pmtHash = Hash.hash(Bytes.random(32));
    final String keysCannotDecryptPayloadMsg = "EnclaveKeysCannotDecryptPayload";
    when(privacyController.findPrivateTransactionByPmtHash(eq(pmtHash), any()))
        .thenThrow(new EnclaveClientException(400, keysCannotDecryptPayloadMsg));

    final JsonRpcRequestContext request = requestContext(pmtHash);
    final Throwable t = catchThrowable(() -> privGetTransactionReceipt.response(request));

    assertThat(t).isInstanceOf(EnclaveClientException.class);
    assertThat(t.getMessage()).isEqualTo(keysCannotDecryptPayloadMsg);
  }

  private PrivateTransactionReceiptResult privateTransactionReceiptResultFor(
      final PrivateTransaction privateTransaction, final Transaction markerTransaction) {
    final String contractAddress =
        Address.privateContractAddress(
                privateTransaction.getSender(),
                privateTransaction.getNonce(),
                privateTransaction.getPrivacyGroupId().orElse(internalPrivacyGroupId))
            .toHexString();

    return new PrivateTransactionReceiptResult(
        contractAddress,
        privateTransaction.getSender().toHexString(),
        null,
        Collections.emptyList(),
        Bytes.EMPTY,
        Hash.EMPTY,
        0,
        0,
        markerTransaction.getHash(),
        privateTransaction.getPrivateFrom(),
        privateTransaction.getPrivateFor().isPresent()
            ? privateTransaction.getPrivateFor().get()
            : null,
        privateTransaction.getPrivacyGroupId().isPresent()
            ? privateTransaction.getPrivacyGroupId().get()
            : null,
        null,
        Quantity.create(Bytes.of(1).toUnsignedBigInteger()));
  }

  private JsonRpcRequestContext requestContext(final Hash pmtHash) {
    final Object[] params = new Object[] {pmtHash};
    return new JsonRpcRequestContext(
        new JsonRpcRequest("1", "priv_getTransactionReceipt", params), user);
  }

  private ExecutedPrivateTransaction createExecutedPrivateTransaction(
      final Transaction pmt, final PrivateTransaction privateTx) {
    return new ExecutedPrivateTransaction(
        Hash.EMPTY, 0L, pmt.getHash(), 0, internalPrivacyGroupId.toBase64String(), privateTx);
  }
}
