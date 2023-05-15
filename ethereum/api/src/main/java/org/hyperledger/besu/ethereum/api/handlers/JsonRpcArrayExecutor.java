package org.hyperledger.besu.ethereum.api.handlers;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonResponseStreamer;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class JsonRpcArrayExecutor extends AbstractJsonRpcExecutor {
  public JsonRpcArrayExecutor(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    super(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration);
  }

  /**
   * Executes the JSON-RPC request(s) associated with the current routing context.
   *
   * @throws IOException if there is an error writing the response to the client
   */
  @Override
  void execute() throws IOException {
    HttpServerResponse response = prepareHttpResponse(ctx);
    final JsonArray batchJsonRequest = getRequestBodyAsJsonArray(ctx);
    if (isBatchSizeValid(batchJsonRequest)) {
      try (final JsonResponseStreamer streamer =
          new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
        executeRpcRequestBatch(batchJsonRequest, streamer);
      }
    } else {
      handleJsonRpcError(ctx, null, JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE);
    }
  }

  /**
   * Executes a batch of RPC requests.
   *
   * @param rpcRequestBatch the batch of RPC requests.
   * @param streamer the JsonResponseStreamer to use.
   */
  public void executeRpcRequestBatch(
      final JsonArray rpcRequestBatch, final JsonResponseStreamer streamer) throws IOException {
    try (JsonGenerator generator = getJsonObjectMapper().getFactory().createGenerator(streamer)) {
      generator.writeStartArray();
      for (int i = 0; i < rpcRequestBatch.size(); i++) {
        JsonRpcResponse response = processMaybeRequest(rpcRequestBatch.getValue(i));
        if (response.getType() != JsonRpcResponseType.NONE) {
          generator.writeObject(response);
        }
      }
      generator.writeEndArray();
    }
  }

  /**
   * Processes a single RPC request.
   *
   * @param maybeRequest the object that might be a request.
   * @return the response from executing the request, or an error response if it wasn't a valid
   *     request.
   */
  private JsonRpcResponse processMaybeRequest(final Object maybeRequest) {
    if (maybeRequest instanceof JsonObject) {
      return executeRequest((JsonObject) maybeRequest);
    } else {
      return createErrorResponse();
    }
  }

  /**
   * Executes a single RPC request.
   *
   * @param request the request to execute.
   * @return the response from executing the request.
   */
  private JsonRpcResponse executeRequest(final JsonObject request) {
    return executeRequest(jsonRpcExecutor, tracer, request, ctx);
  }

  /**
   * Creates an error response for an invalid request.
   *
   * @return an error response.
   */
  private JsonRpcResponse createErrorResponse() {
    return new JsonRpcErrorResponse(null, INVALID_REQUEST);
  }

  @Override
  String getRpcMethodName(final RoutingContext ctx) {
    return "JsonArray";
  }

  private boolean isBatchSizeValid(final JsonArray batchJsonRequest) {
    return !(jsonRpcConfiguration.getMaxBatchSize() > 0
        && batchJsonRequest.size() > jsonRpcConfiguration.getMaxBatchSize());
  }

  private JsonArray getRequestBodyAsJsonArray(final RoutingContext ctx) {
    final JsonArray batchJsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
    lazyTraceLogger(batchJsonRequest::toString);
    return batchJsonRequest;
  }
}
