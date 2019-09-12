/*
 * Copyright 2018 ConsenSys AG.
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
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Service provided by pantheon to facilitate persistent data storage.
 *
 * @param <S> the segment identifier type
 */
public interface SegmentedKeyValueStorage<S> extends Closeable {

  S getSegmentIdentifierByName(SegmentIdentifier segment);

  /**
   * @param segment the segment
   * @param key Index into persistent data repository.
   * @return The value persisted at the key index.
   */
  Optional<byte[]> get(S segment, byte[] key) throws StorageException;

  default boolean containsKey(final S segment, final byte[] key) throws StorageException {
    return get(segment, key).isPresent();
  }

  /**
   * Begins a transaction. Returns a transaction object that can be updated and committed.
   *
   * @return An object representing the transaction.
   */
  Transaction<S> startTransaction() throws StorageException;

  long removeUnless(S segmentHandle, Predicate<byte[]> inUseCheck);

  void clear(S segmentHandle);

  /**
   * Represents a set of changes to be committed atomically. A single transaction is not
   * thread-safe, but multiple transactions can execute concurrently.
   *
   * @param <S> the segment identifier type
   */
  interface Transaction<S> {

    /**
     * Add the given key-value pair to the set of updates to be committed.
     *
     * @param segment the database segment
     * @param key The key to set / modify.
     * @param value The value to be set.
     */
    void put(S segment, byte[] key, byte[] value);

    /**
     * Schedules the given key to be deleted from storage.
     *
     * @param segment the database segment
     * @param key The key to delete
     */
    void remove(S segment, byte[] key);

    /**
     * Atomically commit the set of changes contained in this transaction to the underlying
     * key-value storage from which this transaction was started. After committing, the transaction
     * is no longer usable and will throw exceptions if modifications are attempted.
     */
    void commit() throws StorageException;

    /**
     * Cancel this transaction. After rolling back, the transaction is no longer usable and will
     * throw exceptions if modifications are attempted.
     */
    void rollback();
  }
}
