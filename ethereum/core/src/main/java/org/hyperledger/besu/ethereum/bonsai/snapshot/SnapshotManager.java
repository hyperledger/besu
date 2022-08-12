/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.bonsai.snapshot;

import org.hyperledger.besu.ethereum.bonsai.BonsaiInMemoryWorldState;
import org.hyperledger.besu.plugin.data.Hash;

import java.util.Optional;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class SnapshotManager {

  private final Cache<Hash, BonsaiInMemoryWorldState> snapshots =
      CacheBuilder.newBuilder().maximumSize(512).build();

  public Optional<BonsaiInMemoryWorldState> getSnapshot(final Hash hash) {
    return Optional.ofNullable(snapshots.getIfPresent(hash));
  }

  public boolean isSnapshotAvailable(final Hash hash) {
    return getSnapshot(hash).isPresent();
  }

  public void addSnapshot(final BonsaiInMemoryWorldState bonsaiInMemoryWorldState) {
    snapshots.put(bonsaiInMemoryWorldState.blockHash(), bonsaiInMemoryWorldState);
  }
}
