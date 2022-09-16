package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate. It is able
 * to commit/perist as a trielog layer only. This is useful for async blockchain opperations like block
 * creation and/or point-in-time queries since the snapshot worldstate is fully isolated from the main
 * BonsaiPersistedWorldState.
 */
public class BonsaiSnapshotWorldState extends BonsaiInMemoryWorldState {
  //  private static final Logger LOG = LoggerFactory.getLogger(BonsaiSnapshotWorldState.class);

  private BonsaiSnapshotWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage snapshotWorldStateStorage) {
    super(archive, snapshotWorldStateStorage);
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
    // TODO: find out if we can stack transaction based snapshots, so perhaps we return a new transaction based
    //  worldstate.  For now we just return ourself rather than a copy.
    return this;
  }
}
