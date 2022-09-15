package org.hyperledger.besu.plugin.services.storage;

public interface SnappedKeyValueStorage extends KeyValueStorage {

  KeyValueStorageTransaction getSnapshotTransaction();
}
