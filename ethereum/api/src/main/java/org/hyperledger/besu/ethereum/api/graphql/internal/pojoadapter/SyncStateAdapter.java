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

/**
 * The SyncStateAdapter class provides methods to retrieve the synchronization status of the
 * blockchain.
 *
 * <p>This class is used to adapt a SyncStatus object into a format that can be used by GraphQL. The
 * SyncStatus object is provided at construction time.
 *
 * <p>The class provides methods to retrieve the starting block, current block, and highest block of
 * the synchronization status.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class SyncStateAdapter {
  private final SyncStatus syncStatus;

  /**
   * Constructs a new SyncStateAdapter object.
   *
   * @param syncStatus the SyncStatus object to adapt.
   */
  public SyncStateAdapter(final SyncStatus syncStatus) {
    this.syncStatus = syncStatus;
  }

  /**
   * Returns the starting block of the synchronization status.
   *
   * @return the starting block of the synchronization status.
   */
  public Long getStartingBlock() {
    return syncStatus.getStartingBlock();
  }

  /**
   * Returns the current block of the synchronization status.
   *
   * @return the current block of the synchronization status.
   */
  public Long getCurrentBlock() {
    return syncStatus.getCurrentBlock();
  }

  /**
   * Returns the highest block of the synchronization status.
   *
   * @return the highest block of the synchronization status.
   */
  public Long getHighestBlock() {
    return syncStatus.getHighestBlock();
  }
}
