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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkTrieLogObserver implements TrieLogAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(ZkTrieLogObserver.class);
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
    handleShip(event)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                LOG.atTrace()
                    .setMessage("shipped trie log for {}, response: {}")
                    .addArgument(event.getBlockHeader().toLogString())
                    .addArgument(ar.result().bodyAsString())
                    .log();

              } else {
                LOG.atTrace()
                    .setMessage("failed to ship trie log for {} \n {}")
                    .addArgument(event.getBlockHeader().toLogString())
                    .addArgument(ar.cause())
                    .log();
              }
            });
  }

  @VisibleForTesting
  Future<HttpResponse<Buffer>> handleShip(final TrieLogAddedEvent event) {

    byte[] rlpBytes = zkTrieLogFactory.serialize(event.getLayer());

    JsonObject jsonRpcRequest =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "state_sendRawTrieLog")
            .put(
                "params",
                // TODO: interface this parameter
                List.of(
                    new JsonObject()
                        .put("blockNumber", event.getBlockHeader().getNumber())
                        .put("blockHash", event.getBlockHeader().getBlockHash().toHexString())
                        .put("trieLog", Bytes.wrap(rlpBytes).toHexString())));

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
