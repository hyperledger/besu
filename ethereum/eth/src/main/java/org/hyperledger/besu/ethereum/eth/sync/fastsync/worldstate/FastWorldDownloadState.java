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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator.applyForStrategy;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;

import java.time.Clock;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastWorldDownloadState extends WorldDownloadState<NodeDataRequest> {
  private static final Logger LOG = LoggerFactory.getLogger(FastWorldDownloadState.class);

  public FastWorldDownloadState(
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final InMemoryTasksPriorityQueues<NodeDataRequest> pendingRequests,
      final int maxRequestsWithoutProgress,
      final long minMillisBeforeStalling,
      final Clock clock,
      final SyncDurationMetrics syncDurationMetrics) {
    super(
        worldStateStorageCoordinator,
        pendingRequests,
        maxRequestsWithoutProgress,
        minMillisBeforeStalling,
        clock,
        syncDurationMetrics);
  }

  @Override
  public synchronized boolean checkCompletion(final BlockHeader header) {
    if (!internalFuture.isDone() && pendingRequests.allTasksCompleted()) {
      if (rootNodeData == null) {
        enqueueRequest(
            NodeDataRequest.createAccountDataRequest(
                header.getStateRoot(), Optional.of(Bytes.EMPTY)));
        return false;
      }
      final WorldStateKeyValueStorage.Updater updater = worldStateStorageCoordinator.updater();
      applyForStrategy(
          updater,
          onBonsai -> {
            onBonsai.saveWorldState(header.getHash(), header.getStateRoot(), rootNodeData);
          },
          onForest -> {
            onForest.saveWorldState(header.getStateRoot(), rootNodeData);
          });
      updater.commit();

      internalFuture.complete(null);
      // THere are no more inputs to process so make sure we wake up any threads waiting to dequeue
      // so they can give up waiting.
      notifyAll();

      LOG.info("Finished downloading world state from peers");

      return true;
    } else {
      return false;
    }
  }
}
