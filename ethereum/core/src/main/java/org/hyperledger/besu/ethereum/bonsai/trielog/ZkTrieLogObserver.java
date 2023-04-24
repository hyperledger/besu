package org.hyperledger.besu.ethereum.bonsai.trielog;

import java.util.Base64;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;


public class ZkTrieLogObserver implements TrieLogAddedEvent.TrieLogAddedObserver {

  //todo: get from config
  private String shomeiHttpHost = "localhost";
  private int shomeiHttpPort = 8080;

  static TrieLogFactory<TrieLogLayer> zkTrieLogFactory = new ZkTrieLogFactoryImpl();
  private final WebClient webClient;

  public ZkTrieLogObserver(final String shomeiHttpHost, final int shomeiHttpPort) {
    Vertx vertx = Vertx.vertx();
    WebClientOptions options = new WebClientOptions();
    this.webClient = WebClient.create(vertx, options);
    this.shomeiHttpHost = shomeiHttpHost;
    this.shomeiHttpPort = shomeiHttpPort;
  }

  @Override
  public void onTrieLogAdded(final TrieLogAddedEvent event) {
    handleShip(event);
  }

  @VisibleForTesting
  Future<HttpResponse<Buffer>> handleShip(final TrieLogAddedEvent event) {
    TrieLogLayer layer = event.getLayer();
    byte[] rlpBytes = zkTrieLogFactory.serialize(layer);
    return sendToZk(rlpBytes);
  }

  Future<HttpResponse<Buffer>> sendToZk(final byte[] rlpBytes) {
    // Create a JSON-RPC request
    JsonObject jsonRpcRequest = new JsonObject()
        .put("jsonrpc", "2.0")
        .put("id", 1)
        .put("method", "state_sendRawTrieLog")
        .put("params", new JsonObject().put("data", Base64.getEncoder().encodeToString(rlpBytes)));

    // Send the request to the JSON-RPC service
    return webClient.post(shomeiHttpPort, shomeiHttpHost, "/shomei")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(jsonRpcRequest);
  }
}