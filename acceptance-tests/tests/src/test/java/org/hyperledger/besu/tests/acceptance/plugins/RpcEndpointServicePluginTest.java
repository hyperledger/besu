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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.util.Collections;

import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.Before;
import org.junit.Test;

public class RpcEndpointServicePluginTest extends AcceptanceTestBase {

  private BesuNode node;

  private OkHttpClient client;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  @Before
  public void setUp() throws Exception {
    node =
        besu.createPluginsNode(
            "node1", Collections.singletonList("testPlugins"), Collections.emptyList());
    cluster.start(node);
    client = new OkHttpClient();
  }

  @Test
  public void rpcWorking() throws IOException {
    final String firstCall = "FirstCall";
    final String secondCall = "SecondCall";
    final String thirdCall = "ThirdCall";

    ObjectNode resultJson = callTestMethod("unitTests_replaceValue", firstCall);
    assertThat(resultJson.get("result").asText()).isEqualTo("InitialValue");

    resultJson = callTestMethod("unitTests_replaceValueArray", secondCall);
    assertThat(resultJson.get("result").get(0).asText()).isEqualTo(firstCall);

    resultJson = callTestMethod("unitTests_replaceValueBean", thirdCall);
    assertThat(resultJson.get("result").get("value").asText()).isEqualTo(secondCall);
  }

  @Test
  public void throwsError() throws IOException {
    ObjectNode resultJson = callTestMethod("unitTests_replaceValue", null);
    assertThat(resultJson.get("error").get("message").asText()).isEqualTo("Internal error");
  }

  private ObjectNode callTestMethod(final String method, final String value) throws IOException {
    final String resultString =
        client
            .newCall(
                new Request.Builder()
                    .post(
                        RequestBody.create(
                            "{\"jsonrpc\":\"2.0\",\"method\":\""
                                + method
                                + "\",\"params\":["
                                + "\""
                                + value
                                + "\""
                                + "],\"id\":33}",
                            JSON))
                    .url(
                        "http://"
                            + node.getHostName()
                            + ":"
                            + node.getJsonRpcSocketPort().get()
                            + "/")
                    .build())
            .execute()
            .body()
            .string();
    return JsonUtil.objectNodeFromString(resultString);
  }
}
