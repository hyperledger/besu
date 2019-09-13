/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.LogsResult;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetFilterLogsTest {

  private EthGetFilterLogs method;

  @Mock FilterManager filterManager;

  @Before
  public void setUp() {
    method = new EthGetFilterLogs(filterManager, new JsonRpcParameter());
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_getFilterLogs");
  }

  @Test
  public void shouldReturnErrorWhenMissingParams() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "eth_getFilterLogs", new Object[] {});

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");

    verifyZeroInteractions(filterManager);
  }

  @Test
  public void shouldReturnErrorWhenMissingFilterId() {
    final JsonRpcRequest request = requestWithFilterId();

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");

    verifyZeroInteractions(filterManager);
  }

  @Test
  public void shouldReturnFilterNotFoundWhenFilterManagerReturnsNull() {
    final JsonRpcRequest request = requestWithFilterId("NOT FOUND");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.LOGS_FILTER_NOT_FOUND);
    when(filterManager.logs(eq("NOT FOUND"))).thenReturn(null);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyListWhenFilterManagerReturnsEmpty() {
    final JsonRpcRequest request = requestWithFilterId("0x1");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(new ArrayList<>()));
    when(filterManager.logs(eq("0x1"))).thenReturn(new ArrayList<>());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByFieldRecursively(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedLogsWhenFilterManagerReturnsLogs() {
    final JsonRpcRequest request = requestWithFilterId("0x1");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new LogsResult(logs()));
    when(filterManager.logs(eq("0x1"))).thenReturn(logs());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByFieldRecursively(expectedResponse);
  }

  private JsonRpcRequest requestWithFilterId(final Object... filterId) {
    return new JsonRpcRequest("2.0", "eth_getFilterLogs", filterId);
  }

  private List<LogWithMetadata> logs() {
    return Collections.singletonList(
        new LogWithMetadata(
            0,
            100L,
            Hash.ZERO,
            Hash.ZERO,
            0,
            Address.fromHexString("0x0"),
            BytesValue.EMPTY,
            Lists.newArrayList(),
            false));
  }
}
