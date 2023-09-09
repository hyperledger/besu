package org.hyperledger.besu.services.kvstore;

import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import static org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage.SEGMENT_IDENTIFIER;

public class LayeredKeyValueStorageTest extends AbstractSegmentedKeyValueStorageTest {
  @Override
  protected KeyValueStorage createStore() {
    return new SegmentedKeyValueStorageAdapter(SEGMENT_IDENTIFIER,
        createSegmentedStore());
  }

  @Override
  public SegmentedKeyValueStorage createSegmentedStore() {
    return new LayeredKeyValueStorage(new SegmentedInMemoryKeyValueStorage());
  }
}
