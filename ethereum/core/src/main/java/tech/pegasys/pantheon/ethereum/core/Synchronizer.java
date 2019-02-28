/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.core;

import java.util.Optional;

/** Provides an interface to block synchronization processes. */
public interface Synchronizer {

  void start();

  /**
   * @return the status, based on SyncingResult When actively synchronizing blocks, alternatively
   *     empty
   */
  Optional<SyncStatus> getSyncStatus();

  boolean hasSufficientPeers();

  long observeSyncStatus(final SyncStatusListener listener);

  boolean removeObserver(long observerId);

  @FunctionalInterface
  interface SyncStatusListener {
    void onSyncStatus(final SyncStatus status);
  }
}
