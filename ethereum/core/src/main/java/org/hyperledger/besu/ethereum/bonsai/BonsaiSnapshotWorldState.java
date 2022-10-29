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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SnapshotMutableWorldState;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

/**
 * This class extends BonsaiPersistedWorldstate directly such that it commits/perists directly to
 * the transaction state. A SnapshotMutableWorldState is used to accumulate changes to a
 * non-persisting mutable world state rather than writing worldstate changes directly.
 */
public class BonsaiSnapshotWorldState extends BonsaiPersistedWorldState
    implements SnapshotMutableWorldState {

  private final SnappedKeyValueStorage accountSnap;
  private final SnappedKeyValueStorage codeSnap;
  private final SnappedKeyValueStorage storageSnap;
  private final SnappedKeyValueStorage trieBranchSnap;

  private BonsaiSnapshotWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiSnapshotWorldStateKeyValueStorage snapshotWorldStateStorage) {
    super(archive, snapshotWorldStateStorage);
    this.accountSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.accountStorage;
    this.codeSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.codeStorage;
    this.storageSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.storageStorage;
    this.trieBranchSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.trieBranchStorage;
  }

  public static BonsaiSnapshotWorldState create(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage parentWorldStateStorage) {
    return new BonsaiSnapshotWorldState(
        archive,
        new BonsaiSnapshotWorldStateKeyValueStorage(
            ((SnappableKeyValueStorage) parentWorldStateStorage.accountStorage).takeSnapshot(),
            ((SnappableKeyValueStorage) parentWorldStateStorage.codeStorage).takeSnapshot(),
            ((SnappableKeyValueStorage) parentWorldStateStorage.storageStorage).takeSnapshot(),
            ((SnappableKeyValueStorage) parentWorldStateStorage.trieBranchStorage).takeSnapshot(),
            parentWorldStateStorage.trieLogStorage));
  }

  @Override
  public Hash rootHash() {
    if (updater.isDirty()) {
      this.worldStateRootHash = calculateRootHash(worldStateStorage.updater(), updater);
    }
    return this.worldStateRootHash;
  }

  @Override
  public MutableWorldState copy() {
    // return a clone-based copy of worldstate storage
    return new BonsaiSnapshotWorldState(
        archive,
        new BonsaiSnapshotWorldStateKeyValueStorage(
            accountSnap.cloneFromSnapshot(),
            codeSnap.cloneFromSnapshot(),
            storageSnap.cloneFromSnapshot(),
            trieBranchSnap.cloneFromSnapshot(),
            worldStateStorage.trieLogStorage));
  }

  @Override
  public void close() throws Exception {
    accountSnap.close();
    codeSnap.close();
    storageSnap.close();
    trieBranchSnap.close();
  }
}
