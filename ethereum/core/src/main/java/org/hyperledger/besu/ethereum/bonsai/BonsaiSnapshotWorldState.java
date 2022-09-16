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

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SnapshotMutableWorldState;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate. It is able to
 * commit/perist as a trielog layer only. This is useful for async blockchain opperations like block
 * creation and/or point-in-time queries since the snapshot worldstate is fully isolated from the
 * main BonsaiPersistedWorldState.
 */
public class BonsaiSnapshotWorldState extends BonsaiInMemoryWorldState
    implements SnapshotMutableWorldState {
  //  private static final Logger LOG = LoggerFactory.getLogger(BonsaiSnapshotWorldState.class);

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
  public MutableWorldState copy() {
    // The copy method interface doesn't make much sense in this context since
    // this class is a snapshot based copy of the worldstate and *is* an
    // independent/isolated version of the worldstate, so just return ourselves.
    return this;
  }

  @Override
  public void close() throws Exception {
    // TODO: close and free snapshot transactions
    this.accountSnap.getSnapshotTransaction().rollback();
    this.codeSnap.getSnapshotTransaction().rollback();
    this.storageSnap.getSnapshotTransaction().rollback();
    this.trieBranchSnap.getSnapshotTransaction().rollback();
  }
}
