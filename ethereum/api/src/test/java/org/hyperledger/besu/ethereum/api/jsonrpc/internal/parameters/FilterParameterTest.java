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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.TopicsParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class FilterParameterTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();

  @Test
  public void jsonWithArrayOfAddressesShouldSerializeSuccessfully() throws Exception {
    final String jsonWithAddressArray =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":[\"0x0\",\"0x1\"]}],\"id\":1}";
    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithAddressArray);
    final FilterParameter expectedFilterParameter = filterParameterWithAddresses("0x0", "0x1");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithSingleAddressShouldSerializeSuccessfully() throws Exception {
    final String jsonWithSingleAddress =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":\"0x0\"}],\"id\":1}";
    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithSingleAddress);
    final FilterParameter expectedFilterParameter = filterParameterWithAddresses("0x0");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithSingleAddressAndSingleTopicShouldSerializeSuccessfully() throws Exception {
    final String jsonWithSingleAddress =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":\"0x0\", \"topics\":\"0x0000000000000000000000000000000000000000000000000000000000000002\" }],\"id\":1}";

    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithSingleAddress);
    final FilterParameter expectedFilterParameter =
        filterParameterWithAddressAndSingleListOfTopics(
            "0x0", "0x0000000000000000000000000000000000000000000000000000000000000002");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithSingleAddressAndMultipleTopicsShouldSerializeSuccessfully() throws Exception {
    final String jsonWithSingleAddress =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":\"0x0\", \"topics\":[[\"0x0000000000000000000000000000000000000000000000000000000000000002\",\"0x0000000000000000000000000000000000000000000000000000000000000003\"]]}],\"id\":1}";

    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithSingleAddress);
    final FilterParameter expectedFilterParameter =
        filterParameterWithAddressAndSingleListOfTopics(
            "0x0",
            "0x0000000000000000000000000000000000000000000000000000000000000002",
            "0x0000000000000000000000000000000000000000000000000000000000000003");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithSingleAddressAndMultipleListsOfTopicsShouldSerializeSuccessfully()
      throws Exception {
    final String jsonWithSingleAddress =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":\"0x0\", \"topics\":[[\"0x0000000000000000000000000000000000000000000000000000000000000002\",\"0x0000000000000000000000000000000000000000000000000000000000000003\"],[\"0x0000000000000000000000000000000000000000000000000000000000000002\",\"0x0000000000000000000000000000000000000000000000000000000000000003\"]]}],\"id\":1}";

    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithSingleAddress);
    final FilterParameter expectedFilterParameter =
        filterParameterWithAddressAndMultipleListOfTopics(
            "0x0",
            "0x0000000000000000000000000000000000000000000000000000000000000002",
            "0x0000000000000000000000000000000000000000000000000000000000000003");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithParamsInDifferentOrderShouldDeserializeIntoFilterParameterSuccessfully()
      throws Exception {
    final String jsonWithTopicsFirst =
        "{\"topics\":[\"0x492e34c7da2a87c57444aa0f6143558999bceec63065f04557cfb20932e0d591\"], \"address\":\"0x8CdaF0CD259887258Bc13a92C0a6dA92698644C0\",\"fromBlock\":\"earliest\",\"toBlock\":\"latest\"}";
    final String jsonWithTopicsLast =
        "{\"address\":\"0x8CdaF0CD259887258Bc13a92C0a6dA92698644C0\",\"fromBlock\":\"earliest\",\"toBlock\":\"latest\",\"topics\":[\"0x492e34c7da2a87c57444aa0f6143558999bceec63065f04557cfb20932e0d591\"]}";

    assertThat(readJsonAsFilterParameter(jsonWithTopicsFirst))
        .isEqualToComparingFieldByFieldRecursively(readJsonAsFilterParameter(jsonWithTopicsLast));
  }

  private FilterParameter filterParameterWithAddresses(final String... addresses) {
    return new FilterParameter("latest", "latest", Arrays.asList(addresses), null, null);
  }

  private FilterParameter filterParameterWithAddressAndSingleListOfTopics(
      final String address, final String... topics) {
    return new FilterParameter(
        "latest",
        "latest",
        Arrays.asList(address),
        new TopicsParameter(Collections.singletonList(Arrays.asList(topics))),
        null);
  }

  private FilterParameter filterParameterWithAddressAndMultipleListOfTopics(
      final String address, final String... topics) {
    List<String> topicsList = Arrays.asList(topics);
    List<List<String>> topicsListList = Arrays.asList(topicsList, topicsList);
    return new FilterParameter(
        "latest", "latest", Arrays.asList(address), new TopicsParameter(topicsListList), null);
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String jsonWithSingleAddress)
      throws java.io.IOException {
    return new ObjectMapper().readValue(jsonWithSingleAddress, JsonRpcRequest.class);
  }

  private FilterParameter readJsonAsFilterParameter(final String json) throws java.io.IOException {
    return new ObjectMapper().readValue(json, FilterParameter.class);
  }
}
