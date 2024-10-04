/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BlockchainServiceFinalizedBlockPluginTest extends AcceptanceTestBase {

  private BesuNode pluginNode;
  private BesuNode minerNode;
  private OkHttpClient client;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  @BeforeEach
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("minerNode");
    pluginNode =
        besu.createPluginsNode("node1", List.of("testPlugins"), List.of("--rpc-http-api=UPDATER"));
    cluster.start(minerNode, pluginNode);
    client = new OkHttpClient();
  }

  @Test
  @DisplayName("Calling update{Finalized/Safe}BlockV1 will set block")
  public void canUpdateFinalizedBlock() throws IOException {
    pluginNode.verify(blockchain.minimumHeight(5));

    // RPC Call. Set the safe block number to 3
    final ObjectNode resultJson = callTestMethod("updater_updateSafeBlockV1", List.of(3L));
    assertThat(resultJson.get("result").asBoolean()).isTrue();

    // RPC Call. Set the finalized block number to 4
    final ObjectNode finalizedResultJson =
        callTestMethod("updater_updateFinalizedBlockV1", List.of(4L));
    assertThat(finalizedResultJson.get("result").asBoolean()).isTrue();

    final ObjectNode blockNumberSafeResult =
        callTestMethod("eth_getBlockByNumber", List.of("SAFE", true));
    assertThat(blockNumberSafeResult.get("result").get("number").asText()).isEqualTo("0x3");

    // Verify the value was set
    final ObjectNode blockNumberFinalizedResult =
        callTestMethod("eth_getBlockByNumber", List.of("FINALIZED", true));
    assertThat(blockNumberFinalizedResult.get("result").get("number").asText()).isEqualTo("0x4");
  }

  @Test
  @DisplayName("Calling update{Finalized/Safe}BlockV1 with non-existing block number returns error")
  public void nonExistingBlockNumberReturnsError() throws IOException {
    pluginNode.verify(blockchain.minimumHeight(5));

    final ObjectNode[] resultsJson = new ObjectNode[2];
    resultsJson[0] = callTestMethod("updater_updateFinalizedBlockV1", List.of(250L));
    resultsJson[1] = callTestMethod("updater_updateSafeBlockV1", List.of(250L));

    for (int i = 0; i < resultsJson.length; i++) {
      assertThat(resultsJson[i].get("error").get("code").asInt()).isEqualTo(-32000);
      assertThat(resultsJson[i].get("error").get("message").asText()).isEqualTo("Block not found");
      assertThat(resultsJson[i].get("error").get("data").asText())
          .isEqualTo("Block not found in the local chain: 250");
    }
  }

  @ParameterizedTest(name = "{index} - blockNumber={0}")
  @ValueSource(longs = {-1, 0})
  @DisplayName("Calling update{Finalized/Safe}BlockV1 with block number <= 0 returns error")
  public void invalidBlockNumberReturnsError(final long blockNumber) throws IOException {
    pluginNode.verify(blockchain.minimumHeight(5));

    final ObjectNode[] resultsJson = new ObjectNode[2];
    resultsJson[0] = callTestMethod("updater_updateFinalizedBlockV1", List.of(blockNumber));
    resultsJson[1] = callTestMethod("updater_updateSafeBlockV1", List.of(blockNumber));

    for (int i = 0; i < resultsJson.length; i++) {
      assertThat(resultsJson[i].get("error").get("code").asInt()).isEqualTo(-32602);
      assertThat(resultsJson[i].get("error").get("message").asText()).isEqualTo("Invalid params");
      assertThat(resultsJson[i].get("error").get("data").asText())
          .isEqualTo("Block number must be greater than 0");
    }
  }

  @Test
  @DisplayName("Calling update{Finalized/Safe}BlockV1 with invalid block number type returns error")
  public void invalidBlockNumberTypeReturnsError() throws IOException {
    pluginNode.verify(blockchain.minimumHeight(5));

    final ObjectNode[] resultsJson = new ObjectNode[2];
    resultsJson[0] = callTestMethod("updater_updateFinalizedBlockV1", List.of("testblock"));
    resultsJson[1] = callTestMethod("updater_updateSafeBlockV1", List.of("testblock"));

    for (int i = 0; i < resultsJson.length; i++) {
      assertThat(resultsJson[i].get("error").get("code").asInt()).isEqualTo(-32602);
      assertThat(resultsJson[i].get("error").get("message").asText()).isEqualTo("Invalid params");
      assertThat(resultsJson[i].get("error").get("data").asText())
          .isEqualTo(
              "Invalid json rpc parameter at index 0. Supplied value was: 'testblock' of type: 'java.lang.String' - expected type: 'java.lang.Long'");
    }
  }

  private ObjectNode callTestMethod(final String method, final List<Object> params)
      throws IOException {
    String format =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[%s],\"id\":42}",
            method,
            params.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(",")));

    RequestBody body = RequestBody.create(format, JSON);

    final String resultString =
        client
            .newCall(
                new Request.Builder()
                    .post(body)
                    .url(
                        "http://"
                            + pluginNode.getHostName()
                            + ":"
                            + pluginNode.getJsonRpcPort().get()
                            + "/")
                    .build())
            .execute()
            .body()
            .string();
    return JsonUtil.objectNodeFromString(resultString);
  }
}
