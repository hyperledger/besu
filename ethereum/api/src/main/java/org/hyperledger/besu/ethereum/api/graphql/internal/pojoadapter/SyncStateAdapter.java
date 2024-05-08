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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.plugin.data.SyncStatus;

/** The type Sync state adapter. */
@SuppressWarnings("unused") // reflected by GraphQL
public class SyncStateAdapter {
  private final SyncStatus syncStatus;

  /**
   * Instantiates a new Sync state adapter.
   *
   * @param syncStatus the sync status
   */
  public SyncStateAdapter(final SyncStatus syncStatus) {
    this.syncStatus = syncStatus;
  }

  /**
   * Gets starting block.
   *
   * @return the starting block
   */
  public Long getStartingBlock() {
    return syncStatus.getStartingBlock();
  }

  /**
   * Gets current block.
   *
   * @return the current block
   */
  public Long getCurrentBlock() {
    return syncStatus.getCurrentBlock();
  }

  /**
   * Gets highest block.
   *
   * @return the highest block
   */
  public Long getHighestBlock() {
    return syncStatus.getHighestBlock();
  }
}
