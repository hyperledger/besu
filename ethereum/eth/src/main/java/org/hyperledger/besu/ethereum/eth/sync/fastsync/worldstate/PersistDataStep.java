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

import static org.hyperledger.besu.ethereum.eth.sync.StorageExceptionManager.canRetryOnError;
import static org.hyperledger.besu.ethereum.eth.sync.StorageExceptionManager.errorCountAtThreshold;
import static org.hyperledger.besu.ethereum.eth.sync.StorageExceptionManager.getRetryableErrorCounter;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistDataStep {

  private static final Logger LOG = LoggerFactory.getLogger(PersistDataStep.class);

  private final WorldStateStorageCoordinator worldStateStorageCoordinator;

  public PersistDataStep(final WorldStateStorageCoordinator worldStateStorageCoordinator) {
    this.worldStateStorageCoordinator = worldStateStorageCoordinator;
  }

  public List<Task<NodeDataRequest>> persist(
      final List<Task<NodeDataRequest>> tasks,
      final BlockHeader blockHeader,
      final WorldDownloadState<NodeDataRequest> downloadState) {
    try {
      final WorldStateKeyValueStorage.Updater updater = worldStateStorageCoordinator.updater();
      tasks.stream()
          .map(
              task -> {
                enqueueChildren(task, downloadState);
                return task;
              })
          .map(Task::getData)
          .filter(request -> request.getData() != null)
          .forEach(
              request -> {
                if (isRootState(blockHeader, request)) {
                  downloadState.setRootNodeData(request.getData());
                } else {
                  request.persist(updater);
                }
              });
      updater.commit();
    } catch (StorageException storageException) {
      if (canRetryOnError(storageException)) {
        // We reset the task by setting it to null. This way, it is considered as failed by the
        // pipeline, and it will attempt to execute it again later.
        if (errorCountAtThreshold()) {
          LOG.info(
              "Encountered {} retryable RocksDB errors, latest error message {}",
              getRetryableErrorCounter(),
              storageException.getMessage());
        }
        tasks.forEach(nodeDataRequestTask -> nodeDataRequestTask.getData().setData(null));
      } else {
        throw storageException;
      }
    }
    return tasks;
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }

  private void enqueueChildren(
      final Task<NodeDataRequest> task, final WorldDownloadState<NodeDataRequest> downloadState) {
    final NodeDataRequest request = task.getData();
    downloadState.enqueueRequests(request.getChildRequests(worldStateStorageCoordinator));
  }
}
