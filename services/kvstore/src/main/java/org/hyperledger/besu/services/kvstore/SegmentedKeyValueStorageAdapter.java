/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.services.kvstore;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SegmentedKeyValueStorageAdapter<S> implements KeyValueStorage {
  private final S segmentHandle;
  private final SegmentedKeyValueStorage<S> storage;

  public SegmentedKeyValueStorageAdapter(
      final SegmentIdentifier segment, final SegmentedKeyValueStorage<S> storage) {
    segmentHandle = storage.getSegmentIdentifierByName(segment);
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
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return storage.getAllKeysThat(segmentHandle, returnCondition);
  }

  @Override
  public Stream<byte[]> streamKeys() {
    return storage.streamKeys(segmentHandle);
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    return storage.tryDelete(segmentHandle, key);
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
