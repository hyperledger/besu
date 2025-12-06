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
package org.hyperledger.besu.ethereum.api.handlers;

import static org.hyperledger.besu.ethereum.api.handlers.AbstractJsonRpcExecutor.handleJsonRpcError;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcExecutorHandler {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcExecutorHandler.class);

  private JsonRpcExecutorHandler() {}

  public static Handler<RoutingContext> handler(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final Map<HttpConnection, Set<InterruptibleCompletableFuture<Void>>>
          activeRequestsByConnection) {
    return ctx ->
        handleRequest(
            jsonRpcExecutor, tracer, jsonRpcConfiguration, activeRequestsByConnection, ctx);
  }

  private static void handleRequest(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final Map<HttpConnection, Set<InterruptibleCompletableFuture<Void>>>
          activeRequestsByConnection,
      final RoutingContext ctx) {

    final long timeoutMillis = jsonRpcConfiguration.getHttpTimeoutSec() * 1000;

    Optional<AbstractJsonRpcExecutor> executorOpt =
        createExecutor(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration);

    if (executorOpt.isEmpty()) {
      handleErrorAndEndResponse(ctx, null, RpcErrorType.PARSE_ERROR);
      return;
    }

    final AbstractJsonRpcExecutor executor = executorOpt.get();
    InterruptibleCompletableFuture<Void> executionFuture =
        executeAsync(executor, ctx, activeRequestsByConnection, timeoutMillis);

    executionFuture.handle((result, throwable) -> handleCompletion(ctx, throwable, timeoutMillis));
  }

  private static InterruptibleCompletableFuture<Void> executeAsync(
      final AbstractJsonRpcExecutor executor,
      final RoutingContext ctx,
      final Map<HttpConnection, Set<InterruptibleCompletableFuture<Void>>>
          activeRequestsByConnection,
      final long timeoutMillis) {

    final InterruptibleCompletableFuture<Void> executionFuture =
        new InterruptibleCompletableFuture<>();

    // Register this request with the connection's active requests
    if (ctx.request() != null && ctx.request().connection() != null) {
      Set<InterruptibleCompletableFuture<Void>> activeFutures =
          activeRequestsByConnection.computeIfAbsent(
              ctx.request().connection(), k -> ConcurrentHashMap.newKeySet());
      activeFutures.add(executionFuture);
      // Remove when complete
      executionFuture.whenComplete((r, t) -> activeFutures.remove(executionFuture));
    }

    CompletableFuture.runAsync(
            () -> {
              executionFuture.setWorkerThread(Thread.currentThread());
              try {
                executor.execute();
                executionFuture.complete(null);
              } catch (IOException e) {
                logExecutionError(ctx, executor, e);
                executionFuture.completeExceptionally(
                    new RuntimeException("Error executing RPC method", e));
              } catch (Exception e) {
                executionFuture.completeExceptionally(e);
              } finally {
                executionFuture.clearWorkerThread();
              }
            })
        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .whenComplete(
            (result, throwable) -> {
              if (throwable != null && !executionFuture.isDone()) {
                // For timeout exceptions, cancel with interruption to stop the worker thread
                Throwable cause = unwrapException(throwable);
                if (cause instanceof TimeoutException) {
                  executionFuture.cancel(true);
                } else {
                  executionFuture.completeExceptionally(throwable);
                }
              }
            });

    return executionFuture;
  }

  private static Void handleCompletion(
      final RoutingContext ctx, final Throwable throwable, final long timeoutMillis) {

    if (throwable == null) {
      return null; // Successful completion, response already sent
    }

    Throwable actualCause = unwrapException(throwable);

    // Treat both TimeoutException and CancellationException as timeouts
    // (CancellationException occurs when we cancel the future due to timeout)
    if (actualCause instanceof TimeoutException || actualCause instanceof CancellationException) {
      handleTimeout(ctx, timeoutMillis);
    } else {
      handleExecutionError(ctx, actualCause);
    }

    return null;
  }

  private static Throwable unwrapException(final Throwable throwable) {
    if (throwable instanceof CompletionException && throwable.getCause() != null) {
      return throwable.getCause();
    }
    return throwable;
  }

  private static void handleTimeout(final RoutingContext ctx, final long timeoutMillis) {

    final String requestBodyAsJson =
        ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name()).toString();

    LOG.error(
        "Timeout ({} ms) occurred in JSON-RPC executor for method {}",
        timeoutMillis,
        getShortLogString(requestBodyAsJson));
    LOG.atTrace()
        .setMessage("Timeout ({} ms) occurred in JSON-RPC executor for method {}")
        .addArgument(timeoutMillis)
        .addArgument(requestBodyAsJson)
        .log();

    // Thread interruption is automatically handled by executeAsync's whenComplete handler
    handleErrorAndEndResponse(ctx, null, RpcErrorType.TIMEOUT_ERROR);
  }

  private static void handleExecutionError(final RoutingContext ctx, final Throwable actualCause) {
    Throwable cause =
        actualCause instanceof ExecutionException ? actualCause.getCause() : actualCause;

    final String requestBodyAsJson =
        ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name()).toString();

    LOG.error(
        "Exception during RPC execution for method {}",
        getShortLogString(requestBodyAsJson),
        cause);

    handleErrorAndEndResponse(ctx, null, RpcErrorType.INTERNAL_ERROR);
  }

  private static void logExecutionError(
      final RoutingContext ctx, final AbstractJsonRpcExecutor executor, final IOException e) {
    final String method = executor.getRpcMethodName(ctx);
    LOG.error("{} - Error streaming JSON-RPC response", method, e);

    final String requestBodyAsJson =
        ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name()).toString();
    LOG.atTrace()
        .setMessage("{} - Error streaming JSON-RPC response")
        .addArgument(requestBodyAsJson)
        .log();
  }

  private static Object getShortLogString(final String requestBodyAsJson) {
    final int maxLogLength = 256;
    return requestBodyAsJson == null || requestBodyAsJson.length() < maxLogLength
        ? requestBodyAsJson
        : requestBodyAsJson.substring(0, maxLogLength).concat("...");
  }

  private static void handleErrorAndEndResponse(
      final RoutingContext ctx, final Object id, final RpcErrorType errorType) {
    if (!ctx.response().ended()) {
      handleJsonRpcError(ctx, id, errorType);
    }
  }

  private static Optional<AbstractJsonRpcExecutor> createExecutor(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    if (isJsonObjectRequest(ctx)) {
      return Optional.of(
          new JsonRpcObjectExecutor(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration));
    }
    if (isJsonArrayRequest(ctx)) {
      return Optional.of(
          new JsonRpcArrayExecutor(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration));
    }
    return Optional.empty();
  }

  private static boolean isJsonObjectRequest(final RoutingContext ctx) {
    return ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
  }

  private static boolean isJsonArrayRequest(final RoutingContext ctx) {
    return ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
  }
}
