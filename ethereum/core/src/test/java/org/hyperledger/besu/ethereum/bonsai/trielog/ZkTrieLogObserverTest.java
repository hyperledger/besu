package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.Base64;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ZkTrieLogObserverTest {

  private Vertx vertx;
  private HttpServer server;

  private int rpcServicePort;
  private static final String JSON_SUCCESS_RESPONSE =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"accepted\"}";
  private static final ZkTrieLogFactoryImpl zkTrieLogFactory = new ZkTrieLogFactoryImpl();
  private static final TrieLogLayer trieLogFixture =
      new TrieLogLayer()
          .addAccountChange(
              Address.ZERO,
              null,
              new StateTrieAccountValue(1, Wei.fromEth(1), Hash.ZERO, Hash.ZERO))
          .setBlockHash(Hash.ZERO);

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
    Async async = context.async(2);

    this.requestVerifier =
        req -> {
          req.bodyHandler(
              body -> {
                var params = body.toJsonObject().getJsonObject("params");

                context.assertEquals(
                    params.getJsonObject("trieLog").encode(),
                    Base64.getEncoder().encode(zkTrieLogFactory.serialize(trieLogFixture)));

                context.assertEquals(
                    params.getJsonObject("blockHash").encode(), Hash.ZERO.toHexString());
                async.complete();
              });
        };
    ZkTrieLogObserver observer = new ZkTrieLogObserver("localhost", rpcServicePort);
    TrieLogAddedEvent addEvent = new TrieLogAddedEvent(Hash.ZERO, trieLogFixture);

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
