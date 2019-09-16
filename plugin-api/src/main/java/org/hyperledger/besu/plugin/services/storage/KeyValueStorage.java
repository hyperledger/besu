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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Responsible for storing values against keys.
 *
 * <p>Behaviour expected with regard to key to value mapping is that of a map, one key maps to one
 * value, when a new value is added with an existing key, that key now points at the new value.
 *
 * <p>All keys and values must be non-null.
 */
@Unstable
public interface KeyValueStorage extends Closeable {

  /**
   * Deletes all keys and values from the storage.
   *
   * @throws StorageException problem encountered when attempting to clear storage.
   */
  void clear() throws StorageException;

  /**
   * Whether the key-value storage contains the given key.
   *
   * @param key a key that might be contained in the key-value storage.
   * @return <code>true</code> when the given key is present in keyset, <code>false</code>
   *     otherwise.
   * @throws StorageException problem encountered when interacting with the key set.
   */
  boolean containsKey(byte[] key) throws StorageException;

  /**
   * Retrieves the value associated with a given key.
   *
   * @param key whose associated value is being retrieved.
   * @return an {@link Optional} containing the value associated with the specified key, otherwise
   *     empty.
   * @throws StorageException problem encountered during the retrieval attempt.
   */
  Optional<byte[]> get(byte[] key) throws StorageException;

  /**
   * Performs an evaluation against each key in the store, keeping the entries that pass, removing
   * those that fail.
   *
   * @param retainCondition predicate to evaluate each key against, unless the result is {@code
   *     null}, both the key and associated value must be removed.
   * @return the number of keys removed.
   * @throws StorageException problem encountered when removing data.
   */
  long removeAllKeysUnless(Predicate<byte[]> retainCondition) throws StorageException;

  /**
   * Begins a fresh transaction, for sequencing operations for later atomic execution.
   *
   * @return transaciton to sequence key-value operations.
   * @throws StorageException problem encountered when starting a new transaction.
   */
  KeyValueStorageTransaction startTransaction() throws StorageException;
}
