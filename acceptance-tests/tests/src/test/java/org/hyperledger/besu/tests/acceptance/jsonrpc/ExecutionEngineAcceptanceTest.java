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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExecutionEngineAcceptanceTest {
  private static final String GENESIS_FILE = "/jsonrpc/engine/genesis.json";
  private static final String TEST_CASE_PATH = "/jsonrpc/engine/test-cases/";
  private static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");

  private static Cluster cluster;
  private static BesuNode executionEngine;
  private static OkHttpClient consensusClient;
  private static ObjectMapper mapper;

  private final URI testCaseFileURI;

  public ExecutionEngineAcceptanceTest(final String ignored, final URI testCaseFileURI) {
    this.testCaseFileURI = testCaseFileURI;
  }

  @BeforeClass
  public static void init() throws IOException {
    cluster = new Cluster(new NetConditions(new NetTransactions()));

    executionEngine =
        new BesuNodeFactory().createExecutionEngineGenesisNode("executionEngine", GENESIS_FILE);
    cluster.start(executionEngine);
    consensusClient = new OkHttpClient();

    mapper = new ObjectMapper();
  }

  @Test
  public void test() throws IOException {
    final EngineTestCase testCase = mapper.readValue(testCaseFileURI.toURL(), EngineTestCase.class);

    final Call preparePayloadRequest =
        consensusClient.newCall(
            new Request.Builder()
                .url(executionEngine.engineRpcUrl().get())
                .post(RequestBody.create(testCase.getRequest().toString(), MEDIA_TYPE_JSON))
                .build());
    final Response response = preparePayloadRequest.execute();

    assertThat(response.code()).isEqualTo(testCase.getStatusCode());
    assertThat(response.body().string()).isEqualTo(testCase.getResponse().toPrettyString());
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> testCases() throws URISyntaxException {

    final File testCasePath =
        new File(ExecutionEngineAcceptanceTest.class.getResource(TEST_CASE_PATH).toURI());
    final File[] testCasesList = testCasePath.listFiles();

    return Arrays.stream(testCasesList)
        .sorted()
        .map(file -> new Object[] {file.getName(), file.toURI()})
        .collect(Collectors.toList());
  }

  @AfterClass
  public static void tearDown() {
    cluster.close();
  }
}
