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
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will adapt a SegmentedKeyValueStorage to a KeyValueStorage instance.
 *
 * @param <S> type parameter for the segment handle
 */
public class SegmentedKeyValueStorageAdapter<S> implements KeyValueStorage {

  private static final Logger LOG = LoggerFactory.getLogger(SegmentedKeyValueStorageAdapter.class);
  private final S segmentHandle;
  private final SegmentedKeyValueStorage<S> storage;

  /**
   * Instantiates a new Segmented key value storage adapter for a single segment.
   *
   * @param segment the segment
   * @param storage the storage
   */
  public SegmentedKeyValueStorageAdapter(
      final SegmentIdentifier segment, final SegmentedKeyValueStorage<S> storage) {
    segmentHandle = storage.getSegmentIdentifierByName(segment);
    this.storage = storage;
  }

  @Override
  public void clear() {
    throwIfClosed();
    storage.clear(segmentHandle);
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    throwIfClosed();
    return storage.containsKey(segmentHandle, key);
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    throwIfClosed();
    return storage.get(segmentHandle, key);
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    throwIfClosed();
    return storage.getAllKeysThat(segmentHandle, returnCondition);
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    throwIfClosed();
    return storage.getAllValuesFromKeysThat(segmentHandle, returnCondition);
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream() {
    throwIfClosed();
    return storage.stream(segmentHandle);
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(final byte[] startKey) throws StorageException {
    return storage.streamFromKey(segmentHandle, startKey);
  }

  @Override
  public Stream<byte[]> streamKeys() {
    throwIfClosed();
    return storage.streamKeys(segmentHandle);
  }

  @Override
  public boolean tryDelete(final byte[] key) {
    throwIfClosed();
    return storage.tryDelete(segmentHandle, key);
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    return storage.startTransaction();
  }

  @Override
  public boolean isClosed() {
    return storage.isClosed();
  }

  private void throwIfClosed() {
    if (storage.isClosed()) {
      LOG.error("Attempting to use a closed Storage instance.");
      throw new StorageException("Storage has been closed");
    }
  }
}
