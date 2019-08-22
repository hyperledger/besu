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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class AbstractJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpServiceTest {

  private final String specFileName;

  public AbstractJsonRpcHttpBySpecTest(final String specFileName) {
    this.specFileName = specFileName;
  }

  @Test
  public void jsonRPCCallWithSpecFile() throws Exception {
    jsonRPCCall(specFileName);
  }

  private void jsonRPCCall(final String name) throws IOException {
    final String testSpecFile = name + ".json";
    final String json =
        Resources.toString(
            AbstractJsonRpcHttpBySpecTest.class.getResource(testSpecFile), Charsets.UTF_8);
    final JsonObject spec = new JsonObject(json);

    final String rawRequestBody = spec.getJsonObject("request").toString();
    final RequestBody requestBody = RequestBody.create(JSON, rawRequestBody);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    try (final Response resp = client.newCall(request).execute()) {
      final int expectedStatusCode = spec.getInteger("statusCode");
      assertThat(resp.code()).isEqualTo(expectedStatusCode);

      final String expectedRespBody = spec.getJsonObject("response").encodePrettily();
      assertThat(resp.body().string()).isEqualTo(expectedRespBody);
    }
  }
}
