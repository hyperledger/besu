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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.LogTopic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.junit.Test;

public class FilterParameterTest {

  @Test
  public void jsonWithArrayOfAddressesShouldSerializeSuccessfully() throws Exception {
    final String jsonWithAddressArray =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":[\"0x0\",\"0x1\"]}],\"id\":1}";
    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithAddressArray);
    final FilterParameter expectedFilterParameter = filterParameterWithAddresses("0x0", "0x1");

    final FilterParameter parsedFilterParameter =
        request.getRequiredParameter(0, FilterParameter.class);

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
        request.getRequiredParameter(0, FilterParameter.class);

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
        request.getRequiredParameter(0, FilterParameter.class);

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
        request.getRequiredParameter(0, FilterParameter.class);

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
        request.getRequiredParameter(0, FilterParameter.class);

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

  @Test
  public void whenTopicsParameterIsArrayOfStringsFilterContainsListOfSingletonLists()
      throws JsonProcessingException {
    final List<String> topics =
        Lists.newArrayList(
            "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d",
            null,
            "0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74");
    final FilterParameter filter = createFilterWithTopics(topics);

    assertThat(filter.getTopics())
        .containsExactly(
            singletonList(
                LogTopic.fromHexString(
                    "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d")),
            singletonList(null),
            singletonList(
                LogTopic.fromHexString(
                    "0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74")));
  }

  @Test
  public void whenTopicsParameterContainsArraysFilterContainsListOfSingletonLists()
      throws JsonProcessingException {
    final List<List<String>> topics =
        Lists.newArrayList(
            singletonList("0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d"),
            null,
            singletonList("0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74"));

    final FilterParameter filter = createFilterWithTopics(topics);

    assertThat(filter.getTopics())
        .containsExactly(
            singletonList(
                LogTopic.fromHexString(
                    "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d")),
            singletonList(null),
            singletonList(
                LogTopic.fromHexString(
                    "0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74")));
  }

  @Test
  public void whenTopicArrayContainsNullFilterContainsSingletonListOfAllTopics()
      throws JsonProcessingException {
    final List<List<String>> topics =
        Lists.newArrayList(
            singletonList("0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d"),
            null,
            singletonList("0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74"));
    final FilterParameter filter = createFilterWithTopics(topics);

    assertThat(filter.getTopics())
        .containsExactly(
            singletonList(
                LogTopic.fromHexString(
                    "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d")),
            singletonList(null),
            singletonList(
                LogTopic.fromHexString(
                    "0x000000000000000000000000244a53ab66ea8901c25efc48c8ab84662643cc74")));
  }

  @Test
  public void emptyListDecodesCorrectly() throws JsonProcessingException {
    final List<String> topics = emptyList();
    final FilterParameter filter = createFilterWithTopics(topics);

    assertThat(filter.getTopics().size()).isZero();
  }

  @Test
  public void emptyListAsSubTopicDecodesCorrectly() throws JsonProcessingException {
    final List<List<String>> topics =
        Lists.newArrayList(
            singletonList("0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d"),
            emptyList());
    final FilterParameter filter = createFilterWithTopics(topics);
    assertThat(filter.getTopics())
        .containsExactly(
            singletonList(
                LogTopic.fromHexString(
                    "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d")),
            emptyList());
  }

  private <T> FilterParameter createFilterWithTopics(final T inputTopics)
      throws JsonProcessingException {
    final Map<String, T> payload = new HashMap<>();
    payload.put("topics", inputTopics);

    final String json = new ObjectMapper().writeValueAsString(payload);
    return new ObjectMapper().readValue(json, FilterParameter.class);
  }

  private FilterParameter filterParameterWithAddresses(final String... addresses) {
    return new FilterParameter(
        "latest",
        "latest",
        Arrays.stream(addresses).map(Address::fromHexString).collect(toUnmodifiableList()),
        null,
        null);
  }

  private FilterParameter filterParameterWithAddressAndSingleListOfTopics(
      final String address, final String... topics) {
    return new FilterParameter(
        "latest",
        "latest",
        singletonList(Address.fromHexString(address)),
        singletonList(
            Arrays.stream(topics).map(LogTopic::fromHexString).collect(toUnmodifiableList())),
        null);
  }

  private FilterParameter filterParameterWithAddressAndMultipleListOfTopics(
      final String address, final String... topics) {
    List<LogTopic> topicsList =
        Arrays.stream(topics).map(LogTopic::fromHexString).collect(toUnmodifiableList());
    List<List<LogTopic>> topicsListList = Arrays.asList(topicsList, topicsList);
    return new FilterParameter(
        "latest", "latest", singletonList(Address.fromHexString(address)), topicsListList, null);
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String jsonWithSingleAddress)
      throws java.io.IOException {
    return new ObjectMapper().readValue(jsonWithSingleAddress, JsonRpcRequest.class);
  }

  private FilterParameter readJsonAsFilterParameter(final String json) throws java.io.IOException {
    return new ObjectMapper().readValue(json, FilterParameter.class);
  }
}
