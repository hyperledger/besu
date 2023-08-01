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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

/** Service provided by Besu to facilitate persistent data storage. */
public interface SegmentedKeyValueStorage extends Closeable {

  /**
   * Get the value from the associated segment and key.
   *
   * @param segment the segment
   * @param key Index into persistent data repository.
   * @return The value persisted at the key index.
   * @throws StorageException the storage exception
   */
  Optional<byte[]> get(SegmentIdentifier segment, byte[] key) throws StorageException;

  /**
   * Contains key.
   *
   * @param segment the segment
   * @param key the key
   * @return the boolean
   * @throws StorageException the storage exception
   */
  default boolean containsKey(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    return get(segment, key).isPresent();
  }

  /**
   * Begins a transaction. Returns a transaction object that can be updated and committed.
   *
   * @return An object representing the transaction.
   * @throws StorageException the storage exception
   */
  SegmentedKeyValueStorageTransaction startTransaction() throws StorageException;

  /**
   * Returns a stream of all keys for the segment.
   *
   * @param segmentIdentifier The segment identifier whose keys we want to stream.
   * @return A stream of all keys in the specified segment.
   */
  Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentIdentifier);

  /**
   * Returns a stream of key-value pairs starting from the specified key. This method is used to
   * retrieve a stream of data from the storage, starting from the given key. If no data is
   * available from the specified key onwards, an empty stream is returned.
   *
   * @param segmentIdentifier The segment identifier whose keys we want to stream.
   * @param startKey The key from which the stream should start.
   * @return A stream of key-value pairs starting from the specified key.
   */
  Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segmentIdentifier, final byte[] startKey);

  /**
   * Stream keys.
   *
   * @param segmentIdentifier the segment identifier
   * @return the stream
   */
  Stream<byte[]> streamKeys(final SegmentIdentifier segmentIdentifier);

  /**
   * Delete the value corresponding to the given key in the given segment if a write lock can be
   * instantly acquired on the underlying storage. Do nothing otherwise.
   *
   * @param segmentIdentifier The segment identifier whose keys we want to stream.
   * @param key The key to delete.
   * @return false if the lock on the underlying storage could not be instantly acquired, true
   *     otherwise
   * @throws StorageException any problem encountered during the deletion attempt.
   */
  boolean tryDelete(SegmentIdentifier segmentIdentifier, byte[] key) throws StorageException;

  /**
   * Gets all keys that matches condition.
   *
   * @param segmentIdentifier the segment identifier
   * @param returnCondition the return condition
   * @return set of result
   */
  Set<byte[]> getAllKeysThat(
      SegmentIdentifier segmentIdentifier, Predicate<byte[]> returnCondition);

  /**
   * Gets all values from keys that matches condition.
   *
   * @param segmentIdentifier the segment identifier
   * @param returnCondition the return condition
   * @return the set of result
   */
  Set<byte[]> getAllValuesFromKeysThat(
      final SegmentIdentifier segmentIdentifier, Predicate<byte[]> returnCondition);

  /**
   * Clear.
   *
   * @param segmentIdentifier the segment identifier
   */
  void clear(SegmentIdentifier segmentIdentifier);

  /**
   * Whether the underlying storage is closed.
   *
   * @return boolean indicating whether the underlying storage is closed.
   */
  boolean isClosed();
}
