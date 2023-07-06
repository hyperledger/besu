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
package org.hyperledger.besu.ethereum.stratum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.nio.charset.StandardCharsets;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
public class StratumServerTest {

  public static final Buffer DEFAULT_TEST_MESSAGE =
      new JsonObject()
          .put("version", "1.0")
          .put("id", 1)
          .put("method", RpcMethod.ETH_GET_WORK.getMethodName())
          .toBuffer();
  @Mock private PoWMiningCoordinator mockPoW;

  @Test
  public void runJobSetDifficultyShouldSucceed() {
    StubMetricsSystem metrics = new StubMetricsSystem();
    stratumServerWithMocks(metrics).newJob(new PoWSolverInputs(UInt256.MAX_VALUE, Bytes.EMPTY, 1));
    assertThat(metrics.getGaugeValue("difficulty")).isEqualTo(1d);
  }

  @Test
  public void runJobSetZeroDifficultyShouldNotThrow() {
    StubMetricsSystem metrics = new StubMetricsSystem();
    stratumServerWithMocks(metrics).newJob(new PoWSolverInputs(UInt256.MIN_VALUE, Bytes.EMPTY, 1));

    assertThat(metrics.getGaugeValue("difficulty"))
        .isEqualTo(UInt256.MAX_VALUE.toUnsignedBigInteger().doubleValue());
  }

  @Test
  public void handleHttpChunkedPost(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server ->
                    vertx
                        .fileSystem()
                        .open("rpc-request.json", new OpenOptions())
                        .onComplete(
                            testContext.succeeding(
                                file -> {
                                  WebClient http =
                                      WebClient.create(
                                          vertx,
                                          new WebClientOptions()
                                              .setDefaultHost(host)
                                              .setDefaultPort(server.actualPort()));
                                  http.post("/")
                                      .sendStream(
                                          file,
                                          testContext.succeeding(
                                              resp -> {
                                                testContext.verify(
                                                    () -> {
                                                      assertThat(resp.statusCode()).isEqualTo(200);
                                                      assertThat(resp.bodyAsString())
                                                          .contains(
                                                              "\"jsonrpc\":\"2.0\",\"id\":1,\"result\"");
                                                      stratum
                                                          .stop()
                                                          .onComplete(
                                                              testContext.succeedingThenComplete());
                                                    });
                                              }));
                                }))));
  }

  @Test
  public void rejectNotHttpPost(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  WebClient http =
                      WebClient.create(
                          vertx,
                          new WebClientOptions()
                              .setDefaultHost(host)
                              .setDefaultPort(server.actualPort()));
                  http.put("/")
                      .sendBuffer(DEFAULT_TEST_MESSAGE)
                      .onComplete(
                          testContext.succeeding(
                              resp -> {
                                testContext.verify(
                                    () -> {
                                      assertThat(resp.statusCode()).isEqualTo(405);
                                      stratum
                                          .stop()
                                          .onComplete(testContext.succeedingThenComplete());
                                    });
                              }));
                }));
  }

  @Test
  public void handlePlainText(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  NetClient client = vertx.createNetClient();
                  client
                      .connect(server.actualPort(), host)
                      .onComplete(
                          testContext.succeeding(
                              socket ->
                                  socket
                                      .handler(
                                          resp ->
                                              testContext.verify(
                                                  () -> {
                                                    assertThat(resp.length()).isNotZero();
                                                    assertThat(resp.toString())
                                                        .contains(
                                                            "\"jsonrpc\":\"2.0\",\"id\":1,\"result\"");
                                                    stratum
                                                        .stop()
                                                        .onComplete(
                                                            testContext.succeedingThenComplete());
                                                  }))
                                      .write(
                                          DEFAULT_TEST_MESSAGE.appendString(System.lineSeparator()))
                                      .onComplete(
                                          writeResult ->
                                              testContext.verify(
                                                  () ->
                                                      assertThat(writeResult.failed())
                                                          .isFalse()))));
                }));
  }

  @Test
  public void handlePlainTextMultiple(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  NetClient client = vertx.createNetClient();
                  client
                      .connect(server.actualPort(), host)
                      .onComplete(
                          testContext.succeeding(
                              socket -> {
                                socket.handler(
                                    resp -> {
                                      testContext.verify(
                                          () -> {
                                            assertThat(resp.length()).isNotZero();
                                            String[] responses =
                                                resp.toString(StandardCharsets.UTF_8).split("\n");
                                            assertThat(responses).hasSize(2);
                                            assertThat(responses)
                                                .containsOnly(
                                                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
                                            stratum
                                                .stop()
                                                .onComplete(testContext.succeedingThenComplete());
                                          });
                                    });
                                socket
                                    .write(
                                        DEFAULT_TEST_MESSAGE
                                            .copy()
                                            .appendString(System.lineSeparator())
                                            .appendBuffer(DEFAULT_TEST_MESSAGE)
                                            .appendString(System.lineSeparator()))
                                    .onComplete(
                                        writeResult ->
                                            testContext.verify(
                                                () -> assertThat(writeResult.failed()).isFalse()));
                              }));
                }));
  }

  @Test
  public void handleHttpPost(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  WebClient http =
                      WebClient.create(
                          vertx,
                          new WebClientOptions()
                              .setDefaultHost(host)
                              .setDefaultPort(server.actualPort()));
                  http.post("/")
                      .putHeader("Connection", "keep-alive")
                      .putHeader("Accept-Encoding", "gzip, deflate")
                      .sendBuffer(DEFAULT_TEST_MESSAGE)
                      .onComplete(
                          testContext.succeeding(
                              resp -> {
                                testContext.verify(
                                    () -> {
                                      assertThat(resp.statusCode()).isEqualTo(200);
                                      assertThat(resp.bodyAsString())
                                          .contains("\"jsonrpc\":\"2.0\",\"id\":1,\"result\"");
                                      stratum
                                          .stop()
                                          .onComplete(testContext.succeedingThenComplete());
                                    });
                              }));
                }));
  }

  @Test
  public void handleHttpBadJson(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  WebClient http =
                      WebClient.create(
                          vertx,
                          new WebClientOptions()
                              .setDefaultHost(host)
                              .setDefaultPort(server.actualPort()));
                  http.post("/")
                      .sendBuffer(Buffer.buffer("{\"jsonrpc\":\"2.0\",..."))
                      .onComplete(
                          testContext.succeeding(
                              resp -> {
                                testContext.verify(
                                    () -> {
                                      assertThat(resp.statusCode()).isEqualTo(400);
                                      stratum
                                          .stop()
                                          .onComplete(testContext.succeedingThenComplete());
                                    });
                              }));
                }));
  }

  @Test
  public void handleHttpBadJsonRpc(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  WebClient http =
                      WebClient.create(
                          vertx,
                          new WebClientOptions()
                              .setDefaultHost(host)
                              .setDefaultPort(server.actualPort()));
                  http.post("/")
                      .sendBuffer(
                          Buffer.buffer(
                              "{\"jsonrpc\":\"2.0\",\"id\":one,\"method\":\"eth_getWork\"}"))
                      .onComplete(
                          testContext.succeeding(
                              resp -> {
                                testContext.verify(
                                    () -> {
                                      assertThat(resp.statusCode()).isEqualTo(400);
                                      stratum
                                          .stop()
                                          .onComplete(testContext.succeedingThenComplete());
                                    });
                              }));
                }));
  }

  @Test
  public void handleHttpUnknownMethod(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  WebClient http =
                      WebClient.create(
                          vertx,
                          new WebClientOptions()
                              .setDefaultHost(host)
                              .setDefaultPort(server.actualPort()));
                  http.post("/")
                      .sendBuffer(
                          Buffer.buffer(
                              "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown_method\"}"))
                      .onComplete(
                          testContext.succeeding(
                              resp -> {
                                testContext.verify(
                                    () -> {
                                      assertThat(resp.statusCode()).isEqualTo(400);
                                      stratum
                                          .stop()
                                          .onComplete(testContext.succeedingThenComplete());
                                    });
                              }));
                }));
  }

  @Test
  public void handlePlainTextBadMessage(final Vertx vertx, final VertxTestContext testContext) {
    String host = "localhost";
    StratumServer stratum = new StratumServer(vertx, mockPoW, 0, host, "", new NoOpMetricsSystem());
    stratum
        .start()
        .onComplete(
            testContext.succeeding(
                server -> {
                  NetClient client = vertx.createNetClient();
                  client
                      .connect(server.actualPort(), host)
                      .onComplete(
                          testContext.succeeding(
                              socket ->
                                  socket
                                      .closeHandler(v -> testContext.completeNow())
                                      .write(
                                          Buffer.buffer(
                                              "{\"jsonrpc\":\"2.0\",..." + System.lineSeparator()))
                                      .onComplete(
                                          writeResult ->
                                              testContext.verify(
                                                  () ->
                                                      assertThat(writeResult.failed())
                                                          .isFalse()))));
                }));
  }

  private StratumServer stratumServerWithMocks(final ObservableMetricsSystem metrics) {
    when(mockPoW.getEpochCalculator()).thenReturn(new EpochCalculator.DefaultEpochCalculator());

    StratumServer ss =
        new StratumServer(null, mockPoW, 0, "lo", "", metrics) {
          @Override
          public Future<NetServer> start() {
            this.started.set(true);
            return null;
          }
        };
    ss.start();
    return ss;
  }
}
