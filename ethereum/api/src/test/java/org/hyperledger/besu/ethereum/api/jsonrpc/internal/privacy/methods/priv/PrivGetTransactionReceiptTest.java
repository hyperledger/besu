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
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_CONTRACT_DEPLOYMENT_ADDRESS;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.generateReceiveResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.Collections;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivGetTransactionReceiptTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final PrivateTransaction privateContractDeploymentTransactionLegacy =
      PrivateTransactionDataFixture.privateContractDeploymentTransactionLegacy();
  private final PrivateTransaction privateContractDeploymentTransactionBesu =
      PrivateTransactionDataFixture.privateContractDeploymentTransactionBesu();
  private final Transaction markerTransaction =
      PrivateTransactionDataFixture.privacyMarkerTransaction();

  private final User user =
      new JWTUser(
          new JsonObject().put("privacyPublicKey", VALID_BASE64_ENCLAVE_KEY.toBase64String()), "");
  private final EnclavePublicKeyProvider enclavePublicKeyProvider =
      (user) -> VALID_BASE64_ENCLAVE_KEY.toBase64String();

  private final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
  private final Blockchain blockchain = mock(Blockchain.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);

  @Before
  public void setUp() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    final TransactionLocation transactionLocation = new TransactionLocation(Hash.EMPTY, 0);
    when(blockchain.getTransactionLocation(nullable(Hash.class)))
        .thenReturn(Optional.of(transactionLocation));
    final BlockBody blockBody =
        new BlockBody(Collections.singletonList(markerTransaction), Collections.emptyList());
    when(blockchain.getBlockBody(any(Hash.class))).thenReturn(Optional.of(blockBody));
    final BlockHeader mockBlockHeader = mock(BlockHeader.class);
    when(blockchain.getBlockHeader(any(Hash.class))).thenReturn(Optional.of(mockBlockHeader));
    when(mockBlockHeader.getNumber()).thenReturn(0L);

    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.getPrivateStateStorage()).thenReturn(privateStateStorage);
    final PrivateTransactionReceipt receipt =
        new PrivateTransactionReceipt(1, Collections.emptyList(), Bytes.EMPTY, Optional.empty());
    when(privateStateStorage.getTransactionReceipt(any(Bytes32.class), any(Bytes32.class)))
        .thenReturn(Optional.of(receipt));
  }

  @Test
  public void returnReceiptIfTransactionExists() {
    final PrivateTransactionReceiptResult expectedResult =
        privateTransactionReceiptResultFor(
            privateContractDeploymentTransactionLegacy, markerTransaction);

    when(privacyController.retrieveTransaction(anyString(), anyString()))
        .thenReturn(
            PrivateTransactionDataFixture.generateReceiveResponse(
                privateContractDeploymentTransactionLegacy));

    executeAndVerifyCall(expectedResult, 2);
  }

  @Test
  public void returnReceiptForVersionedOnChainTransaction() {
    final PrivateTransactionReceiptResult expectedResult =
        privateTransactionReceiptResultFor(
            privateContractDeploymentTransactionBesu, markerTransaction);

    when(privacyController.retrieveTransaction(anyString(), anyString()))
        .thenReturn(
            PrivateTransactionDataFixture.generateVersionedReceiveResponse(
                privateContractDeploymentTransactionBesu));

    executeAndVerifyCall(expectedResult, 1);
  }

  @Test
  public void returnReceiptIfTransactionExistsInBlob() {
    final PrivateTransactionReceiptResult expectedResult =
        privateTransactionReceiptResultFor(
            privateContractDeploymentTransactionBesu, markerTransaction);

    when(privacyController.retrieveTransaction(anyString(), anyString()))
        .thenThrow(new EnclaveClientException(0, "EnclavePayloadNotFound"));
    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any(Bytes32.class)))
        .thenReturn(
            Optional.of(
                new PrivacyGroupHeadBlockMap(Collections.singletonMap(Bytes32.ZERO, Hash.ZERO))));
    when(privateStateStorage.getAddDataKey(any(Bytes32.class)))
        .thenReturn(Optional.of(Bytes32.ZERO));
    when(privacyController.retrieveAddBlob(anyString()))
        .thenReturn(
            PrivateTransactionDataFixture.generateAddBlobResponse(
                privateContractDeploymentTransactionBesu, markerTransaction));

    executeAndVerifyCall(expectedResult, 1);
  }

  @Test
  public void enclavePayloadNotFoundResultsInSuccessButNullResponse() {
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenThrow(new EnclaveClientException(404, "EnclavePayloadNotFound"));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result).isNull();
  }

  @Test
  public void markerTransactionNotAvailableResultsInNullResponse() {
    when(blockchain.getTransactionLocation(nullable(Hash.class))).thenReturn(Optional.empty());

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result).isNull();
  }

  @Test
  public void enclaveConnectionIssueThrowsRuntimeException() {
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenThrow(EnclaveServerException.class);

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);
    final Throwable t = catchThrowable(() -> privGetTransactionReceipt.response(request));
    assertThat(t).isInstanceOf(RuntimeException.class);
  }

  @Test
  public void transactionReceiptContainsRevertReasonWhenInvalidTransactionOccurs() {
    when(privacyController.retrieveTransaction(anyString(), anyString()))
        .thenReturn(generateReceiveResponse(privateContractDeploymentTransactionLegacy));
    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            1,
            Collections.emptyList(),
            Bytes.EMPTY,
            Optional.of(Bytes.wrap(new byte[] {(byte) 0x01})));
    when(privateStateStorage.getTransactionReceipt(any(Bytes32.class), any(Bytes32.class)))
        .thenReturn(Optional.of(privateTransactionReceipt));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result.getRevertReason()).isEqualTo("0x01");
  }

  @Test
  public void enclaveKeysCannotDecryptPayloadThrowsRuntimeException() {
    final String keysCannotDecryptPayloadMsg = "EnclaveKeysCannotDecryptPayload";
    when(privacyController.retrieveTransaction(any(), any()))
        .thenThrow(new EnclaveClientException(400, keysCannotDecryptPayloadMsg));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);
    final Throwable t = catchThrowable(() -> privGetTransactionReceipt.response(request));

    assertThat(t).isInstanceOf(EnclaveClientException.class);
    assertThat(t.getMessage()).isEqualTo(keysCannotDecryptPayloadMsg);
  }

  private PrivateTransactionReceiptResult privateTransactionReceiptResultFor(
      final PrivateTransaction privateTransaction, final Transaction markerTransaction) {
    return new PrivateTransactionReceiptResult(
        privateTransaction.getPrivacyGroupId().isPresent()
            ? Address.privateContractAddress(
                    privateTransaction.getSender(),
                    privateTransaction.getNonce(),
                    privateTransaction.getPrivacyGroupId().get())
                .toHexString()
            : VALID_CONTRACT_DEPLOYMENT_ADDRESS.toHexString(),
        privateTransaction.getSender().toHexString(),
        null,
        Collections.emptyList(),
        Bytes.EMPTY,
        Hash.EMPTY,
        0,
        0,
        markerTransaction.getHash(),
        privateTransaction.getHash(),
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

  private void executeAndVerifyCall(
      final PrivateTransactionReceiptResult expectedResult, final int i) {
    final PrivGetTransactionReceipt privGetTransactionReceipt =
        new PrivGetTransactionReceipt(
            blockchainQueries, privacyParameters, privacyController, enclavePublicKeyProvider);

    final JsonRpcRequestContext request = createRequestContext();

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionReceipt.response(request);
    final PrivateTransactionReceiptResult result =
        (PrivateTransactionReceiptResult) response.getResult();

    assertThat(result).isEqualToComparingFieldByField(expectedResult);
    verify(privacyController, times(i))
        .retrieveTransaction(
            markerTransaction.getPayload().toBase64String(),
            VALID_BASE64_ENCLAVE_KEY.toBase64String());
  }

  private JsonRpcRequestContext createRequestContext() {
    final Object[] params = new Object[] {markerTransaction.getHash()};
    return new JsonRpcRequestContext(
        new JsonRpcRequest("1", "priv_getTransactionReceipt", params), user);
  }
}
