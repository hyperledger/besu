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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataRequest;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;
import java.util.stream.Stream;

public class PersistDataStep {

  private final SnapSyncState snapSyncState;
  private final WorldStateStorage worldStateStorage;
  private final SnapWorldDownloadState downloadState;

  public PersistDataStep(
      final SnapSyncState snapSyncState,
      final WorldStateStorage worldStateStorage,
      final SnapWorldDownloadState downloadState) {
    this.snapSyncState = snapSyncState;
    this.worldStateStorage = worldStateStorage;
    this.downloadState = downloadState;
  }

  public List<Task<SnapDataRequest>> persist(final List<Task<SnapDataRequest>> tasks) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    for (Task<SnapDataRequest> task : tasks) {
      if (task.getData().isResponseReceived()) {
        // enqueue child requests
        final Stream<SnapDataRequest> childRequests =
            task.getData().getChildRequests(downloadState, worldStateStorage, snapSyncState);
        if (!(task.getData() instanceof TrieNodeDataRequest)) {
          enqueueChildren(childRequests);
        } else {
          if (!task.getData().isExpired(snapSyncState)) {
            enqueueChildren(childRequests);
          } else {
            continue;
          }
        }

        // persist nodes
        final int persistedNodes =
            task.getData().persist(worldStateStorage, updater, downloadState, snapSyncState);
        if (persistedNodes > 0) {
          if (task.getData() instanceof TrieNodeDataRequest) {
            downloadState.getMetricsManager().notifyNodesHealed(persistedNodes);
          } else {
            downloadState.getMetricsManager().notifyNodesGenerated(persistedNodes);
          }
        }
      }
    }
    updater.commit();
    return tasks;
  }

  public Task<SnapDataRequest> persist(final Task<SnapDataRequest> task) {
    return persist(List.of(task)).get(0);
  }

  private void enqueueChildren(final Stream<SnapDataRequest> childRequests) {
    downloadState.enqueueRequests(childRequests);
  }
}
