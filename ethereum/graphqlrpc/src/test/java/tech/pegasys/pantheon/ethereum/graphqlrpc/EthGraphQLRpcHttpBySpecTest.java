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
package tech.pegasys.pantheon.ethereum.graphqlrpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EthGraphQLRpcHttpBySpecTest extends AbstractEthGraphQLRpcHttpServiceTest {

  private final String specFileName;

  public EthGraphQLRpcHttpBySpecTest(final String specFileName) {
    this.specFileName = specFileName;
  }

  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final List<String> specs = new ArrayList<>();

    specs.add("eth_blockNumber");
    specs.add("eth_getTransactionByHash");
    specs.add("eth_getTransactionByHashNull");
    specs.add("eth_getBlockByHash");
    specs.add("eth_getBlockByNumber");
    specs.add("eth_getBlockTransactionCountByHash");
    specs.add("eth_getBlockTransactionCountByNumber");
    specs.add("eth_getTransactionByBlockHashAndIndex");
    specs.add("eth_getTransactionByBlockNumberAndIndex");

    specs.add("eth_estimateGas_transfer");
    specs.add("eth_estimateGas_noParams");
    specs.add("eth_estimateGas_contractDeploy");

    specs.add("eth_getCode");
    specs.add("eth_getCode_noCode");

    specs.add("eth_getStorageAt");
    specs.add("eth_getStorageAt_illegalRangeGreaterThan");

    specs.add("eth_getTransactionCount");

    specs.add("eth_getTransactionByBlockNumberAndInvalidIndex");

    specs.add("eth_getBlocksByRange");
    specs.add("eth_call_Block8");
    specs.add("eth_call_BlockLatest");
    specs.add("eth_getBalance_latest");
    specs.add("eth_getBalance_0x19");
    specs.add("eth_gasPrice");

    specs.add("eth_getTransactionReceipt");

    specs.add("eth_syncing");
    specs.add("eth_sendRawTransaction_contractCreation");

    specs.add("eth_sendRawTransaction_messageCall");
    specs.add("eth_sendRawTransaction_transferEther");
    specs.add("eth_sendRawTransaction_unsignedTransaction");

    specs.add("eth_getLogs_matchTopic");
    return specs;
  }

  @Test
  public void graphQLRPCCallWithSpecFile() throws Exception {
    graphQLRPCCall(specFileName);
  }

  private void graphQLRPCCall(final String name) throws IOException {
    final String testSpecFile = name + ".json";
    final String json =
        Resources.toString(
            EthGraphQLRpcHttpBySpecTest.class.getResource(testSpecFile), Charsets.UTF_8);
    final JsonObject spec = new JsonObject(json);
    final String rawRequestBody = spec.getString("request");
    final RequestBody requestBody = RequestBody.create(JSON, rawRequestBody);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    importBlocks(1, BLOCKS.size());
    try (final Response resp = client.newCall(request).execute()) {
      final int expectedStatusCode = spec.getInteger("statusCode");
      assertThat(resp.code()).isEqualTo(expectedStatusCode);

      final JsonObject expectedRespBody = spec.getJsonObject("response");
      final String resultStr = resp.body().string();

      final JsonObject result = new JsonObject(resultStr);
      assertThat(result).isEqualTo(expectedRespBody);
    }
  }

  private void importBlocks(final int from, final int to) {
    for (int i = from; i < to; ++i) {
      importBlock(i);
    }
  }
}
