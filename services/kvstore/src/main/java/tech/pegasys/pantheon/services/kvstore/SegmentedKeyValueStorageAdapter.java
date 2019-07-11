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

import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage.Segment;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

public class SegmentedKeyValueStorageAdapter<S> implements KeyValueStorage {

  private final S segmentHandle;
  private final SegmentedKeyValueStorage<S> storage;

  public SegmentedKeyValueStorageAdapter(
      final Segment segment, final SegmentedKeyValueStorage<S> storage) {
    this.segmentHandle = storage.getSegmentIdentifierByName(segment);
    this.storage = storage;
  }

  @Override
  public void clear() {
    storage.clear(segmentHandle);
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public boolean containsKey(final BytesValue key) throws StorageException {
    return storage.containsKey(segmentHandle, key);
  }

  @Override
  public Optional<BytesValue> get(final BytesValue key) throws StorageException {
    return storage.get(segmentHandle, key);
  }

  @Override
  public long removeUnless(final Predicate<BytesValue> inUseCheck) {
    return storage.removeUnless(segmentHandle, inUseCheck);
  }

  @Override
  public Transaction startTransaction() throws StorageException {
    final SegmentedKeyValueStorage.Transaction<S> transaction = storage.startTransaction();
    return new Transaction() {
      @Override
      public void put(final BytesValue key, final BytesValue value) {
        transaction.put(segmentHandle, key, value);
      }

      @Override
      public void remove(final BytesValue key) {
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
