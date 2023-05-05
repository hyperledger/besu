/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateTrieAccountValue;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ZkTrieLogObserverTest {

  private Vertx vertx;
  private HttpServer server;

  private int rpcServicePort;
  private static final BlockHeader headerFixture = new BlockHeaderTestFixture().buildHeader();
  private static final String JSON_SUCCESS_RESPONSE =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"accepted\"}";

  private static final ZkTrieLogFactoryImpl zkTrieLogFactory = new ZkTrieLogFactoryImpl();
  private static final TrieLogLayer trieLogFixture =
      new TrieLogLayer()
          .addAccountChange(
              Address.ZERO,
              null,
              new StateTrieAccountValue(1, Wei.fromEth(1), Hash.ZERO, Hash.ZERO))
          .setBlockHash(Hash.ZERO)
          .setBlockNumber(1337L);

  // should be provided by test method:
  private Consumer<HttpServerRequest> requestVerifier = null;

  @Before
  public void setUp(final TestContext context) {
    vertx = Vertx.vertx();

    // Create a router and set up a route to handle JSON-RPC requests
    Router router = Router.router(vertx);
    router.post("/").handler(z -> handleJsonRpcRequest(z, requestVerifier));

    // Start the HTTP server on a random available port
    Async async = context.async();
    server =
        vertx
            .createHttpServer()
            .requestHandler(router)
            .listen(
                0,
                ar -> {
                  if (ar.succeeded()) {
                    rpcServicePort = server.actualPort();
                    async.complete();
                  } else {
                    context.fail(ar.cause());
                  }
                });
  }

  @After
  public void tearDown(final TestContext context) {
    server.close(context.asyncAssertSuccess());
    vertx.close(context.asyncAssertSuccess());
  }

  private void handleJsonRpcRequest(
      final RoutingContext context, final Consumer<HttpServerRequest> requestHandler) {
    if (requestHandler != null) {
      requestHandler.accept(context.request());
    }
    context
        .response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(JSON_SUCCESS_RESPONSE);
  }

  @Test
  public void testSendToZk(final TestContext context) {
    Async async = context.async();
    Async async2 = context.async();

    this.requestVerifier =
        req -> {
          req.bodyHandler(
              body -> {
                var params = body.toJsonObject().getJsonArray("params").getJsonObject(0);

                context.assertEquals(
                    params.getString("trieLog"),
                    Bytes.wrap(zkTrieLogFactory.serialize(trieLogFixture)).toHexString());
                context.assertEquals(
                    params.getString("blockHash"), headerFixture.getBlockHash().toHexString());
                context.assertEquals(params.getLong("blockNumber"), headerFixture.getNumber());
                async2.complete();
              });
        };
    ZkTrieLogObserver observer = new ZkTrieLogObserver("localhost", rpcServicePort);
    TrieLogAddedEvent addEvent = new TrieLogAddedEvent(headerFixture, trieLogFixture);

    observer
        .handleShip(addEvent)
        .onComplete(
            context.asyncAssertSuccess(
                response -> {
                  context.assertEquals(200, response.statusCode());
                  // assert response
                  context.assertEquals(response.bodyAsJsonObject().getString("result"), "accepted");
                  async.complete();
                }));
  }
}
