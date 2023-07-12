package org.hyperledger.besu.plugin.services.storage;

public interface SnappedSegmentedKeyValueStorage<S> extends SegmentedKeyValueStorage<S> {
  /**
   * Gets snapshot transaction.
   *
   * @return the snapshot transaction
   */
  KeyValueStorageTransaction getSnapshotTransaction();

}
