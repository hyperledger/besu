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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.tests.acceptance.dsl.rpc.JsonRpcTestCase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExecutionEngineCancunBlockBulidingAcceptanceTest extends AbstractJsonRpcTest {
  private static final String GENESIS_FILE = "/jsonrpc/engine/cancun/genesis.json";
  private static final String TEST_CASE_PATH = "/jsonrpc/engine/cancun/test-cases/block-production";

  private static JsonRpcTestsContext testsContext;

  public ExecutionEngineCancunBlockBulidingAcceptanceTest(
      final String ignored, final URI testCaseFileURI) {
    super(ignored, testsContext, testCaseFileURI);
  }

  @BeforeClass
  public static void init() throws IOException {
    testsContext = new JsonRpcTestsContext(GENESIS_FILE);
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> testCases() throws URISyntaxException {
    return testCases(TEST_CASE_PATH);
  }

  @Override
  protected void evaluateResponse(
      final ObjectNode responseBody,
      final Call testRequest,
      final JsonRpcTestCase testCase,
      final URL url) {
    if (url.toString().endsWith("10_cancun_build_on_genesis.json")) {
      // if we just asked the node to build, give it some time to build
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    if (url.toString().endsWith("12_cancun_get_built_block.json")) {

        //final ObjectNode rpcResponse = JsonUtil.objectNodeFromString(response.body().string());
        final ObjectNode result = (ObjectNode) responseBody.get("result");
        final ObjectNode execPayload = (ObjectNode) result.get("executionPayload");
        final ObjectNode blobsBundle = (ObjectNode) result.get("blobsBundle");
        assertThat(execPayload.get("transactions").getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        final ArrayNode transactions = (ArrayNode) execPayload.get("transactions");
        // actually, you need to decode the transactions and count how many unique
        // versioned hashes are referenced amongst them.
        assertThat(blobsBundle.get("commitments").getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        final ArrayNode commitments = (ArrayNode) blobsBundle.get("commitments");
        assertThat(blobsBundle.get("blobs").getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        final ArrayNode blobs = (ArrayNode) blobsBundle.get("blobs");
        final ArrayNode proofs = (ArrayNode) blobsBundle.get("proofs");
        assertThat(2).isEqualTo(transactions.size());
        assertThat(6).isEqualTo(commitments.size());
        assertThat(6).isEqualTo(blobs.size());
        assertThat(6).isEqualTo(proofs.size());

    }
  }

  @AfterClass
  public static void tearDown() {
    testsContext.cluster.close();
  }
}
