package org.hyperledger.besu.services.kvstore;

import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

public interface SnappableKeyValueStorage<S> extends KeyValueStorage {

  SegmentedKeyValueStorage<S> takeSnapshot();

}
