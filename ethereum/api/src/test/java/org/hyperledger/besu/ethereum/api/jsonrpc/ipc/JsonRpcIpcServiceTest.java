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
package org.hyperledger.besu.ethereum.api.jsonrpc.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
@ExtendWith(VertxExtension.class)
class JsonRpcIpcServiceTest {

  @TempDir private Path tempDir;
  private Vertx vertx;
  private VertxTestContext testContext;

  @BeforeEach
  public void setUp() {
    vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    testContext = new VertxTestContext();
  }

  @AfterEach
  public void after() throws Throwable {
    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS))
        .describedAs("Test completed on time")
        .isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  @Test
  void successfulExecution() {
    final Path socketPath = tempDir.resolve("besu-test.ipc");
    final JsonRpcMethod testMethod = mock(JsonRpcMethod.class);
    when(testMethod.response(any())).thenReturn(new JsonRpcSuccessResponse(1, "TEST OK"));
    final JsonRpcIpcService service =
        new JsonRpcIpcService(
            vertx,
            socketPath,
            new JsonRpcExecutor(new BaseJsonRpcProcessor(), Map.of("test_method", testMethod)));
    final String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"TEST OK\"}\n";

    assertSocketCall(
        service,
        socketPath,
        expectedResponse,
        new JsonObject().put("id", 1).put("method", "test_method").toBuffer());
  }

  @Test
  void successfulBatchExecution() {
    final Path socketPath = tempDir.resolve("besu-test.ipc");
    final JsonRpcMethod fooMethod = mock(JsonRpcMethod.class);
    when(fooMethod.response(any())).thenReturn(new JsonRpcSuccessResponse(1, "FOO OK"));
    final JsonRpcMethod barMethod = mock(JsonRpcMethod.class);
    when(barMethod.response(any())).thenReturn(new JsonRpcSuccessResponse(2, "BAR OK"));
    final JsonRpcIpcService service =
        new JsonRpcIpcService(
            vertx,
            socketPath,
            new JsonRpcExecutor(
                new BaseJsonRpcProcessor(),
                Map.of("foo_method", fooMethod, "bar_method", barMethod)));

    assertSocketCall(
        service,
        socketPath,
        "[{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"FOO OK\"},{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"BAR OK\"}]\n",
        new JsonArray(
                Arrays.asList(
                    new JsonObject().put("id", 1).put("method", "foo_method"),
                    new JsonObject().put("id", 2).put("method", "bar_method")))
            .toBuffer());
  }

  @Test
  void validJsonButNotRpcShouldReturnInvalidRequest() {
    final Path socketPath = tempDir.resolve("besu-test.ipc");
    final JsonRpcIpcService service =
        new JsonRpcIpcService(
            vertx,
            socketPath,
            new JsonRpcExecutor(new BaseJsonRpcProcessor(), Collections.emptyMap()));
    final String expectedResponse =
        "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Invalid Request\"}}\n";

    assertSocketCall(service, socketPath, expectedResponse, Buffer.buffer("{\"foo\":\"bar\"}"));
  }

  @Test
  void nonJsonRequestShouldReturnParseError() {
    final Path socketPath = tempDir.resolve("besu-test.ipc");
    final JsonRpcIpcService service =
        new JsonRpcIpcService(vertx, socketPath, mock(JsonRpcExecutor.class));
    final String expectedResponse =
        "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}\n";

    assertSocketCall(service, socketPath, expectedResponse, Buffer.buffer("bad request"));
  }

  @Test
  void shouldDeleteSocketFileOnStop() {
    final Path socketPath = tempDir.resolve("besu-test.ipc");
    final JsonRpcIpcService service =
        new JsonRpcIpcService(vertx, socketPath, mock(JsonRpcExecutor.class));
    service
        .start()
        .onComplete(
            testContext.succeeding(
                server ->
                    service
                        .stop()
                        .onComplete(
                            testContext.succeeding(
                                handler ->
                                    testContext.verify(
                                        () -> {
                                          assertThat(socketPath).doesNotExist();
                                          testContext.completeNow();
                                        })))));
  }

  private void assertSocketCall(
      final JsonRpcIpcService service,
      final Path socketPath,
      final String expectedResponse,
      final Buffer request) {
    service
        .start()
        .onComplete(
            testContext.succeeding(
                server ->
                    vertx
                        .createNetClient()
                        .connect(SocketAddress.domainSocketAddress(socketPath.toString()))
                        .onComplete(
                            testContext.succeeding(
                                socket ->
                                    socket
                                        .handler(
                                            buffer ->
                                                testContext.verify(
                                                    () -> {
                                                      assertThat(buffer)
                                                          .hasToString(expectedResponse);
                                                      service
                                                          .stop()
                                                          .onComplete(
                                                              testContext.succeedingThenComplete());
                                                    }))
                                        .write(request)))));
  }
}
