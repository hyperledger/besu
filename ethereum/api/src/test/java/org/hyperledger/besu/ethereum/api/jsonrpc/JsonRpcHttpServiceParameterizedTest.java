/*
 * Copyright Hyperledger Besu contributors
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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;

import java.util.Arrays;
import java.util.Collection;

import io.vertx.core.json.JsonObject;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JsonRpcHttpServiceParameterizedTest extends JsonRpcHttpServiceTestBase {

  private final String requestJson;

  public JsonRpcHttpServiceParameterizedTest(final String requestJson) {
    this.requestJson = requestJson;
  }

  @BeforeClass
  public static void setup() throws Exception {
    initServerAndClient();
  }

  /** Tears down the HTTP server. */
  @AfterClass
  public static void shutdownServer() {
    service.stop().join();
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{"\"a string\""}, {"a string"}, {"{bla"}, {""}});
  }

  @Test
  public void invalidJsonShouldReturnParseError() throws Exception {
    final RequestBody body = RequestBody.create(JSON, requestJson);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.PARSE_ERROR;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }
}
