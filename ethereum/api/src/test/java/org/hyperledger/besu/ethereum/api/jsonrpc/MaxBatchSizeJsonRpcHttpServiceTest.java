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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

public class MaxBatchSizeJsonRpcHttpServiceTest extends JsonRpcHttpServiceTestBase {

  private void initMaxBatchSize(final int rpcMaxBatchSize) throws Exception {
    maxBatchSize = rpcMaxBatchSize;
    initServerAndClient();
  }

  @Test
  public void shouldNotReturnErrorWhenConfigIsDisabled() throws Exception {

    // disable batch size
    initMaxBatchSize(-1);

    // Create a batch request with 2 requests
    final RequestBody body =
        RequestBody.create(
            "["
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"}"
                + "]",
            JSON);

    // Should not return error
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      final JsonArray json = new JsonArray(resp.body().string());
      assertThat(json.size()).isEqualTo(2);
    }
  }

  @Test
  public void shouldReturnErrorWhenBatchRequestGreaterThanConfig() throws Exception {

    // set max batch size
    initMaxBatchSize(1);

    // Create a batch request with 2 requests
    final RequestBody body =
        RequestBody.create(
            "["
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"web3_clientVersion\"}"
                + "]",
            JSON);

    // Should return error
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      final JsonObject json = new JsonObject(resp.body().string());
      final RpcErrorType expectedError = RpcErrorType.EXCEEDS_RPC_MAX_BATCH_SIZE;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }
}
