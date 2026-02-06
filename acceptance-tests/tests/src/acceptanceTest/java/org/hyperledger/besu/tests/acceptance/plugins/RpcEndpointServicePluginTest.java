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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcEndpointServicePluginTest extends AcceptanceTestBase {

  private BesuNode node;

  private OkHttpClient client;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  @BeforeEach
  public void setUp() throws Exception {
    node = besu.createPluginsNode("node1", List.of("testPlugins"), List.of("--rpc-http-api=TESTS"));
    cluster.start(node);
    client = new OkHttpClient();
  }

  @Test
  public void canUseRpcToSetValue() throws IOException {
    String setValue = "secondCall";

    ObjectNode resultJson = callTestMethod("tests_getValue", List.of());
    assertThat(resultJson.get("result").asText()).isEqualTo("InitialValue");

    resultJson = callTestMethod("tests_setValue", List.of(setValue));
    assertThat(resultJson.get("result").asText()).isEqualTo(setValue);

    resultJson = callTestMethod("tests_getValue", List.of("ignored"));
    assertThat(resultJson.get("result").asText()).isEqualTo(setValue);
  }

  @Test
  public void canCheckArgumentInsideSetValue() throws IOException {
    ObjectNode resultJson = callTestMethod("tests_setValue", List.of("one", "two"));
    assertThat(resultJson.get("error").get("message").asText()).isEqualTo("Internal error");
  }

  @Test
  public void exceptionsInPluginMethodReturnError() throws IOException {
    ObjectNode resultJson = callTestMethod("tests_throwException", List.of());
    assertThat(resultJson.get("error").get("message").asText()).isEqualTo("Internal error");
  }

  @Test
  public void onlyEnabledMethodsReturn() throws IOException {
    ObjectNode resultJson = callTestMethod("notEnabled_getValue", List.of());
    assertThat(resultJson.get("error").get("message").asText()).isEqualTo("Method not found");
  }

  @Test
  public void mixedTypeArraysAreStringified() throws IOException {
    ObjectNode resultJson = callTestMethod("tests_replaceValueList", List.of());
    assertThat(resultJson.get("result")).isEmpty();

    resultJson = callTestMethod("tests_replaceValueList", List.of("One", 2, true));
    JsonNode result = resultJson.get("result");

    assertThat(result.get(0).asText()).isEqualTo("One");
    assertThat(result.get(1).asText()).isEqualTo("2");
    assertThat(result.get(2).asText()).isEqualTo("true");
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
                    .url("http://" + node.getHostName() + ":" + node.getJsonRpcPort().get() + "/")
                    .build())
            .execute()
            .body()
            .string();
    return JsonUtil.objectNodeFromString(resultString);
  }
}
