package org.hyperledger.besu.plugin.services.storage;

public interface SnappableKeyValueStorage extends KeyValueStorage {

  SnappedKeyValueStorage takeSnapshot();
}
