package tech.pegasys.pantheon.ethereum.jsonrpc.websocket;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketRequestHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;
  private final Map<String, JsonRpcMethod> methods;

  public WebSocketRequestHandler(final Vertx vertx, final Map<String, JsonRpcMethod> methods) {
    this.vertx = vertx;
    this.methods = methods;
  }

  public void handle(final String id, final Buffer buffer) {
    vertx.executeBlocking(
        future -> {
          WebSocketRpcRequest request;
          try {
            request = buffer.toJsonObject().mapTo(WebSocketRpcRequest.class);
          } catch (IllegalArgumentException | DecodeException e) {
            LOG.debug("Error mapping json to WebSocketRpcRequest", e);
            future.complete(JsonRpcError.INVALID_REQUEST);
            return;
          }

          if (!methods.containsKey(request.getMethod())) {
            future.complete(JsonRpcError.METHOD_NOT_FOUND);
            LOG.debug("Can't find method {}", request.getMethod());
            return;
          }
          final JsonRpcMethod method = methods.get(request.getMethod());
          try {
            LOG.info("WS-RPC request -> {}", request.getMethod());
            request.setConnectionId(id);
            future.complete(method.response(request));
          } catch (final Exception e) {
            LOG.error(JsonRpcError.INTERNAL_ERROR.getMessage(), e);
            future.complete(JsonRpcError.INTERNAL_ERROR);
          }
        },
        result -> {
          if (result.succeeded()) {
            replyToClient(id, Json.encodeToBuffer(result.result()));
          } else {
            replyToClient(id, Json.encodeToBuffer(JsonRpcError.INTERNAL_ERROR));
          }
        });
  }

  private void replyToClient(final String id, final Buffer request) {
    vertx.eventBus().send(id, request.toString());
  }
}
