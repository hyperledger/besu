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
import org.hyperledger.besu.services.tasks.Task;

public class StubTask implements Task<SnapDataRequest> {

  private final SnapDataRequest data;
  private boolean completed = false;
  private boolean failed = false;

  public StubTask(final SnapDataRequest data) {
    this.data = data;
  }

  @Override
  public SnapDataRequest getData() {
    return data;
  }

  @Override
  public void markCompleted() {
    completed = true;
  }

  @Override
  public void markFailed() {
    failed = true;
  }

  public boolean isCompleted() {
    return completed;
  }

  public boolean isFailed() {
    return failed;
  }
}
