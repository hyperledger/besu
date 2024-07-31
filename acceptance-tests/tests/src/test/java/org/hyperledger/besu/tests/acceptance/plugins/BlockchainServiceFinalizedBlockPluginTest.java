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
  @DisplayName("updateFinalizedBlockV1 can be called to set finalized block")
  public void canCallRocMethod() throws IOException {
    pluginNode.verify(blockchain.minimumHeight(20));

    // RPC Call. Set the finalized block number to 18 (0x12)
    ObjectNode resultJson = callTestMethod("updater_updateFinalizedBlockV1", List.of(18L));
    assertThat(resultJson.get("result").asBoolean()).isTrue();

    // Verify the value was set
    ObjectNode blockNumberResult =
        callTestMethod("eth_getBlockByNumber", List.of("FINALIZED", true));
    assertThat(blockNumberResult.get("result").get("number").asText()).isEqualTo("0x12");
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
