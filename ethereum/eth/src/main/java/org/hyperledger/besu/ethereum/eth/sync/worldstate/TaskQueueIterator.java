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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.services.tasks.Task;

import java.util.Iterator;

class TaskQueueIterator implements Iterator<Task<NodeDataRequest>> {

  private final WorldDownloadState downloadState;

  public TaskQueueIterator(final WorldDownloadState downloadState) {
    this.downloadState = downloadState;
  }

  @Override
  public boolean hasNext() {
    return downloadState.isDownloading();
  }

  @Override
  public Task<NodeDataRequest> next() {
    return downloadState.dequeueRequestBlocking();
  }
}
