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
import java.util.function.Supplier;

public class BonsaiIntermediateCommitCountUpdater<WORLDSTATE extends WorldStateStorage> {

  private WorldStateStorage.Updater updater;

  private final Integer maxElements;
  final AtomicInteger nbElements = new AtomicInteger();
  private final Supplier<WorldStateStorage.Updater> updaterSupplier;

  public BonsaiIntermediateCommitCountUpdater(
      final Supplier<WorldStateStorage.Updater> updaterSupplier, final Integer maxElements) {
    this.updaterSupplier = updaterSupplier;
    this.updater = updaterSupplier.get();
    this.maxElements = maxElements;
  }

  public WorldStateStorage.Updater getUpdater() {
    if (nbElements.incrementAndGet() % maxElements == 0) {
      updater.commit();
      updater = updaterSupplier.get();
    }
    return updater;
  }

  public Integer close() {
    updater.commit();
    return nbElements.get();
  }
}
