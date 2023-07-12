package org.hyperledger.besu.plugin.services.storage;

public interface SnappableSegmentedKeyValueStorage<S> extends SegmentedKeyValueStorage<S> {
  /**
   * Take snapshot.
   *
   * @return the snapped key value storage
   */
  SnappedSegmentedKeyValueStorage<S> takeSnapshot();
}
