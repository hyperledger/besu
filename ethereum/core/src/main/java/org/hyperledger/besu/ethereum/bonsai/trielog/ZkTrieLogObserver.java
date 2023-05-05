/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;

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

public class ZkTrieLogObserver
    implements TrieLogEvent.TrieLogObserver, BesuEvents.SyncStatusListener {

  private static final Logger LOG = LoggerFactory.getLogger(ZkTrieLogObserver.class);
  // todo: get from config
  private String shomeiHttpHost = "localhost";
  private int shomeiHttpPort = 8888;
  private boolean isSyncing;

  static TrieLogFactory zkTrieLogFactory = new ZkTrieLogFactoryImpl();
  private final WebClient webClient;

  public ZkTrieLogObserver(final String shomeiHttpHost, final int shomeiHttpPort) {
    Vertx vertx = Vertx.vertx();
    WebClientOptions options = new WebClientOptions();
    this.webClient = WebClient.create(vertx, options);
    this.shomeiHttpHost = shomeiHttpHost;
    this.shomeiHttpPort = shomeiHttpPort;

    // TODO: wire up the SyncStatusListener via plugin
  }

  @Override
  public void onTrieLogAdded(final TrieLogEvent event) {
    handleShip(event)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                LOG.atTrace()
                    .setMessage("shipped trie log for {}:{}, response: {}")
                    .addArgument(event.layer().getBlockNumber())
                    .addArgument(event.layer().getBlockHash())
                    .addArgument(ar.result().bodyAsString())
                    .log();

              } else {
                LOG.atTrace()
                    .setMessage("failed to ship trie log for {}:{} \n {}")
                    .addArgument(event.layer().getBlockNumber())
                    .addArgument(event.layer().getBlockHash())
                    .addArgument(ar.cause())
                    .log();
              }
            });
  }

  @VisibleForTesting
  Future<HttpResponse<Buffer>> handleShip(final TrieLogEvent addedEvent) {

    byte[] rlpBytes = zkTrieLogFactory.serialize(addedEvent.layer());

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
                        .put("blockNumber", addedEvent.layer().getBlockNumber())
                        .put("blockHash", addedEvent.layer().getBlockHash().toHexString())
                        .put("isSync", isSyncing)
                        .put("trieLog", Bytes.wrap(rlpBytes).toHexString())));

    // Send the request to the JSON-RPC service
    return webClient
        .post(shomeiHttpPort, shomeiHttpHost, "/")
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(jsonRpcRequest);
  }

  // TODO: remove this in favor of plugin-based configuration of observers:
  ZkTrieLogObserver addAsObserverTo(
      final Subscribers<TrieLogEvent.TrieLogObserver> addToSubscribers) {
    addToSubscribers.subscribe(this);
    return this;
  }

  @Override
  public void onSyncStatusChanged(final Optional<SyncStatus> syncStatus) {
    syncStatus.ifPresent(
        sync -> {
          // return isSyncing if we are more than 50 blocks behind head
          isSyncing = sync.getCurrentBlock() < sync.getHighestBlock() - 50;
        });
  }
}
