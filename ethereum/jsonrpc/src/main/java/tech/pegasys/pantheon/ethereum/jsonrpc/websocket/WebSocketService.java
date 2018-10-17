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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket;

import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketService {

  private static final Logger LOG = LogManager.getLogger();

  private static final InetSocketAddress EMPTY_SOCKET_ADDRESS = new InetSocketAddress("0.0.0.0", 0);

  private final Vertx vertx;
  private final WebSocketConfiguration configuration;
  private final WebSocketRequestHandler websocketRequestHandler;

  private HttpServer httpServer;

  public WebSocketService(
      final Vertx vertx,
      final WebSocketConfiguration configuration,
      final WebSocketRequestHandler websocketRequestHandler) {
    this.vertx = vertx;
    this.configuration = configuration;
    this.websocketRequestHandler = websocketRequestHandler;
  }

  public CompletableFuture<?> start() {
    LOG.info(
        "Starting Websocket service on {}:{}", configuration.getHost(), configuration.getPort());

    final CompletableFuture<?> resultFuture = new CompletableFuture<>();

    httpServer =
        vertx
            .createHttpServer(
                new HttpServerOptions()
                    .setHost(configuration.getHost())
                    .setPort(configuration.getPort())
                    .setWebsocketSubProtocols("undefined"))
            .websocketHandler(websocketHandler())
            .listen(startHandler(resultFuture));

    return resultFuture;
  }

  private Handler<ServerWebSocket> websocketHandler() {
    return websocket -> {
      final SocketAddress socketAddress = websocket.remoteAddress();
      final String connectionId = websocket.textHandlerID();

      LOG.debug("Websocket Connected ({})", socketAddressAsString(socketAddress));

      websocket.handler(
          buffer -> {
            LOG.debug(
                "Received Websocket request {} ({})",
                buffer.toString(),
                socketAddressAsString(socketAddress));

            websocketRequestHandler.handle(connectionId, buffer);
          });

      websocket.closeHandler(
          v -> {
            LOG.debug("Websocket Disconnected ({})", socketAddressAsString(socketAddress));
            vertx
                .eventBus()
                .publish(SubscriptionManager.EVENTBUS_REMOVE_SUBSCRIPTIONS_ADDRESS, connectionId);
          });

      websocket.exceptionHandler(
          t -> {
            LOG.debug(
                "Unrecoverable error on Websocket: {} ({})",
                t.getMessage(),
                socketAddressAsString(socketAddress));
            websocket.close();
          });
    };
  }

  private Handler<AsyncResult<HttpServer>> startHandler(final CompletableFuture<?> resultFuture) {
    return res -> {
      if (res.succeeded()) {

        LOG.info(
            "Websocket service started and listening on {}:{}",
            configuration.getHost(),
            httpServer.actualPort());

        resultFuture.complete(null);
      } else {
        resultFuture.completeExceptionally(res.cause());
      }
    };
  }

  public CompletableFuture<?> stop() {
    if (httpServer == null) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<?> resultFuture = new CompletableFuture<>();

    httpServer.close(
        res -> {
          if (res.succeeded()) {
            httpServer = null;
            resultFuture.complete(null);
          } else {
            resultFuture.completeExceptionally(res.cause());
          }
        });

    return resultFuture;
  }

  public InetSocketAddress socketAddress() {
    if (httpServer == null) {
      return EMPTY_SOCKET_ADDRESS;
    }
    return new InetSocketAddress(configuration.getHost(), httpServer.actualPort());
  }

  private String socketAddressAsString(final SocketAddress socketAddress) {
    return String.format("host=%s, port=%d", socketAddress.host(), socketAddress.port());
  }
}
