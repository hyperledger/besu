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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import org.junit.Before;
import org.junit.Test;

public class PrivGetEeaTransactionCountTest {
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);
  private JsonRpcRequestContext request;

  private final String privateFrom = "thePrivateFromKey";
  private final String[] privateFor = new String[] {"first", "second", "third"};
  private final Address address = Address.fromHexString("55");
  private final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Before
  public void setup() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    final Object[] jsonBody = new Object[] {address.toString(), privateFrom, privateFor};
    request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "priv_getEeaTransactionCount", jsonBody));
  }

  @Test
  public void validRequestProducesExpectedNonce() {
    final long reportedNonce = 8L;
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, enclavePublicKeyProvider);

    when(privacyController.determineEeaNonce(privateFrom, privateFor, address, ENCLAVE_PUBLIC_KEY))
        .thenReturn(reportedNonce);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);

    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    final int returnedValue = Integer.decode((String) successResponse.getResult());
    assertThat(returnedValue).isEqualTo(reportedNonce);
  }

  @Test
  public void nonceProviderThrowsRuntimeExceptionProducesErrorResponse() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, enclavePublicKeyProvider);

    when(privacyController.determineEeaNonce(privateFrom, privateFor, address, ENCLAVE_PUBLIC_KEY))
        .thenThrow(EnclaveClientException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void nonceProviderThrowsAnExceptionProducesErrorResponse() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, enclavePublicKeyProvider);

    when(privacyController.determineEeaNonce(privateFrom, privateFor, address, ENCLAVE_PUBLIC_KEY))
        .thenThrow(EnclaveClientException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  public void failsWithUnauthorizedErrorIfMultiTenancyValidationFails() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, enclavePublicKeyProvider);

    when(privacyController.determineEeaNonce(privateFrom, privateFor, address, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }
}
