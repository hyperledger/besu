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

import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.engine.EngineTestCase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExecutionEngineAbstractJsonRpcTest {
  private static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");

  static class ExecutionEngineJsonTestsContext {
    final Cluster cluster;
    final BesuNode executionEngine;
    final OkHttpClient consensusClient;
    final ObjectMapper mapper;

    public ExecutionEngineJsonTestsContext(final String genesisFile) throws IOException {
      cluster = new Cluster(new NetConditions(new NetTransactions()));
      executionEngine =
          new BesuNodeFactory().createExecutionEngineGenesisNode("executionEngine", genesisFile);
      cluster.start(executionEngine);
      consensusClient = new OkHttpClient();

      mapper = new ObjectMapper();
    }

    public void tearDown() {
      cluster.close();
    }
  }

  private final ExecutionEngineJsonTestsContext testsContext;
  private final URI testCaseFileURI;

  public ExecutionEngineAbstractJsonRpcTest(
      final String ignored,
      final ExecutionEngineJsonTestsContext testsContext,
      final URI testCaseFileURI) {
    this.testCaseFileURI = testCaseFileURI;
    this.testsContext = testsContext;
  }

  @Test
  public void test() throws IOException {
    final EngineTestCase testCase =
        testsContext.mapper.readValue(testCaseFileURI.toURL(), EngineTestCase.class);

    final Call preparePayloadRequest =
        testsContext.consensusClient.newCall(
            new Request.Builder()
                .url(testsContext.executionEngine.engineRpcUrl().get())
                .post(RequestBody.create(testCase.getRequest().toString(), MEDIA_TYPE_JSON))
                .build());
    final Response response = preparePayloadRequest.execute();

    assertThat(response.code()).isEqualTo(testCase.getStatusCode());
    assertThat(response.body().string()).isEqualTo(testCase.getResponse().toPrettyString());
  }

  public static Iterable<Object[]> testCases(final String testCasesPath) throws URISyntaxException {

    final File[] testCasesList =
        new File(ExecutionEngineAbstractJsonRpcTest.class.getResource(testCasesPath).toURI())
            .listFiles();

    return Arrays.stream(testCasesList)
        .sorted()
        .map(file -> new Object[] {file.getName(), file.toURI()})
        .collect(Collectors.toList());
  }
}
