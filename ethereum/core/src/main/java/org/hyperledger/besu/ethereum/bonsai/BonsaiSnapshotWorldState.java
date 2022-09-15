package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate. It is not
 * able to commit or persist however. This is useful for async blockchain opperations like block
 * creation and/or point-in-time queries.
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
            ((SnappableKeyValueStorage) parentWorldStateStorage.trieLogStorage).takeSnapshot()));
  }

  @Override
  public MutableWorldState copy() {
    // TODO: we are transaction based, so perhaps we return a new transaction based worldstate...
    //       for now lets just return ourselves
    return this;
  }
}
