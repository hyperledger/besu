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
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.concurrent.atomic.AtomicInteger;

public class BonsaiIntermediateCommitCountUpdater<WORLDSTATE extends WorldStateStorage> {

  private final WORLDSTATE worldStateStorage;

  private final Integer maxElements;
  final AtomicInteger nbElements = new AtomicInteger();
  private WorldStateStorage.Updater updater;

  public BonsaiIntermediateCommitCountUpdater(
      final WORLDSTATE worldStateStorage, final Integer maxElements) {
    this.updater = worldStateStorage.updater();
    this.worldStateStorage = worldStateStorage;
    this.maxElements = maxElements;
  }

  public WorldStateStorage.Updater getUpdater() {
    if (nbElements.incrementAndGet() % maxElements == 0) {
      updater.commit();
      updater = worldStateStorage.updater();
    }
    return updater;
  }

  public Integer close() {
    updater.commit();
    return nbElements.get();
  }
}
