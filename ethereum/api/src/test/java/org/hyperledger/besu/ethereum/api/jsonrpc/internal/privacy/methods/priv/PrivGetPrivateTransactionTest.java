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
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_BASE64_ENCLAVE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.Collections;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivGetPrivateTransactionTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private final PrivateTransaction privateContractDeploymentTransactionLegacy =
      PrivateTransactionDataFixture.privateContractDeploymentTransactionLegacy();
  private final PrivateTransaction privateContractDeploymentTransactionBesu =
      PrivateTransactionDataFixture.privateContractDeploymentTransactionBesu();
  private final Transaction markerTransaction =
      PrivateTransactionDataFixture.privacyMarkerTransaction();

  private final Enclave enclave = mock(Enclave.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final TransactionWithMetadata returnedTransaction = mock(TransactionWithMetadata.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);

  private final User user =
      new JWTUser(
          new JsonObject().put("privacyPublicKey", VALID_BASE64_ENCLAVE_KEY.toBase64String()), "");
  private final EnclavePublicKeyProvider enclavePublicKeyProvider =
      (user) -> VALID_BASE64_ENCLAVE_KEY.toBase64String();

  @Before
  public void before() {
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(returnedTransaction.getBlockHash()).thenReturn(Optional.of(Hash.ZERO));

    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(markerTransaction);
  }

  @Test
  public void returnsPrivateTransactionLegacy() {
    final PrivateTransactionLegacyResult privateTransactionLegacyResult =
        new PrivateTransactionLegacyResult(privateContractDeploymentTransactionLegacy);

    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenReturn(
            PrivateTransactionDataFixture.generateReceiveResponse(
                privateContractDeploymentTransactionLegacy));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).isEqualToComparingFieldByField(privateTransactionLegacyResult);
    verify(privacyController)
        .retrieveTransaction(
            markerTransaction.getPayload().toBase64String(),
            VALID_BASE64_ENCLAVE_KEY.toBase64String());
  }

  protected PrivateTransactionResult makeRequest(final JsonRpcRequestContext request) {
    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(
            blockchain, privacyController, privateStateStorage, enclavePublicKeyProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    return (PrivateTransactionResult) response.getResult();
  }

  @Test
  public void returnsPrivateTransactionGroup() {
    final PrivateTransactionGroupResult privateTransactionGroupResult =
        new PrivateTransactionGroupResult(privateContractDeploymentTransactionBesu);

    final JsonRpcRequestContext request = createRequestContext();

    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenReturn(
            PrivateTransactionDataFixture.generateReceiveResponse(
                privateContractDeploymentTransactionBesu));

    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).isEqualToComparingFieldByField(privateTransactionGroupResult);
  }

  @Test
  public void returnsPrivateTransactionOnChain() {
    final PrivateTransactionGroupResult privateTransactionGroupResult =
        new PrivateTransactionGroupResult(privateContractDeploymentTransactionBesu);

    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenReturn(
            PrivateTransactionDataFixture.generateVersionedReceiveResponse(
                privateContractDeploymentTransactionBesu));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).isEqualToComparingFieldByField(privateTransactionGroupResult);
  }

  @Test
  public void returnsPrivateTransactionOnChainFromBlob() {
    final PrivateTransactionGroupResult privateTransactionGroupResult =
        new PrivateTransactionGroupResult(privateContractDeploymentTransactionBesu);

    when(privateStateStorage.getPrivacyGroupHeadBlockMap(any(Bytes32.class)))
        .thenReturn(
            Optional.of(
                new PrivacyGroupHeadBlockMap(Collections.singletonMap(Bytes32.ZERO, Hash.ZERO))));
    when(privateStateStorage.getAddDataKey(any(Bytes32.class)))
        .thenReturn(Optional.of(Bytes32.ZERO));
    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenThrow(new EnclaveClientException(0, "EnclavePayloadNotFound"));
    when(privacyController.retrieveAddBlob(anyString()))
        .thenReturn(
            PrivateTransactionDataFixture.generateAddBlobResponse(
                privateContractDeploymentTransactionBesu, markerTransaction));

    final JsonRpcRequestContext request = createRequestContext();

    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).isEqualToComparingFieldByField(privateTransactionGroupResult);
  }

  @Test
  public void returnNullWhenPrivateMarkerTransactionDoesNotExist() {
    when(blockchain.transactionByHash(any(Hash.class))).thenReturn(Optional.empty());

    final JsonRpcRequestContext request = createRequestContext();

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(
            blockchain, privacyController, privateStateStorage, enclavePublicKeyProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);

    assertThat(response.getResult()).isNull();
  }

  @Test
  public void failsWithEnclaveErrorOnEnclaveError() {
    final JsonRpcRequestContext request = createRequestContext();

    when(privacyController.retrieveTransaction(anyString(), any()))
        .thenThrow(new EnclaveClientException(500, "enclave failure"));

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(
            blockchain, privacyController, privateStateStorage, enclavePublicKeyProvider);
    final JsonRpcResponse response = privGetPrivateTransaction.response(request);
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.ENCLAVE_ERROR);
    assertThat(response).isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext createRequestContext() {
    final Object[] params = new Object[] {markerTransaction.getHash()};
    return new JsonRpcRequestContext(
        new JsonRpcRequest("1", "priv_getTransactionReceipt", params), user);
  }
}
