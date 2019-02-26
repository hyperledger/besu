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

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;

/** Service provided by pantheon to facilitate persistent data storage. */
public interface KeyValueStorage extends Closeable {

  /**
   * @param key Index into persistent data repository.
   * @return The value persisted at the key index.
   */
  Optional<BytesValue> get(BytesValue key) throws StorageException;

  /**
   * Begins a transaction. Returns a transaction object that can be updated and committed.
   *
   * @return An object representing the transaction.
   */
  Transaction startTransaction() throws StorageException;

  class Entry {
    private final BytesValue key;
    private final BytesValue value;

    private Entry(final BytesValue key, final BytesValue value) {
      this.key = key;
      this.value = value;
    }

    public static Entry create(final BytesValue key, final BytesValue value) {
      return new Entry(key, value);
    }

    public BytesValue getKey() {
      return key;
    }

    public BytesValue getValue() {
      return value;
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Entry)) {
        return false;
      }
      final Entry other = (Entry) obj;
      return Objects.equals(getKey(), other.getKey())
          && Objects.equals(getValue(), other.getValue());
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }
  }

  class StorageException extends RuntimeException {
    public StorageException(final Throwable t) {
      super(t);
    }
  }

  /**
   * Represents a set of changes to be committed atomically. A single transaction is not
   * thread-safe, but multiple transactions can execute concurrently.
   */
  interface Transaction {

    /**
     * Add the given key-value pair to the set of updates to be committed.
     *
     * @param key The key to set / modify.
     * @param value The value to be set.
     */
    void put(BytesValue key, BytesValue value);

    /**
     * Schedules the given key to be deleted from storage.
     *
     * @param key The key to delete
     */
    void remove(BytesValue key);

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

  abstract class AbstractTransaction implements Transaction {

    private boolean active = true;

    @Override
    public final void put(final BytesValue key, final BytesValue value) {
      checkState(active, "Cannot invoke put() on a completed transaction.");
      doPut(key, value);
    }

    @Override
    public final void remove(final BytesValue key) {
      checkState(active, "Cannot invoke remove() on a completed transaction.");
      doRemove(key);
    }

    @Override
    public final void commit() throws StorageException {
      checkState(active, "Cannot commit a completed transaction.");
      active = false;
      doCommit();
    }

    @Override
    public final void rollback() {
      checkState(active, "Cannot rollback a completed transaction.");
      active = false;
      doRollback();
    }

    protected abstract void doPut(BytesValue key, BytesValue value);

    protected abstract void doRemove(BytesValue key);

    protected abstract void doCommit() throws StorageException;

    protected abstract void doRollback();
  }
}
