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
package tech.pegasys.pantheon.ethereum.eth.sync.state;

import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;

public final class FastSyncState {
  private long fastSyncTargetBlockNumber = -1;

  private final SynchronizerConfiguration config;

  public FastSyncState(final SynchronizerConfiguration config) {
    this.config = config;
  }

  /**
   * Registers the chain height that we're trying to sync to.
   *
   * @param blockNumber the height of the chain we are syncing to.
   */
  public void setFastSyncChainTarget(final long blockNumber) {
    fastSyncTargetBlockNumber = blockNumber;
  }

  /** @return the block number at which we switch from fast sync to full sync */
  public long pivot() {
    return Math.max(fastSyncTargetBlockNumber - config.fastSyncPivotDistance(), 0);
  }
}
