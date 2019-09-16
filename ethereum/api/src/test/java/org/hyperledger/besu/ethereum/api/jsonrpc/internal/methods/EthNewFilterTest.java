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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.LogsQuery;
import org.hyperledger.besu.ethereum.api.TopicsParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthNewFilterTest {

  @Mock private FilterManager filterManager;
  private EthNewFilter method;
  private final String ETH_METHOD = "eth_newFilter";

  @Before
  public void setUp() {
    method = new EthNewFilter(filterManager, new JsonRpcParameter());
  }

  @Test
  public void methodReturnsExpectedMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void newFilterWithoutFromBlockParamUsesLatestAsDefault() {
    final FilterParameter filterParameter = new FilterParameter(null, null, null, null, null);
    final JsonRpcRequest request = ethNewFilter(filterParameter);

    method.response(request);

    verify(filterManager).installLogFilter(refEq(blockParamLatest()), any(), any());
  }

  @Test
  public void newFilterWithoutToBlockParamUsesLatestAsDefault() {
    final FilterParameter filterParameter = new FilterParameter(null, null, null, null, null);
    final JsonRpcRequest request = ethNewFilter(filterParameter);

    method.response(request);

    verify(filterManager).installLogFilter(any(), refEq(blockParamLatest()), any());
  }

  @Test
  public void newFilterWithoutAddressAndTopicsParamsInstallsEmptyLogFilter() {
    final FilterParameter filterParameter =
        new FilterParameter("latest", "latest", null, null, null);
    final JsonRpcRequest request = ethNewFilter(filterParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x1");

    final LogsQuery expectedLogsQuery = new LogsQuery.Builder().build();
    when(filterManager.installLogFilter(any(), any(), refEq(expectedLogsQuery))).thenReturn("0x1");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(filterManager)
        .installLogFilter(
            refEq(blockParamLatest()), refEq(blockParamLatest()), refEq(expectedLogsQuery));
  }

  @Test
  public void newFilterWithTopicsOnlyParamInstallsExpectedLogFilter() {
    final List<List<String>> topics = topics();
    final FilterParameter filterParameter = filterParamWithAddressAndTopics(null, topics);
    final JsonRpcRequest request = ethNewFilter(filterParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x1");

    final LogsQuery expectedLogsQuery =
        new LogsQuery.Builder().topics(new TopicsParameter(topics)).build();
    when(filterManager.installLogFilter(any(), any(), refEq(expectedLogsQuery))).thenReturn("0x1");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(filterManager)
        .installLogFilter(
            refEq(blockParamLatest()), refEq(blockParamLatest()), refEq(expectedLogsQuery));
  }

  @Test
  public void newFilterWithAddressOnlyParamInstallsExpectedLogFilter() {
    final Address address = Address.fromHexString("0x0");
    final FilterParameter filterParameter = filterParamWithAddressAndTopics(address, null);
    final JsonRpcRequest request = ethNewFilter(filterParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x1");

    final LogsQuery expectedLogsQuery = new LogsQuery.Builder().address(address).build();
    when(filterManager.installLogFilter(any(), any(), refEq(expectedLogsQuery))).thenReturn("0x1");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(filterManager)
        .installLogFilter(
            refEq(blockParamLatest()), refEq(blockParamLatest()), refEq(expectedLogsQuery));
  }

  @Test
  public void newFilterWithAddressAndTopicsParamInstallsExpectedLogFilter() {
    final Address address = Address.fromHexString("0x0");
    final List<List<String>> topics = topics();
    final FilterParameter filterParameter = filterParamWithAddressAndTopics(address, topics);
    final JsonRpcRequest request = ethNewFilter(filterParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x1");

    final LogsQuery expectedLogsQuery =
        new LogsQuery.Builder().address(address).topics(new TopicsParameter(topics)).build();
    when(filterManager.installLogFilter(any(), any(), refEq(expectedLogsQuery))).thenReturn("0x1");

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(filterManager)
        .installLogFilter(
            refEq(blockParamLatest()), refEq(blockParamLatest()), refEq(expectedLogsQuery));
  }

  private List<List<String>> topics() {
    return Arrays.asList(
        Arrays.asList("0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
  }

  private FilterParameter filterParamWithAddressAndTopics(
      final Address address, final List<List<String>> topics) {
    final List<String> addresses = address != null ? Arrays.asList(address.toString()) : null;
    return new FilterParameter("latest", "latest", addresses, new TopicsParameter(topics), null);
  }

  private JsonRpcRequest ethNewFilter(final FilterParameter filterParameter) {
    return new JsonRpcRequest("2.0", ETH_METHOD, new Object[] {filterParameter});
  }

  private BlockParameter blockParamLatest() {
    return new BlockParameter("latest");
  }
}
