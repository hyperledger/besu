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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static com.google.common.collect.Streams.stream;

import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketService {

  private static final Logger LOG = LogManager.getLogger();

  private static final InetSocketAddress EMPTY_SOCKET_ADDRESS = new InetSocketAddress("0.0.0.0", 0);
  private static final String APPLICATION_JSON = "application/json";

  private final int maxActiveConnections;
  private final AtomicInteger activeConnectionsCount = new AtomicInteger();

  private final Vertx vertx;
  private final WebSocketConfiguration configuration;
  private final WebSocketRequestHandler websocketRequestHandler;

  private HttpServer httpServer;

  @VisibleForTesting public final Optional<AuthenticationService> authenticationService;

  public WebSocketService(
      final Vertx vertx,
      final WebSocketConfiguration configuration,
      final WebSocketRequestHandler websocketRequestHandler) {
    this(
        vertx,
        configuration,
        websocketRequestHandler,
        AuthenticationService.create(vertx, configuration));
  }

  private WebSocketService(
      final Vertx vertx,
      final WebSocketConfiguration configuration,
      final WebSocketRequestHandler websocketRequestHandler,
      final Optional<AuthenticationService> authenticationService) {
    this.vertx = vertx;
    this.configuration = configuration;
    this.websocketRequestHandler = websocketRequestHandler;
    this.authenticationService = authenticationService;
    this.maxActiveConnections = configuration.getMaxActiveConnections();
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
                    .setHandle100ContinueAutomatically(true)
                    .setCompressionSupported(true)
                    .addWebSocketSubProtocol("undefined"))
            .webSocketHandler(websocketHandler())
            .connectionHandler(connectionHandler())
            .requestHandler(httpHandler())
            .listen(startHandler(resultFuture));

    return resultFuture;
  }

  private Handler<ServerWebSocket> websocketHandler() {
    return websocket -> {
      final SocketAddress socketAddress = websocket.remoteAddress();
      final String connectionId = websocket.textHandlerID();
      final String token = getAuthToken(websocket);
      if (token != null) {
        LOG.trace("Websocket authentication token {}", token);
      }

      if (!hasAllowedHostnameHeader(Optional.ofNullable(websocket.headers().get("Host")))) {
        websocket.reject(403);
      }

      LOG.debug("Websocket Connected ({})", socketAddressAsString(socketAddress));

      websocket.binaryMessageHandler(
          buffer -> {
            LOG.debug(
                "Received Websocket request (binary frame) {} ({})",
                buffer.toString(),
                socketAddressAsString(socketAddress));

            AuthenticationUtils.getUser(
                authenticationService,
                token,
                user ->
                    websocketRequestHandler.handle(
                        authenticationService, websocket, buffer.toString(), user));
          });

      websocket.textMessageHandler(
          payload -> {
            LOG.debug(
                "Received Websocket request {} ({})",
                payload,
                socketAddressAsString(socketAddress));

            AuthenticationUtils.getUser(
                authenticationService,
                token,
                user ->
                    websocketRequestHandler.handle(
                        authenticationService, websocket, payload, user));
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

  private Handler<HttpConnection> connectionHandler() {

    return connection -> {
      if (activeConnectionsCount.get() >= maxActiveConnections) {
        // disallow new connections to prevent DoS
        LOG.warn(
            "Rejecting new connection from {}. {}/{} max active connections limit reached.",
            connection.remoteAddress(),
            activeConnectionsCount.getAndIncrement(),
            maxActiveConnections);
        connection.close();
      } else {
        LOG.debug(
            "Opened connection from {}. Total of active connections: {}/{}",
            connection.remoteAddress(),
            activeConnectionsCount.incrementAndGet(),
            maxActiveConnections);
      }
      connection.closeHandler(
          c ->
              LOG.debug(
                  "Connection closed from {}. Total of active connections: {}/{}",
                  connection.remoteAddress(),
                  activeConnectionsCount.decrementAndGet(),
                  maxActiveConnections));
    };
  }

  private Handler<HttpServerRequest> httpHandler() {
    final Router router = Router.router(vertx);

    // Verify Host header to avoid rebind attack.
    router.route().handler(checkAllowlistHostHeader());

    if (authenticationService.isPresent()) {
      router.route("/login").handler(BodyHandler.create());
      router
          .post("/login")
          .produces(APPLICATION_JSON)
          .handler(authenticationService.get()::handleLogin);
    } else {
      router
          .post("/login")
          .produces(APPLICATION_JSON)
          .handler(AuthenticationService::handleDisabledLogin);
    }

    router.route().handler(WebSocketService::handleHttpNotSupported);
    return router;
  }

  private static void handleHttpNotSupported(final RoutingContext http) {
    final HttpServerResponse response = http.response();
    if (!response.closed()) {
      response.setStatusCode(400).end("Websocket endpoint can't handle HTTP requests");
    }
  }

  private Handler<AsyncResult<HttpServer>> startHandler(final CompletableFuture<?> resultFuture) {
    return res -> {
      if (res.succeeded()) {

        final int actualPort = res.result().actualPort();
        LOG.info(
            "Websocket service started and listening on {}:{}",
            configuration.getHost(),
            actualPort);
        configuration.setPort(actualPort);
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

  private String getAuthToken(final ServerWebSocket websocket) {
    return AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(
        websocket.headers().get("Authorization"));
  }

  private Handler<RoutingContext> checkAllowlistHostHeader() {
    return event -> {
      if (hasAllowedHostnameHeader(Optional.ofNullable(event.request().host()))) {
        event.next();
      } else {
        final HttpServerResponse response = event.response();
        if (!response.closed()) {
          response
              .setStatusCode(403)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end("{\"message\":\"Host not authorized.\"}");
        }
      }
    };
  }

  @VisibleForTesting
  public boolean hasAllowedHostnameHeader(final Optional<String> header) {
    return configuration.getHostsAllowlist().contains("*")
        || header.map(value -> checkHostInAllowlist(validateHostHeader(value))).orElse(false);
  }

  private Optional<String> validateHostHeader(final String header) {
    final Iterable<String> splitHostHeader = Splitter.on(':').split(header);
    final long hostPieces = stream(splitHostHeader).count();
    if (hostPieces > 1) {
      // If the host contains a colon, verify the host is correctly formed - host [ ":" port ]
      if (hostPieces > 2 || !Iterables.get(splitHostHeader, 1).matches("\\d{1,5}+")) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(Iterables.get(splitHostHeader, 0));
  }

  private boolean checkHostInAllowlist(final Optional<String> hostHeader) {
    return hostHeader
        .map(
            header ->
                configuration.getHostsAllowlist().stream()
                    .anyMatch(
                        allowlistEntry ->
                            allowlistEntry.toLowerCase().equals(header.toLowerCase())))
        .orElse(false);
  }
}
