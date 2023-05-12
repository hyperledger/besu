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

package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;

import java.io.IOException;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;

public class MaxResourceIntensePerBatchJsonRpcHttpServiceTest extends JsonRpcHttpServiceTestBase {

  private void initMaxResourceIntensivePerBatchSize(final int rpcResourceIntensiveMaxBatchSize)
      throws Exception {
    maxResourceIntensivePerBatchSize = rpcResourceIntensiveMaxBatchSize;
    resourceIntenseMethods = List.of("resourceIntensiveMethod");
    initServerAndClient();
  }

  @Test
  public void shouldOnlyExecuteOneResourceRequestOPerBatchWhenConfigIsEnabled() throws Exception {
    // Set up the limit resource-intensive methods per batch
    final int resourceIntensiveLimit = 1;
    initMaxResourceIntensivePerBatchSize(resourceIntensiveLimit);

    final RequestBody body = createRequestBody();

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      final JsonArray json = parseResponse(resp);

      // Assert that the first non-resource-intensive request executed successfully (it should not
      // contain an error).
      assertThat(json.getJsonObject(0).getString("error")).isNull();

      // Assert that the first resource-intensive request executed successfully.
      // As per our configuration, only one resource-intensive request should be allowed per batch,
      // so it should not exceed the limit.
      assertThat(json.getJsonObject(1).getJsonObject("error").getInteger("code"))
          .isNotEqualTo(JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE.getCode());

      // Assert that the second non-resource-intensive request executed successfully (it should not
      // contain an error).
      assertThat(json.getJsonObject(2).getString("error")).isNull();

      // Assert that the second resource-intensive request failed due to exceeding the limit of
      // resource-intensive requests per batch.
      assertThat(json.getJsonObject(3).getJsonObject("error").getInteger("code"))
          .isEqualTo(JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE.getCode());

      // Assert that the third non-resource-intensive request executed successfully (it should not
      // contain an error).
      assertThat(json.getJsonObject(4).getString("error")).isNull();
    }
  }

  @Test
  public void shouldOnlyExecuteOneResourceRequestOPerBatchWhenConfigIsDisabled() throws Exception {
    // disable resource-intensive methods per batch
    final int resourceIntensiveLimit = -1;
    initMaxResourceIntensivePerBatchSize(resourceIntensiveLimit);

    final RequestBody body = createRequestBody();

    // Execute the batch request and parse the response as a JSON array.
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      final JsonArray json = parseResponse(resp);

      // Iterate over the responses. Since we've disabled the limit on resource-intensive methods
      // per batch,
      // none of the requests should fail due to exceeding the batch size limit (i.e., no error
      // should have the code EXCEEDS_RPC_MAX_BATCH_SIZE).
      for (int i = 0; i < json.size(); i++) {
        JsonObject jsonObject = json.getJsonObject(i);
        if (jsonObject.containsKey("error")) {
          assertThat(jsonObject.getJsonObject("error").getInteger("code"))
              .isNotEqualTo(JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE.getCode());
        }
      }
    }
  }

  // Create a batch request containing two types of requests: one resource-intensive and one
  // non-resource-intensive.
  // The batch request is structured as a JSON array containing five requests in total.

  private RequestBody createRequestBody() {
    return RequestBody.create(
        "["
            // 1 - Non-resource-intensive request
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"},"
            // 2 - Resource-intensive request
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resourceIntensiveMethod\"},"
            // 3 - Non-resource-intensive request
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"},"
            // 4 - Resource-intensive request
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resourceIntensiveMethod\"},"
            // 5 - Non-resource-intensive request
            + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"}"
            + "]",
        JSON);
  }

  private JsonArray parseResponse(final Response resp) throws IOException {
    return new JsonArray(resp.body().string());
  }
}
