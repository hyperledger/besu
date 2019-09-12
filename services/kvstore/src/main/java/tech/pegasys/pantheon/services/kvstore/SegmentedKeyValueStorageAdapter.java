/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.kvstore;

import tech.pegasys.pantheon.plugin.services.exception.StorageException;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

public class SegmentedKeyValueStorageAdapter<S> implements KeyValueStorage {

  private final S segmentHandle;
  private final SegmentedKeyValueStorage<S> storage;

  public SegmentedKeyValueStorageAdapter(
      final SegmentIdentifier segment, final SegmentedKeyValueStorage<S> storage) {
    this.segmentHandle = storage.getSegmentIdentifierByName(segment);
    this.storage = storage;
  }

  @Override
  public void clear() {
    storage.clear(segmentHandle);
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return storage.containsKey(segmentHandle, key);
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return storage.get(segmentHandle, key);
  }

  @Override
  public long removeAllKeysUnless(final Predicate<byte[]> retainCondition) throws StorageException {
    return storage.removeUnless(segmentHandle, retainCondition);
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    final SegmentedKeyValueStorage.Transaction<S> transaction = storage.startTransaction();
    return new KeyValueStorageTransaction() {

      @Override
      public void put(final byte[] key, final byte[] value) {
        transaction.put(segmentHandle, key, value);
      }

      @Override
      public void remove(final byte[] key) {
        transaction.remove(segmentHandle, key);
      }

      @Override
      public void commit() throws StorageException {
        transaction.commit();
      }

      @Override
      public void rollback() {
        transaction.rollback();
      }
    };
  }
}
