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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.PluginJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.plugin.services.exception.PluginRpcEndpointException;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

import java.util.Locale;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PluginJsonRpcMethodTest extends JsonRpcHttpServiceTestBase {

  @BeforeAll
  public static void setup() throws Exception {
    initServerAndClient();
  }

  /** Tears down the HTTP server. */
  @AfterAll
  public static void shutdownServer() {
    service.stop().join();
  }

  @Test
  public void happyPath() throws Exception {
    final var request =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":["hello"]}""";

    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(request, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(200);
        final JsonObject json = new JsonObject(resp.body().string());
        testHelper.assertValidJsonRpcResult(json, 1);
        assertThat(json.getString("result")).isEqualTo("hello");
      }
    }
  }

  @Test
  public void invalidJsonShouldReturnParseError() throws Exception {
    final var malformedRequest =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":}""";

    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(malformedRequest, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(400);
        final JsonObject json = new JsonObject(resp.body().string());
        final JsonRpcError expectedError = new JsonRpcError(RpcErrorType.PARSE_ERROR);
        testHelper.assertValidJsonRpcError(
            json, null, expectedError.getCode(), expectedError.getMessage());
      }
    }
  }

  @Test
  public void invalidParamsShouldReturnInvalidParams() throws Exception {
    final var missingRequiredParam =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":[]}""";
    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(missingRequiredParam, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(200);
        final JsonObject json = new JsonObject(resp.body().string());
        final JsonRpcError expectedError = new JsonRpcError(RpcErrorType.INVALID_PARAM_COUNT);
        testHelper.assertValidJsonRpcError(
            json, 1, expectedError.getCode(), expectedError.getMessage());
      }
    }
  }

  @Test
  public void methodErrorShouldReturnErrorResponse() throws Exception {
    final var wrongParamContent =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":[" "]}""";
    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(wrongParamContent, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(200);
        final JsonObject json = new JsonObject(resp.body().string());
        testHelper.assertValidJsonRpcError(json, 1, -1, "Blank input not allowed");
      }
    }
  }

  @Test
  public void methodErrorWithDataShouldReturnErrorResponseWithDecodedData() throws Exception {
    final var wrongParamContent =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":["data"]}""";
    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(wrongParamContent, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(200);
        final JsonObject json = new JsonObject(resp.body().string());
        testHelper.assertValidJsonRpcError(json, 1, -2, "Error with data (ABC)", "abc");
      }
    }
  }

  @Test
  public void unhandledExceptionShouldReturnInternalErrorResponse() throws Exception {
    final var nullParam =
        """
        {"jsonrpc":"2.0","id":1,"method":"plugin_echo","params":[null]}""";
    try (var unused =
        addRpcMethod(
            "plugin_echo",
            new PluginJsonRpcMethod("plugin_echo", PluginJsonRpcMethodTest::echoPluginRpcMethod))) {
      final RequestBody body = RequestBody.create(nullParam, JSON);

      try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
        assertThat(resp.code()).isEqualTo(200);
        final JsonObject json = new JsonObject(resp.body().string());
        final JsonRpcError expectedError = new JsonRpcError(RpcErrorType.INTERNAL_ERROR);
        testHelper.assertValidJsonRpcError(
            json, 1, expectedError.getCode(), expectedError.getMessage());
      }
    }
  }

  private static Object echoPluginRpcMethod(final PluginRpcRequest request) {
    final var params = request.getParams();
    if (params.length == 0) {
      throw new InvalidJsonRpcParameters(
          "parameter is mandatory", RpcErrorType.INVALID_PARAM_COUNT);
    }
    final var input = params[0];
    if (input.toString().isBlank()) {
      throw new PluginRpcEndpointException(
          new RpcMethodError() {
            @Override
            public int getCode() {
              return -1;
            }

            @Override
            public String getMessage() {
              return "Blank input not allowed";
            }
          });
    }

    if (input.toString().equals("data")) {
      throw new PluginRpcEndpointException(
          new RpcMethodError() {
            @Override
            public int getCode() {
              return -2;
            }

            @Override
            public String getMessage() {
              return "Error with data";
            }

            @Override
            public Optional<String> decodeData(final String data) {
              // just turn everything uppercase
              return Optional.of(data.toUpperCase(Locale.US));
            }
          },
          "abc");
    }

    return input;
  }
}
