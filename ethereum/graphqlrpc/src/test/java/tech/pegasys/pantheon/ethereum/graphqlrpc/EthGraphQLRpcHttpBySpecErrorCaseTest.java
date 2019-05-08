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
public class EthGraphQLRpcHttpBySpecErrorCaseTest extends AbstractEthGraphQLRpcHttpServiceTest {

  private final String specFileName;

  public EthGraphQLRpcHttpBySpecErrorCaseTest(final String specFileName) {
    this.specFileName = specFileName;
  }

  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final List<String> specs = new ArrayList<>();
    specs.add("eth_getBlockWrongParams");
    specs.add("eth_getBlocksByWrongRange");
    specs.add("eth_getBalance_toobig_bn");
    specs.add("eth_getBalance_without_addr");

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
      final String resultStr = resp.body().string();

      assertThat(resp.code()).isEqualTo(expectedStatusCode);
      try {
        final JsonObject expectedRespBody = spec.getJsonObject("response");
        final JsonObject result = new JsonObject(resultStr);
        if (expectedRespBody != null) {
          assertThat(result).isEqualTo(expectedRespBody);
        }
      } catch (final IllegalStateException ignored) {
      }
    }
  }

  private void importBlocks(final int from, final int to) {
    for (int i = from; i < to; ++i) {
      importBlock(i);
    }
  }
}
