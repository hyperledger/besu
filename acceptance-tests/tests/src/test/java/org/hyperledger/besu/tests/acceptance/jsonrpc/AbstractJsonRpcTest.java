/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.rpc.JsonRpcTestCase;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;

abstract class AbstractJsonRpcTest {
  private static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");

  static class JsonRpcTestsContext {
    final Cluster cluster;
    final BesuNode besuNode;
    final OkHttpClient httpClient;
    final ObjectMapper mapper;

    public JsonRpcTestsContext(final String genesisFile) throws IOException {
      cluster = new Cluster(new NetConditions(new NetTransactions()));

      besuNode =
          new BesuNodeFactory().createExecutionEngineGenesisNode("executionEngine", genesisFile);
      cluster.start(besuNode);
      httpClient = new OkHttpClient();

      mapper = new ObjectMapper();
    }

    public void tearDown() {
      cluster.close();
    }
  }

  private final JsonRpcTestsContext testsContext;
  private final URI testCaseFileURI;

  public AbstractJsonRpcTest(
      final String ignored, final JsonRpcTestsContext testsContext, final URI testCaseFileURI) {
    this.testCaseFileURI = testCaseFileURI;
    this.testsContext = testsContext;
  }

  @Test
  public void test() throws IOException {
    final JsonRpcTestCase testCase =
        testsContext.mapper.readValue(testCaseFileURI.toURL(), JsonRpcTestCase.class);

    final String rpcMethod = String.valueOf(testCase.getRequest().get("method"));
    OkHttpClient client = testsContext.httpClient;
    if (System.getenv("BESU_DEBUG_CHILD_PROCESS_PORT") != null) {
      // if running in debug mode, set a longer timeout
      client =
          testsContext
              .httpClient
              .newBuilder()
              .readTimeout(900, java.util.concurrent.TimeUnit.SECONDS)
              .build();
    }
    final Call testRequest =
        client.newCall(
            new Request.Builder()
                .url(getRpcUrl(rpcMethod))
                .post(RequestBody.create(testCase.getRequest().toString(), MEDIA_TYPE_JSON))
                .build());
    final Response response = testRequest.execute();

    assertThat(response.code()).isEqualTo(testCase.getStatusCode());
    final ObjectNode actualBody = JsonUtil.objectNodeFromString(response.body().string());
    evaluateResponse(actualBody, testRequest, testCase, testCaseFileURI.toURL());
    final ObjectNode expectedBody =
        JsonUtil.objectNodeFromString(testCase.getResponse().toString());
    assertThat(actualBody)
        .withFailMessage(
            "%s\ndid not equal\n %s", actualBody.toPrettyString(), expectedBody.toPrettyString())
        .isEqualTo(expectedBody);
  }

  protected void evaluateResponse(
      final ObjectNode responseBody,
      final Call testRequest,
      final JsonRpcTestCase testCase,
      final URL url) {}

  private String getRpcUrl(final String rpcMethod) {
    if (rpcMethod.contains("eth_") || rpcMethod.contains("engine_")) {
      return testsContext.besuNode.engineRpcUrl().get();
    }

    return testsContext.besuNode.jsonRpcBaseUrl().get();
  }

  public static Iterable<Object[]> testCases(final String testCasesPath) throws URISyntaxException {

    final File[] testCasesList =
        new File(AbstractJsonRpcTest.class.getResource(testCasesPath).toURI()).listFiles();

    return Arrays.stream(testCasesList)
        .sorted()
        .map(file -> new Object[] {file.getName(), file.toURI()})
        .collect(Collectors.toList());
  }
}
