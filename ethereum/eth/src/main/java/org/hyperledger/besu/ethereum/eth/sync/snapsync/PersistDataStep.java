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

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

public class PersistDataStep {

  private final WorldStateStorage worldStateStorage;

  public PersistDataStep(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public Task<SnapDataRequest> persist(
      final Task<SnapDataRequest> task, final HealNodeCollection healNodeCollection) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    if (task.getData().getData().isPresent()) {
      task.getData().persist(worldStateStorage, updater, healNodeCollection);
    }
    updater.commit();
    return task;
  }
}
