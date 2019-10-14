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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.junit.Before;
import org.junit.Test;

public class PrivGetTransactionCountTest {

  private static final PrivacyGroup[] PRIVACY_GROUP_FOR_VALID_LEGACY_CASE = {
    new PrivacyGroup(
        "Group1", PrivacyGroup.Type.PANTHEON, "Group1_Name", "Group1_Desc", new String[0]),
    new PrivacyGroup(
        "Group2", PrivacyGroup.Type.LEGACY, "Group2_Name", "Group2_Desc", new String[0]),
  };
  private static final PrivacyGroup[] PRIVACY_GROUP_WITH_TWO_LEGACY_TYPES = {
    new PrivacyGroup(
        "Group1", PrivacyGroup.Type.LEGACY, "Group1_Name", "Group1_Desc", new String[0]),
    new PrivacyGroup(
        "Group2", PrivacyGroup.Type.LEGACY, "Group2_Name", "Group2_Desc", new String[0]),
  };
  private static final PrivacyGroup[] PRIVACY_GROUP_WITHOUT_LEGACY_TYPE = {
    new PrivacyGroup(
        "Group1", PrivacyGroup.Type.PANTHEON, "Group1_Name", "Group1_Desc", new String[0]),
    new PrivacyGroup(
        "Group2", PrivacyGroup.Type.PANTHEON, "Group2_Name", "Group2_Desc", new String[0]),
  };
  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final String privacyGroupId =
      BytesValues.asBase64String(BytesValue.wrap("0x123".getBytes(UTF_8)));

  private JsonRpcRequest legacyRequest;
  private JsonRpcRequest nonLegacyRequest;

  private final Address senderAddress =
      Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
  private final long NONCE = 5;

  @Before
  public void setup() {
    final String[] privateFor = new String[] {"first", "second", "third"};
    final Address address = Address.fromHexString("55");
    final Object[] jsonBody = new Object[] {address.toString(), "thePrivateFromKey", privateFor};
    legacyRequest = new JsonRpcRequest("2.0", "priv_getTransactionCount", jsonBody);
    final Object[] params = new Object[] {senderAddress, privacyGroupId};
    nonLegacyRequest = new JsonRpcRequest("1", "priv_getTransactionCount", params);
  }

  @Test
  public void validRequestForNonLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);
    when(privateNonceProvider.getSenderNonce(senderAddress, privacyGroupId)).thenReturn(NONCE);
    final Enclave enclave = mock(Enclave.class);

    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionCount.response(nonLegacyRequest);

    assertThat(response.getResult()).isEqualTo(String.format("0x%X", NONCE));
  }

  @Test
  public void whenNonceProviderThrowsRuntimeExceptionProducesErrorResponseForNonLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);
    when(privateNonceProvider.getSenderNonce(any(), any())).thenThrow(RuntimeException.class);
    final Enclave enclave = mock(Enclave.class);

    final PrivGetTransactionCount method =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcResponse response = method.response(nonLegacyRequest);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void validRequestProducesExpectedNonceForLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);
    when(privateNonceProvider.getSenderNonce(any(), any())).thenReturn(NONCE);

    final Enclave enclave = mock(Enclave.class);
    when(enclave.findPrivacyGroup(any())).thenReturn(PRIVACY_GROUP_FOR_VALID_LEGACY_CASE);

    final PrivGetTransactionCount method =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcResponse response = method.response(legacyRequest);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);

    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    final int returnedValue = Integer.decode((String) successResponse.getResult());
    assertThat(returnedValue).isEqualTo(NONCE);
  }

  @Test
  public void whenNonceProviderThrowsRuntimeExceptionProducesErrorResponseForLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);
    when(privateNonceProvider.getSenderNonce(any(), any())).thenThrow(RuntimeException.class);

    final Enclave enclave = mock(Enclave.class);
    when(enclave.findPrivacyGroup(any())).thenReturn(PRIVACY_GROUP_FOR_VALID_LEGACY_CASE);

    final PrivGetTransactionCount method =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcResponse response = method.response(legacyRequest);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void moreThanOneMatchingLegacyGroupProducesErrorResponseForLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);
    when(privateNonceProvider.getSenderNonce(any(), any())).thenThrow(RuntimeException.class);

    final Enclave enclave = mock(Enclave.class);
    when(enclave.findPrivacyGroup(any())).thenReturn(PRIVACY_GROUP_WITH_TWO_LEGACY_TYPES);

    final PrivGetTransactionCount method =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcResponse response = method.response(legacyRequest);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void noMatchingLegacyGroupReturnsNonce0ForLegacyCase() {
    final PrivateTransactionHandler privateNonceProvider = mock(PrivateTransactionHandler.class);

    final Enclave enclave = mock(Enclave.class);
    when(enclave.findPrivacyGroup(any())).thenReturn(PRIVACY_GROUP_WITHOUT_LEGACY_TYPE);

    final PrivGetTransactionCount method =
        new PrivGetTransactionCount(parameters, privateNonceProvider, enclave);

    final JsonRpcResponse response = method.response(legacyRequest);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);

    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    final int returnedValue = Integer.decode((String) successResponse.getResult());
    assertThat(returnedValue).isEqualTo(0);
  }
}
