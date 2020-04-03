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

import java.util.Optional;

@SuppressWarnings("unused") // reflected by GraphQL
public class SyncStateAdapter {
  private final SyncStatus syncStatus;

  public SyncStateAdapter(final SyncStatus syncStatus) {
    this.syncStatus = syncStatus;
  }

  public Optional<Long> getStartingBlock() {
    return Optional.of(syncStatus.getStartingBlock());
  }

  public Optional<Long> getCurrentBlock() {
    return Optional.of(syncStatus.getCurrentBlock());
  }

  public Optional<Long> getHighestBlock() {
    return Optional.of(syncStatus.getHighestBlock());
  }

  public Optional<Long> getPulledStates() {
    return syncStatus.getPulledStates();
  }

  public Optional<Long> getKnownStates() {
    return syncStatus.getKnownStates();
  }
}
