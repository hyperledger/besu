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
package org.hyperledger.besu.metrics.prometheus;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Streams.stream;

import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsHttpService implements MetricsService {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsHttpService.class);

  private static final InetSocketAddress EMPTY_SOCKET_ADDRESS = new InetSocketAddress("0.0.0.0", 0);

  private final Vertx vertx;
  private final MetricsConfiguration config;
  private final MetricsSystem metricsSystem;

  private HttpServer httpServer;

  public MetricsHttpService(
      final Vertx vertx,
      final MetricsConfiguration configuration,
      final MetricsSystem metricsSystem) {
    validateConfig(configuration);
    this.vertx = vertx;
    this.config = configuration;
    this.metricsSystem = metricsSystem;
  }

  private void validateConfig(final MetricsConfiguration config) {
    checkArgument(config.getPort() >= 0 && config.getPort() < 65535, "Invalid port configuration.");
    checkArgument(config.getHost() != null, "Required host is not configured.");
    checkArgument(
        !(config.isEnabled() && config.isPushEnabled()),
        "Metrics Http Service cannot run concurrent with push metrics.");
  }

  @Override
  public CompletableFuture<?> start() {
    LOG.info("Starting metrics http service on {}:{}", config.getHost(), config.getPort());
    // Create the HTTP server and a router object.
    httpServer =
        vertx.createHttpServer(
            new HttpServerOptions()
                .setHost(config.getHost())
                .setPort(config.getPort())
                .setIdleTimeout(config.getIdleTimeout())
                .setHandle100ContinueAutomatically(true)
                .setCompressionSupported(true));

    final Router router = Router.router(vertx);

    // Verify Host header.
    router.route().handler(checkAllowlistHostHeader());

    // Endpoint for AWS health check.
    router.route("/").method(HttpMethod.GET).handler(this::handleEmptyRequest);

    // Endpoint for Prometheus metrics monitoring.
    router.route("/metrics").method(HttpMethod.GET).handler(this::metricsRequest);

    final CompletableFuture<?> resultFuture = new CompletableFuture<>();
    httpServer
        .requestHandler(router)
        .listen(
            res -> {
              if (!res.failed()) {
                resultFuture.complete(null);
                final int actualPort = httpServer.actualPort();
                config.setActualPort(actualPort);
                LOG.info(
                    "Metrics service started and listening on {}:{}", config.getHost(), actualPort);
                return;
              }
              httpServer = null;
              final Throwable cause = res.cause();
              if (cause instanceof SocketException) {
                resultFuture.completeExceptionally(
                    new RuntimeException(
                        String.format(
                            "Failed to bind metrics listener to %s:%s (actual port %s): %s",
                            config.getHost(),
                            config.getPort(),
                            config.getActualPort(),
                            cause.getMessage())));
                return;
              }
              resultFuture.completeExceptionally(cause);
            });
    return resultFuture;
  }

  private Handler<RoutingContext> checkAllowlistHostHeader() {
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
    final String hostHeader = event.request().host();
    if (hostHeader == null) {
      return Optional.empty();
    }
    final Iterable<String> splitHostHeader = Splitter.on(':').split(hostHeader);
    final long hostPieces = stream(splitHostHeader).count();
    if (hostPieces > 1) {
      // If the host contains a colon, verify the host is correctly formed - host [ ":" port ]
      if (hostPieces > 2 || !Iterables.get(splitHostHeader, 1).matches("\\d{1,5}+")) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(Iterables.get(splitHostHeader, 0));
  }

  private boolean hostIsInAllowlist(final String hostHeader) {
    if (config.getHostsAllowlist().stream()
        .anyMatch(
            allowlistEntry -> allowlistEntry.toLowerCase().equals(hostHeader.toLowerCase()))) {
      return true;
    } else {
      LOG.trace("Host not in allowlist: '{}'", hostHeader);
      return false;
    }
  }

  @Override
  public CompletableFuture<?> stop() {
    if (httpServer == null) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<?> resultFuture = new CompletableFuture<>();
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

  private void metricsRequest(final RoutingContext routingContext) {
    final Set<String> names = new TreeSet<>(routingContext.queryParam("name[]"));
    final HttpServerResponse response = routingContext.response();
    vertx.<String>executeBlocking(
        future -> {
          try {
            final ByteArrayOutputStream metrics = new ByteArrayOutputStream(16 * 1024);
            final OutputStreamWriter osw = new OutputStreamWriter(metrics, StandardCharsets.UTF_8);
            TextFormat.write004(
                osw,
                ((PrometheusMetricsSystem) (metricsSystem))
                    .getRegistry()
                    .filteredMetricFamilySamples(names));
            osw.flush();
            osw.close();
            metrics.flush();
            metrics.close();
            future.complete(metrics.toString(StandardCharsets.UTF_8.name()));
          } catch (final IOException ioe) {
            future.fail(ioe);
          }
        },
        false,
        (res) -> {
          if (response.closed()) {
            // Request for metrics closed before response was generated
            return;
          }
          if (res.failed()) {
            LOG.error("Request for metrics failed", res.cause());
            response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
          } else {
            response.setStatusCode(HttpResponseStatus.OK.code());
            response.putHeader("Content-Type", TextFormat.CONTENT_TYPE_004);
            response.end(res.result());
          }
        });
  }

  InetSocketAddress socketAddress() {
    if (httpServer == null) {
      return EMPTY_SOCKET_ADDRESS;
    }
    return new InetSocketAddress(config.getHost(), httpServer.actualPort());
  }

  @Override
  public Optional<Integer> getPort() {
    if (httpServer == null) {
      return Optional.empty();
    }
    return Optional.of(httpServer.actualPort());
  }

  // Facilitate remote health-checks in AWS, inter alia.
  private void handleEmptyRequest(final RoutingContext routingContext) {
    routingContext.response().setStatusCode(201).end();
  }
}
