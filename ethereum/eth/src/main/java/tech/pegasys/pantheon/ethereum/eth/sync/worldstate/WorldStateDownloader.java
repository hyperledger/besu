/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetNodeDataFromPeerTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeerTask;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.queue.BigQueue;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldStateDownloader {
  private static final Logger LOG = LogManager.getLogger();

  private enum Status {
    IDLE,
    RUNNING,
    DONE
  }

  private final EthContext ethContext;
  private final BigQueue<NodeDataRequest> pendingRequests;
  private final WorldStateStorage.Updater worldStateStorageUpdater;
  private final int hashCountPerRequest;
  private final int maxOutstandingRequests;
  private final AtomicInteger outstandingRequests = new AtomicInteger(0);
  private final LabelledMetric<OperationTimer> ethTasksTimer;
  private final WorldStateStorage worldStateStorage;
  private final AtomicBoolean sendingRequests = new AtomicBoolean(false);
  private volatile CompletableFuture<Void> future;
  private volatile Status status = Status.IDLE;

  public WorldStateDownloader(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final BigQueue<NodeDataRequest> pendingRequests,
      final int hashCountPerRequest,
      final int maxOutstandingRequests,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.ethContext = ethContext;
    this.worldStateStorage = worldStateStorage;
    this.pendingRequests = pendingRequests;
    this.hashCountPerRequest = hashCountPerRequest;
    this.maxOutstandingRequests = maxOutstandingRequests;
    this.ethTasksTimer = ethTasksTimer;
    this.worldStateStorageUpdater = worldStateStorage.updater();
  }

  public CompletableFuture<Void> run(final BlockHeader header) {
    LOG.info(
        "Begin downloading world state from peers for block {} ({})",
        header.getNumber(),
        header.getHash());
    synchronized (this) {
      if (status == Status.DONE || status == Status.RUNNING) {
        return future;
      }
      status = Status.RUNNING;
      future = new CompletableFuture<>();
    }

    Hash stateRoot = header.getStateRoot();
    if (stateRoot.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      // If we're requesting data for an empty world state, we're already done
      markDone();
    } else {
      pendingRequests.enqueue(NodeDataRequest.createAccountDataRequest(stateRoot));
      requestNodeData(header);
    }

    return future;
  }

  public void cancel() {
    // TODO
  }

  private void requestNodeData(final BlockHeader header) {
    if (sendingRequests.compareAndSet(false, true)) {
      while (shouldRequestNodeData()) {
        Optional<EthPeer> maybePeer = ethContext.getEthPeers().idlePeer(header.getNumber());

        if (!maybePeer.isPresent()) {
          // If no peer is available, wait and try again
          waitForNewPeer().whenComplete((r, t) -> requestNodeData(header));
          break;
        } else {
          EthPeer peer = maybePeer.get();

          // Collect data to be requested
          List<NodeDataRequest> toRequest = new ArrayList<>();
          for (int i = 0; i < hashCountPerRequest; i++) {
            NodeDataRequest pendingRequest = pendingRequests.dequeue();
            if (pendingRequest == null) {
              break;
            }
            toRequest.add(pendingRequest);
          }

          // Request and process node data
          outstandingRequests.incrementAndGet();
          sendAndProcessRequests(peer, toRequest)
              .whenComplete(
                  (res, error) -> {
                    if (outstandingRequests.decrementAndGet() == 0 && pendingRequests.isEmpty()) {
                      // We're done
                      worldStateStorageUpdater.commit();
                      markDone();
                    } else {
                      // Send out additional requests
                      requestNodeData(header);
                    }
                  });
        }
      }
      sendingRequests.set(false);
    }
  }

  private synchronized void markDone() {
    LOG.info("Finished downloading world state from peers");
    if (future == null) {
      future = CompletableFuture.completedFuture(null);
    } else {
      future.complete(null);
    }
    status = Status.DONE;
  }

  private boolean shouldRequestNodeData() {
    return !future.isDone()
        && outstandingRequests.get() < maxOutstandingRequests
        && !pendingRequests.isEmpty();
  }

  private CompletableFuture<?> waitForNewPeer() {
    return ethContext
        .getScheduler()
        .timeout(WaitForPeerTask.create(ethContext, ethTasksTimer), Duration.ofSeconds(5));
  }

  private CompletableFuture<?> sendAndProcessRequests(
      final EthPeer peer, final List<NodeDataRequest> requests) {
    List<Hash> hashes =
        requests.stream().map(NodeDataRequest::getHash).distinct().collect(Collectors.toList());
    return GetNodeDataFromPeerTask.forHashes(ethContext, hashes, ethTasksTimer)
        .assignPeer(peer)
        .run()
        .thenApply(PeerTaskResult::getResult)
        .thenApply(this::mapNodeDataByHash)
        .whenComplete(
            (data, err) -> {
              boolean requestFailed = err != null;
              for (NodeDataRequest request : requests) {
                BytesValue matchingData = requestFailed ? null : data.get(request.getHash());
                if (matchingData == null) {
                  pendingRequests.enqueue(request);
                } else {
                  // Persist request data
                  request.setData(matchingData);
                  request.persist(worldStateStorageUpdater);

                  // Queue child requests
                  request
                      .getChildRequests()
                      .filter(n -> !worldStateStorage.contains(n.getHash()))
                      .forEach(pendingRequests::enqueue);
                }
              }
            });
  }

  private Map<Hash, BytesValue> mapNodeDataByHash(final List<BytesValue> data) {
    // Map data by hash
    Map<Hash, BytesValue> dataByHash = new HashMap<>();
    data.stream().forEach(d -> dataByHash.put(Hash.hash(d), d));
    return dataByHash;
  }
}
