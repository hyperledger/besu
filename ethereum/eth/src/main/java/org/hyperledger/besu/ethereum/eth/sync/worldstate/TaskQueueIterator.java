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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.Iterator;
import java.util.function.Supplier;

public class TaskQueueIterator<REQUEST extends TasksPriorityProvider>
    implements Iterator<Task<REQUEST>> {

  private final WorldDownloadState<? super REQUEST> downloadState;
  private final Supplier<Task<REQUEST>> supplier;

  public TaskQueueIterator(final WorldDownloadState<REQUEST> downloadState) {
    this(downloadState, downloadState::dequeueRequestBlocking);
  }

  public TaskQueueIterator(
      final WorldDownloadState<? super REQUEST> downloadState,
      final Supplier<Task<REQUEST>> supplier) {
    this.supplier = supplier;
    this.downloadState = downloadState;
  }

  @Override
  public boolean hasNext() {
    return downloadState.isDownloading();
  }

  @Override
  public Task<REQUEST> next() {
    return supplier.get();
  }
}
