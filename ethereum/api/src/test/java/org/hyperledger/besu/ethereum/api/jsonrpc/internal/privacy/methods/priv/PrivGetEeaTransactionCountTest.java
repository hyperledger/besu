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
import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PrivGetEeaTransactionCountTest {
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String[] PRIVATE_FOR = {
    "sgFkVOyFndZe/5SAZJO5UYbrl7pezHetveriBBWWnE8=",
    "R1kW75NQC9XX3kwNpyPjCBFflM29+XvnKKS9VLrUkzo=",
    "QzHuACXpfhoGAgrQriWJcDJ6MrUwcCvutKMoAn9KplQ="
  };

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);
  private JsonRpcRequestContext request;

  private final Address address =
      Address.fromHexString("0x1000000000000000000000000000000000000001");
  private final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @BeforeEach
  public void setup() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    final Object[] jsonBody = new Object[] {address.toString(), ENCLAVE_PUBLIC_KEY, PRIVATE_FOR};
    request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "priv_getEeaTransactionCount", jsonBody));
  }

  @ParameterizedTest(name = "{index}: {1}")
  @MethodSource({"provideNonces"})
  void validRequestProducesExpectedNonce(final long nonce, final String ignoredName) {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineNonce(any(), any(), eq(ENCLAVE_PUBLIC_KEY))).thenReturn(nonce);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);

    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo("0x" + Long.toHexString(nonce));
  }

  @Test
  void nonceProviderThrowsRuntimeExceptionProducesErrorResponse() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineNonce(any(), any(), eq(ENCLAVE_PUBLIC_KEY)))
        .thenThrow(EnclaveClientException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  void nonceProviderThrowsAnExceptionProducesErrorResponse() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineNonce(any(), any(), eq(ENCLAVE_PUBLIC_KEY)))
        .thenThrow(EnclaveClientException.class);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  @Test
  void failsWithUnauthorizedErrorIfMultiTenancyValidationFails() {
    final PrivGetEeaTransactionCount method =
        new PrivGetEeaTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineNonce(any(), any(), eq(ENCLAVE_PUBLIC_KEY)))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError())
        .isEqualTo(JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
  }

  private static Stream<Arguments> provideNonces() {
    return Stream.of(Arguments.of(8, "low nonce"), Arguments.of(MAX_NONCE - 1, "high nonce"));
  }
}
