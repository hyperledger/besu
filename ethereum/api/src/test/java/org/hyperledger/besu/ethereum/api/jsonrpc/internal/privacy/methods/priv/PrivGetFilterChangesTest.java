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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivGetFilterChanges;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import io.vertx.ext.auth.User;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivGetFilterChangesTest {

  private final String FILTER_ID = "0xdbdb02abb65a2ba57a1cc0336c17ef75";
  private final String ENCLAVE_KEY = "enclave_key";
  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private FilterManager filterManager;
  @Mock private MultiTenancyPrivacyController privacyController;
  @Mock private PrivacyIdProvider privacyIdProvider;

  private PrivGetFilterChanges method;

  @Before
  public void before() {
    method = new PrivGetFilterChanges(filterManager, privacyController, privacyIdProvider);
  }

  @Test
  public void getMethodReturnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("priv_getFilterChanges");
  }

  @Test
  public void privacyGroupIdIsRequired() {
    final JsonRpcRequestContext request = privGetFilterChangesRequest(null, "0x1");

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 0");
  }

  @Test
  public void multiTenancyCheckFailure() {
    final User user = mock(User.class);

    when(privacyIdProvider.getPrivacyUserId(any())).thenReturn(ENCLAVE_KEY);
    doThrow(new MultiTenancyValidationException("msg"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(eq(PRIVACY_GROUP_ID), eq(ENCLAVE_KEY));

    final JsonRpcRequestContext request =
        privGetFilterChangesRequestWithUser(PRIVACY_GROUP_ID, FILTER_ID, user);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessageContaining("msg");
  }

  @Test
  public void filterIdIsRequired() {
    final JsonRpcRequestContext request = privGetFilterChangesRequest(PRIVACY_GROUP_ID, null);

    assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("Missing required json rpc parameter at index 1");
  }

  @Test
  public void correctFilterIsQueried() {
    final JsonRpcRequestContext request = privGetFilterChangesRequest(PRIVACY_GROUP_ID, FILTER_ID);
    method.response(request);

    verify(filterManager).logsChanges(eq(FILTER_ID));
  }

  @Test
  public void returnExpectedLogs() {
    final LogWithMetadata logWithMetadata = logWithMetadata();
    when(filterManager.logsChanges(eq(FILTER_ID))).thenReturn(List.of(logWithMetadata));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(List.of(logWithMetadata)));

    final JsonRpcRequestContext request = privGetFilterChangesRequest(PRIVACY_GROUP_ID, FILTER_ID);
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void returnEmptyListWhenLogsReturnEmpty() {
    when(filterManager.logsChanges(eq(FILTER_ID))).thenReturn(Collections.emptyList());

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(Collections.emptyList()));

    final JsonRpcRequestContext request = privGetFilterChangesRequest(PRIVACY_GROUP_ID, FILTER_ID);
    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void returnFilterNotFoundWhenLogsReturnIsNull() {
    when(filterManager.logsChanges(eq(FILTER_ID))).thenReturn(null);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.FILTER_NOT_FOUND);

    final JsonRpcRequestContext request = privGetFilterChangesRequest(PRIVACY_GROUP_ID, FILTER_ID);
    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext privGetFilterChangesRequest(
      final String privacyGroupId, final String filterId) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "priv_getFilterChanges", new Object[] {privacyGroupId, filterId}));
  }

  private JsonRpcRequestContext privGetFilterChangesRequestWithUser(
      final String privacyGroupId, final String filterId, final User user) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "priv_getFilterChanges", new Object[] {privacyGroupId, filterId}),
        user);
  }

  private LogWithMetadata logWithMetadata() {
    return new LogWithMetadata(
        0,
        100L,
        Hash.ZERO,
        Hash.ZERO,
        0,
        Address.fromHexString("0x0"),
        Bytes.EMPTY,
        Lists.newArrayList(),
        false);
  }
}
