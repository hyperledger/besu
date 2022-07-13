/*
 * Copyright Hyperledger Besu contributors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Streams.stream;
import static org.apache.tuweni.net.tls.VertxTrustOptions.allowlistClients;

import org.hyperledger.besu.ethereum.api.handlers.HandlerFactory;
import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.DefaultAuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.AuthenticatedJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.TimedJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.TracedJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketMessageHandler;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.tls.TlsClientAuthConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.util.ExceptionUtils;
import org.hyperledger.besu.util.NetworkUtility;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcService {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcService.class);

  private static final String SPAN_CONTEXT = "span_context";
  private static final InetSocketAddress EMPTY_SOCKET_ADDRESS = new InetSocketAddress("0.0.0.0", 0);
  private static final String APPLICATION_JSON = "application/json";

  private static final TextMapPropagator traceFormats =
      TextMapPropagator.composite(
          JaegerPropagator.getInstance(),
          B3Propagator.injectingSingleHeader(),
          W3CBaggagePropagator.getInstance());

  private static final TextMapGetter<HttpServerRequest> requestAttributesGetter =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(final HttpServerRequest carrier) {
          return carrier.headers().names();
        }

        @Nullable
        @Override
        public String get(final @Nullable HttpServerRequest carrier, final String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.headers().get(key);
        }
      };

  private final Vertx vertx;
  private final JsonRpcConfiguration config;
  private final Map<String, JsonRpcMethod> rpcMethods;
  private final NatService natService;
  private final Path dataDir;
  private final LabelledMetric<OperationTimer> requestTimer;
  private Tracer tracer;
  private final int maxActiveConnections;
  private final AtomicInteger activeConnectionsCount = new AtomicInteger();

  private final WebSocketConfiguration socketConfiguration;
  private final Optional<WebSocketMessageHandler> webSocketMessageHandler;

  @VisibleForTesting public final Optional<AuthenticationService> authenticationService;

  private HttpServer httpServer;
  private final HealthService livenessService;
  private final HealthService readinessService;

  /**
   * Construct a JsonRpcService to handle either http or websocket clients
   *
   * @param vertx The vertx process that will be running this service
   * @param dataDir The data directory where requests can be buffered
   * @param config Configuration for the rpc methods being loaded
   * @param metricsSystem The metrics service that activities should be reported to
   * @param natService The NAT environment manager.
   * @param methods The json rpc methods that should be enabled
   * @param maybeSockets websocket configuration to use
   * @param scheduler for managing ETH tasks
   * @param authenticationService used to grant or deny access to methods served
   * @param livenessService A service responsible for reporting whether this node is live
   * @param readinessService A service responsible for reporting whether this node has fully started
   */
  public JsonRpcService(
      final Vertx vertx,
      final Path dataDir,
      final JsonRpcConfiguration config,
      final MetricsSystem metricsSystem,
      final NatService natService,
      final Map<String, JsonRpcMethod> methods,
      final Optional<WebSocketConfiguration> maybeSockets,
      final EthScheduler scheduler,
      final Optional<AuthenticationService> authenticationService,
      final HealthService livenessService,
      final HealthService readinessService) {
    this.dataDir = dataDir;
    this.requestTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.RPC,
            "request_time",
            "Time taken to process a JSON-RPC request",
            "methodName");
    JsonRpcProcessor jsonRpcProcessor = new BaseJsonRpcProcessor();

    this.socketConfiguration =
        maybeSockets.isPresent() ? maybeSockets.get() : WebSocketConfiguration.createDefault();

    if (authenticationService.isPresent()) {
      jsonRpcProcessor =
          new AuthenticatedJsonRpcProcessor(
              jsonRpcProcessor,
              authenticationService.get(),
              this.socketConfiguration.getRpcApisNoAuth());
    }

    final JsonRpcExecutor jsonRpcExecutor = new JsonRpcExecutor(jsonRpcProcessor, methods);
    this.webSocketMessageHandler =
        Optional.of(
            new WebSocketMessageHandler(
                vertx, jsonRpcExecutor, scheduler, this.socketConfiguration.getTimeoutSec()));

    validateConfig(config);
    this.config = config;
    this.vertx = vertx;
    this.natService = natService;
    this.rpcMethods = methods;
    this.authenticationService = authenticationService;
    this.livenessService = livenessService;
    this.readinessService = readinessService;
    this.maxActiveConnections = config.getMaxActiveConnections();
  }

  public CompletableFuture<Void> start() {
    LOG.info("Starting JSON-RPC service on {}:{}", config.getHost(), config.getPort());
    LOG.debug("max number of active connections {}", maxActiveConnections);
    this.tracer = GlobalOpenTelemetry.getTracer("org.hyperledger.besu.jsonrpc", "1.0.0");

    final CompletableFuture<Void> resultFuture = new CompletableFuture<>();
    try {
      // Create the HTTP server and a router object.
      httpServer = vertx.createHttpServer(getHttpServerOptions());
      httpServer.webSocketHandler(webSocketHandler());
      httpServer.connectionHandler(connectionHandler());

      httpServer
          .requestHandler(buildRouter())
          .listen(
              res -> {
                if (!res.failed()) {
                  resultFuture.complete(null);
                  config.setPort(httpServer.actualPort());
                  LOG.info(
                      "JSON-RPC service started and listening on {}:{}{}",
                      config.getHost(),
                      config.getPort(),
                      tlsLogMessage());

                  natService.ifNatEnvironment(
                      NatMethod.UPNP,
                      natManager ->
                          ((UpnpNatManager) natManager)
                              .requestPortForward(
                                  config.getPort(), NetworkProtocol.TCP, NatServiceType.JSON_RPC));

                  return;
                }

                httpServer = null;
                resultFuture.completeExceptionally(getFailureException(res.cause()));
              });
    } catch (final JsonRpcServiceException tlsException) {
      httpServer = null;
      resultFuture.completeExceptionally(tlsException);
    } catch (final VertxException listenException) {
      httpServer = null;
      resultFuture.completeExceptionally(
          new JsonRpcServiceException(
              String.format(
                  "Ethereum JSON-RPC listener failed to start: %s",
                  ExceptionUtils.rootCause(listenException).getMessage())));
    }

    return resultFuture;
  }

  public CompletableFuture<Void> stop() {
    if (httpServer == null) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<Void> resultFuture = new CompletableFuture<>();
    httpServer.close(
        res -> {
          if (res.failed()) {
            resultFuture.completeExceptionally(res.cause());
          } else {
            httpServer = null;
            resultFuture.complete(null);
          }
        });
    return resultFuture;
  }

  private Handler<HttpConnection> connectionHandler() {

    return connection -> {
      if (activeConnectionsCount.get() >= maxActiveConnections) {
        // disallow new connections to prevent DoS
        LOG.warn(
            "Rejecting new connection from {}. Max {} active connections limit reached.",
            connection.remoteAddress(),
            activeConnectionsCount.getAndIncrement());
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

  private Handler<ServerWebSocket> webSocketHandler() {
    return websocket -> {
      final SocketAddress socketAddress = websocket.remoteAddress();
      final String connectionId = websocket.textHandlerID();
      final String token =
          AuthenticationUtils.getJwtTokenFromAuthorizationHeaderValue(
              websocket.headers().get("Authorization"));
      if (token != null) {
        LOG.trace("Websocket authentication token {}", token);
      }

      if (!hostIsInAllowlist(
          Optional.ofNullable(websocket.headers().get("Host")).orElse("NOHOST"))) {
        websocket.reject(403);
      }

      if (authenticationService.isPresent()) {
        authenticationService
            .get()
            .authenticate(
                token,
                user -> {
                  if (user.isEmpty()) {
                    websocket.reject(403);
                  } else {
                    final Handler<Buffer> socketHandler =
                        handlerForUser(socketAddress, websocket, user);
                    websocket.textMessageHandler(text -> socketHandler.handle(Buffer.buffer(text)));
                    websocket.binaryMessageHandler(socketHandler);
                  }
                });
      } else {
        final Handler<Buffer> socketHandler =
            handlerForUser(socketAddress, websocket, Optional.empty());
        websocket.textMessageHandler(text -> socketHandler.handle(Buffer.buffer(text)));
        websocket.binaryMessageHandler(socketHandler);
      }
      String addr = socketAddressAsString(socketAddress);
      LOG.debug("Websocket Connected ({})", addr);

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

  @NotNull
  private Handler<Buffer> handlerForUser(
      final SocketAddress socketAddress,
      final ServerWebSocket websocket,
      final Optional<User> user) {
    return buffer -> {
      String addr = socketAddressAsString(socketAddress);
      LOG.debug("Received Websocket request (binary frame) {} ({})", buffer, addr);

      if (webSocketMessageHandler.isPresent()) {
        // if auth enabled and user empty will always 401
        webSocketMessageHandler.get().handle(websocket, buffer, user);
      } else {
        LOG.error("No socket request handler configured");
      }
    };
  }

  private void validateConfig(final JsonRpcConfiguration config) {
    checkArgument(
        config.getPort() == 0 || NetworkUtility.isValidPort(config.getPort()),
        "Invalid port configuration.");
    checkArgument(config.getHost() != null, "Required host is not configured.");
    checkArgument(
        config.getMaxActiveConnections() > 0, "Invalid max active connections configuration.");
  }

  private Router buildRouter() {
    // Handle json rpc requests
    final Router router = Router.router(vertx);
    router.route().handler(this::createSpan);

    // Verify Host header to avoid rebind attack.
    router.route().handler(denyRouteToBlockedHost());

    router
        .route()
        .handler(
            CorsHandler.create(buildCorsRegexFromConfig())
                .allowedHeader("*")
                .allowedHeader("content-type"));
    router
        .route()
        .handler(
            BodyHandler.create()
                .setUploadsDirectory(dataDir.resolve("uploads").toString())
                .setDeleteUploadedFilesOnEnd(true));
    router.route("/").method(HttpMethod.GET).handler(this::handleEmptyRequest);
    router
        .route(HealthService.LIVENESS_PATH)
        .method(HttpMethod.GET)
        .handler(livenessService::handleRequest);
    router
        .route(HealthService.READINESS_PATH)
        .method(HttpMethod.GET)
        .handler(readinessService::handleRequest);
    Route mainRoute = router.route("/").method(HttpMethod.POST).produces(APPLICATION_JSON);
    if (authenticationService.isPresent()) {
      mainRoute.handler(
          HandlerFactory.authentication(authenticationService.get(), config.getNoAuthRpcApis()));
    }
    mainRoute
        .handler(HandlerFactory.jsonRpcParser())
        .handler(
            HandlerFactory.timeout(new TimeoutOptions(config.getHttpTimeoutSec()), rpcMethods));
    if (authenticationService.isPresent()) {
      mainRoute.blockingHandler(
          HandlerFactory.jsonRpcExecutor(
              new JsonRpcExecutor(
                  new AuthenticatedJsonRpcProcessor(
                      new TimedJsonRpcProcessor(
                          new TracedJsonRpcProcessor(new BaseJsonRpcProcessor()), requestTimer),
                      authenticationService.get(),
                      config.getNoAuthRpcApis()),
                  rpcMethods),
              tracer));
    } else {
      mainRoute.blockingHandler(
          HandlerFactory.jsonRpcExecutor(
              new JsonRpcExecutor(
                  new TimedJsonRpcProcessor(
                      new TracedJsonRpcProcessor(new BaseJsonRpcProcessor()), requestTimer),
                  rpcMethods),
              tracer));
    }

    if (authenticationService.isPresent()) {
      router
          .route("/login")
          .method(HttpMethod.POST)
          .produces(APPLICATION_JSON)
          .handler(authenticationService.get()::handleLogin);
    } else {
      router
          .route("/login")
          .method(HttpMethod.POST)
          .produces(APPLICATION_JSON)
          .handler(DefaultAuthenticationService::handleDisabledLogin);
    }
    return router;
  }

  private void createSpan(final RoutingContext routingContext) {
    final SocketAddress address = routingContext.request().connection().remoteAddress();

    Context parent =
        traceFormats.extract(Context.current(), routingContext.request(), requestAttributesGetter);
    final Span serverSpan =
        tracer
            .spanBuilder(address.host() + ":" + address.port())
            .setParent(parent)
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
    routingContext.put(SPAN_CONTEXT, Context.current().with(serverSpan));

    routingContext.addEndHandler(
        event -> {
          if (event.failed()) {
            serverSpan.recordException(event.cause());
            serverSpan.setStatus(StatusCode.ERROR);
          }
          serverSpan.end();
        });
    routingContext.next();
  }

  private HttpServerOptions getHttpServerOptions() {
    final HttpServerOptions httpServerOptions =
        new HttpServerOptions()
            .setHost(config.getHost())
            .setPort(config.getPort())
            .setHandle100ContinueAutomatically(true)
            .setCompressionSupported(true);

    httpServerOptions.setMaxWebSocketFrameSize(socketConfiguration.getMaxFrameSize());
    httpServerOptions.setMaxWebSocketMessageSize(socketConfiguration.getMaxFrameSize() * 4);

    applyTlsConfig(httpServerOptions);
    return httpServerOptions;
  }

  private void applyTlsConfig(final HttpServerOptions httpServerOptions) {
    final Optional<TlsConfiguration> maybeTlsConfig = config.getTlsConfiguration();
    if (maybeTlsConfig.isPresent()) {
      final TlsConfiguration tlsConfiguration = maybeTlsConfig.get();
      try {
        httpServerOptions
            .setSsl(true)
            .setPfxKeyCertOptions(
                new PfxOptions()
                    .setPath(tlsConfiguration.getKeyStorePath().toString())
                    .setPassword(tlsConfiguration.getKeyStorePassword()))
            .setUseAlpn(true);

        tlsConfiguration
            .getSecureTransportProtocols()
            .ifPresent(httpServerOptions::setEnabledSecureTransportProtocols);

        tlsConfiguration
            .getCipherSuites()
            .ifPresent(
                cipherSuites -> {
                  for (String cs : cipherSuites) {
                    httpServerOptions.addEnabledCipherSuite(cs);
                  }
                });

        tlsConfiguration
            .getClientAuthConfiguration()
            .ifPresent(
                clientAuthConfiguration ->
                    applyTlsClientAuth(clientAuthConfiguration, httpServerOptions));
      } catch (final RuntimeException re) {
        throw new JsonRpcServiceException(
            String.format(
                "TLS options failed to initialize for Ethereum JSON-RPC listener: %s",
                re.getMessage()));
      }
    }
  }

  private void applyTlsClientAuth(
      final TlsClientAuthConfiguration clientAuthConfiguration,
      final HttpServerOptions httpServerOptions) {
    httpServerOptions.setClientAuth(ClientAuth.REQUIRED);
    clientAuthConfiguration
        .getKnownClientsFile()
        .ifPresent(
            knownClientsFile ->
                httpServerOptions.setTrustOptions(
                    allowlistClients(
                        knownClientsFile, clientAuthConfiguration.isCaClientsEnabled())));
  }

  private String tlsLogMessage() {
    return config.getTlsConfiguration().isPresent() ? " with TLS enabled." : "";
  }

  private Throwable getFailureException(final Throwable listenFailure) {

    JsonRpcServiceException servFail =
        new JsonRpcServiceException(
            String.format(
                "Failed to bind Ethereum JSON-RPC listener to %s:%s: %s",
                config.getHost(), config.getPort(), listenFailure.getMessage()));
    servFail.initCause(listenFailure);

    return servFail;
  }

  private Handler<RoutingContext> denyRouteToBlockedHost() {
    return event -> {
      final Optional<String> hostHeader = getAndValidateHostHeader(event);
      if (config.getHostsAllowlist().contains("*")
          || (hostHeader.isPresent() && hostIsInAllowlist(hostHeader.get()))) {
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

  private Optional<String> getAndValidateHostHeader(final RoutingContext event) {
    String hostname =
        event.request().getHeader(HttpHeaders.HOST) != null
            ? event.request().getHeader(HttpHeaders.HOST)
            : event.request().host();
    final Iterable<String> splitHostHeader = Splitter.on(':').split(hostname);
    final long hostPieces = stream(splitHostHeader).count();
    // If the host contains a colon, verify the host is correctly formed - host [ ":" port ]
    if (hostPieces > 1) {
      if (hostPieces > 2 || !Iterables.get(splitHostHeader, 1).matches("\\d{1,5}+")) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(Iterables.get(splitHostHeader, 0));
  }

  private boolean hostIsInAllowlist(final String hostHeader) {
    if (config.getHostsAllowlist().contains("*")
        || config.getHostsAllowlist().stream()
            .anyMatch(allowlistEntry -> allowlistEntry.equalsIgnoreCase(hostHeader))) {
      return true;
    } else {
      LOG.trace("Host not in allowlist: '{}'", hostHeader);
      return false;
    }
  }

  public InetSocketAddress socketAddress() {
    if (httpServer == null) {
      return EMPTY_SOCKET_ADDRESS;
    }
    return new InetSocketAddress(config.getHost(), httpServer.actualPort());
  }

  private String socketAddressAsString(final SocketAddress socketAddress) {
    return String.format("host=%s, port=%d", socketAddress.host(), socketAddress.port());
  }

  @VisibleForTesting
  public String url() {
    if (httpServer == null) {
      return "";
    }
    return NetworkUtility.urlForSocketAddress(getScheme(), socketAddress());
  }

  private String getScheme() {
    return config.getTlsConfiguration().isPresent() ? "https" : "http";
  }

  // Facilitate remote health-checks in AWS, inter alia.
  private void handleEmptyRequest(final RoutingContext routingContext) {
    routingContext.response().setStatusCode(201).end();
  }

  private String buildCorsRegexFromConfig() {
    if (config.getCorsAllowedDomains().isEmpty()) {
      return "";
    }
    if (config.getCorsAllowedDomains().contains("*")) {
      return ".*";
    } else {
      final StringJoiner stringJoiner = new StringJoiner("|");
      config.getCorsAllowedDomains().stream().filter(s -> !s.isEmpty()).forEach(stringJoiner::add);
      return stringJoiner.toString();
    }
  }
}
