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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogsResult;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthGetFilterChangesTest {

  private EthGetFilterChanges method;

  @Mock FilterManager filterManager;

  @BeforeEach
  public void setUp() {
    method = new EthGetFilterChanges(filterManager);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_getFilterChanges");
  }

  @Test
  public void shouldThrowExceptionWhenNoParamsSupplied() {
    final JsonRpcRequestContext request = requestWithParams();

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid filter ID parameter (index 0)");

    verifyNoInteractions(filterManager);
  }

  @Test
  public void shouldThrowExceptionWhenInvalidParamsSupplied() {
    final JsonRpcRequestContext request = requestWithParams();

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid filter ID parameter (index 0)");

    verifyNoInteractions(filterManager);
  }

  @Test
  public void shouldReturnErrorResponseWhenFilterManagerDoesNotFindAnyFilters() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.FILTER_NOT_FOUND);

    when(filterManager.blockChanges(anyString())).thenReturn(null);
    when(filterManager.pendingTransactionChanges(anyString())).thenReturn(null);
    when(filterManager.logsChanges(anyString())).thenReturn(null);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(filterManager).blockChanges(eq("0x1"));
    verify(filterManager).pendingTransactionChanges(eq("0x1"));
    verify(filterManager).logsChanges(eq("0x1"));
  }

  @Test
  public void shouldReturnHashesWhenFilterManagerFindsBlockFilterWithHashes() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges("0x1")).thenReturn(Lists.newArrayList(Hash.ZERO));

    final List<String> expectedHashes =
        Lists.newArrayList(Hash.ZERO).stream().map(Hash::toString).collect(Collectors.toList());
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, expectedHashes);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyHashesWhenFilterManagerFindsBlockFilterWithNoChanges() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges("0x1")).thenReturn(Lists.newArrayList());

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, Lists.newArrayList());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnHashesWhenFilterManagerFindsPendingTransactionFilterWithHashes() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges(anyString())).thenReturn(null);
    when(filterManager.pendingTransactionChanges("0x1")).thenReturn(Lists.newArrayList(Hash.ZERO));

    final List<String> expectedHashes =
        Lists.newArrayList(Hash.ZERO).stream().map(Hash::toString).collect(Collectors.toList());
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, expectedHashes);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyHashesWhenFilterManagerFindsPendingTransactionFilterWithNoChanges() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges(anyString())).thenReturn(null);
    when(filterManager.pendingTransactionChanges("0x1")).thenReturn(Lists.newArrayList());

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, Lists.newArrayList());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnLogsWhenFilterManagerFindsLogFilterWithLogs() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges(anyString())).thenReturn(null);
    when(filterManager.pendingTransactionChanges(anyString())).thenReturn(null);
    when(filterManager.logsChanges("0x1")).thenReturn(Lists.newArrayList(logWithMetadata()));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(Lists.newArrayList(logWithMetadata())));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyLogsWhenFilterManagerFindsLogFilterWithNoChanges() {
    final JsonRpcRequestContext request = requestWithParams("0x1");
    when(filterManager.blockChanges(anyString())).thenReturn(null);
    when(filterManager.pendingTransactionChanges(anyString())).thenReturn(null);
    when(filterManager.logsChanges("0x1")).thenReturn(Lists.newArrayList());

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(Lists.newArrayList()));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_getFilterChanges", params));
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
