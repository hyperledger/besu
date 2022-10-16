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
 * This class is similar to BonsaiSnapshotWorldState however it extends BonsaiPersistedWorldstate
 * directly such that it commits/perists directly to the transaction state. The primary difference
 * here is that we use RocksDBSnapshotTransaction (off-heap mem) to accumulate changes rather than
 * BonsaiWorldStateUpdater (on-heap mem)
 */
public class BonsaiSnapshotPersistedWorldState extends BonsaiPersistedWorldState
    implements SnapshotMutableWorldState {

  private final SnappedKeyValueStorage accountSnap;
  private final SnappedKeyValueStorage codeSnap;
  private final SnappedKeyValueStorage storageSnap;
  private final SnappedKeyValueStorage trieBranchSnap;

  private BonsaiSnapshotPersistedWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiSnapshotWorldStateKeyValueStorage snapshotWorldStateStorage) {
    super(archive, snapshotWorldStateStorage);
    this.accountSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.accountStorage;
    this.codeSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.codeStorage;
    this.storageSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.storageStorage;
    this.trieBranchSnap = (SnappedKeyValueStorage) snapshotWorldStateStorage.trieBranchStorage;
  }

  public static BonsaiSnapshotPersistedWorldState create(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage parentWorldStateStorage) {
    return new BonsaiSnapshotPersistedWorldState(
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
    return rootHash(updater.copy());
  }

  public Hash rootHash(final BonsaiWorldStateUpdater localUpdater) {
    final BonsaiWorldStateKeyValueStorage.BonsaiUpdater updater = worldStateStorage.updater();
    try {
      final Hash calculatedRootHash = calculateRootHash(updater, localUpdater);
      return Hash.wrap(calculatedRootHash);
    } finally {
      updater.rollback();
    }
  }

  @Override
  public MutableWorldState copy() {
    // return a clone-based copy of worldstate storage
    return new BonsaiSnapshotPersistedWorldState(
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
