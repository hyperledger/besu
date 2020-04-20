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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Collections;
import java.util.List;

import io.vertx.ext.auth.User;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivNewFilterTest {

  private final String ENCLAVE_KEY = "enclave_key";
  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private FilterManager filterManager;
  @Mock private PrivacyController privacyController;
  @Mock private EnclavePublicKeyProvider enclavePublicKeyProvider;

  private PrivNewFilter method;

  @Before
  public void before() {
    method = new PrivNewFilter(filterManager, privacyController, enclavePublicKeyProvider);
  }

  @Test
  public void getMethodReturnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_newFilter");
  }

  @Test
  public void privacyGroupIdIsRequired() {
    final JsonRpcRequestContext request = privNewFilterRequest(null, mock(FilterParameter.class));

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 0");
  }

  @Test
  public void filterParameterIsRequired() {
    final JsonRpcRequestContext request = privNewFilterRequest(PRIVACY_GROUP_ID, null);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 1");
  }

  @Test
  public void filterWithInvalidParameters() {
    final FilterParameter invalidFilter =
        new FilterParameter(
            "earliest",
            "earliest",
            Collections.emptyList(),
            Collections.emptyList(),
            Hash.ZERO.toHexString());

    final JsonRpcRequestContext request = privNewFilterRequest(PRIVACY_GROUP_ID, invalidFilter);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void filterWithExpectedQueryIsCreated() {
    final BlockParameter fromBlock = new BlockParameter("earliest");
    final BlockParameter toBlock = new BlockParameter("latest");
    final List<Address> addresses = List.of(Address.ZERO);
    final List<List<LogTopic>> logTopics = List.of(List.of(LogTopic.of(Bytes32.random())));

    final FilterParameter filter =
        new FilterParameter("earliest", "latest", addresses, logTopics, null);

    final LogsQuery expectedQuery =
        new LogsQuery.Builder().addresses(addresses).topics(logTopics).build();

    final JsonRpcRequestContext request = privNewFilterRequest(PRIVACY_GROUP_ID, filter);
    method.response(request);

    verify(filterManager)
        .installPrivateLogFilter(
            eq(PRIVACY_GROUP_ID), refEq(fromBlock), refEq(toBlock), eq((expectedQuery)));
  }

  @Test
  public void multiTenancyCheckFailure() {
    final User user = mock(User.class);
    final FilterParameter filterParameter = mock(FilterParameter.class);

    when(enclavePublicKeyProvider.getEnclaveKey(any())).thenReturn(ENCLAVE_KEY);
    doThrow(new MultiTenancyValidationException("msg"))
        .when(privacyController)
        .verifyPrivacyGroupContainsEnclavePublicKey(eq(PRIVACY_GROUP_ID), eq(ENCLAVE_KEY));

    final JsonRpcRequestContext request =
        privNewFilterRequestWithUser(PRIVACY_GROUP_ID, filterParameter, user);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessageContaining("msg");
  }

  private JsonRpcRequestContext privNewFilterRequest(
      final String privacyGroupId, final FilterParameter filterParameter) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "priv_newFilter", new Object[] {privacyGroupId, filterParameter}));
  }

  private JsonRpcRequestContext privNewFilterRequestWithUser(
      final String privacyGroupId, final FilterParameter filterParameter, final User user) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "priv_newFilter", new Object[] {privacyGroupId, filterParameter}),
        user);
  }
}
