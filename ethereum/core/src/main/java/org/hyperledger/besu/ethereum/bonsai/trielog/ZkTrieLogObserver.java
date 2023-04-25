package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogAddedEvent.TrieLogAddedObserver;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.tuweni.bytes.Bytes;

public class ZkTrieLogObserver implements TrieLogAddedObserver {

  // todo: get from config
  private String shomeiHttpHost = "localhost";
  private int shomeiHttpPort = 8888;

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

    byte[] rlpBytes = zkTrieLogFactory.serialize(event.getLayer());

    // Create a JSON-RPC request
    JsonObject jsonRpcRequest =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "state_sendRawTrieLog")
            .put(
                "params",
                List.of(event.getBlockHash().toHexString(), Bytes.wrap(rlpBytes).toHexString()));

    // Send the request to the JSON-RPC service
    return webClient
        .post(shomeiHttpPort, shomeiHttpHost, "/")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(jsonRpcRequest);
  }

  // TODO: remove this in favor of plugin-based configuration of observers:
  ZkTrieLogObserver addAsObserverTo(final Subscribers<TrieLogAddedObserver> addToSubscribers) {
    addToSubscribers.subscribe(this);
    return this;
  }
}
