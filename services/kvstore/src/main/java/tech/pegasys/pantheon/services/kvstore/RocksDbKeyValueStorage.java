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

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class RocksDbKeyValueStorage implements KeyValueStorage, Closeable {

  private static final Logger LOG = LogManager.getLogger();

  private final Options options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  static {
    RocksDB.loadLibrary();
  }

  public static KeyValueStorage create(final Path storageDirectory) throws StorageException {
    return new RocksDbKeyValueStorage(storageDirectory);
  }

  private RocksDbKeyValueStorage(final Path storageDirectory) {
    try {
      options = new Options().setCreateIfMissing(true);
      txOptions = new TransactionDBOptions();
      db = TransactionDB.open(options, txOptions, storageDirectory.toString());
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<BytesValue> get(final BytesValue key) throws StorageException {
    throwIfClosed();
    try {
      return Optional.ofNullable(db.get(key.extractArray())).map(BytesValue::wrap);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void put(final BytesValue key, final BytesValue value) throws StorageException {
    throwIfClosed();
    try {
      db.put(key.extractArray(), value.extractArray());
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void remove(final BytesValue key) throws StorageException {
    throwIfClosed();
    try {
      db.delete(key.extractArray());
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction getStartTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    return new RocksDbTransaction(db.beginTransaction(options), options);
  }

  @Override
  public Stream<Entry> entries() {
    throwIfClosed();
    final RocksIterator rocksIt = db.newIterator();
    rocksIt.seekToFirst();
    return new RocksDbEntryIterator(rocksIt).toStream();
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  /**
   * Iterates over rocksDB key-value entries. Reads from a db snapshot implicitly taken when the
   * RocksIterator passed to the constructor was created.
   *
   * <p>Implements {@link AutoCloseable} and can be used with try-with-resources construct. When
   * transformed to a stream (see {@link #toStream}), iterator is automatically closed when the
   * stream is closed.
   */
  private static class RocksDbEntryIterator implements Iterator<Entry>, AutoCloseable {
    private final RocksIterator rocksIt;
    private volatile boolean closed = false;

    RocksDbEntryIterator(final RocksIterator rocksIt) {
      this.rocksIt = rocksIt;
    }

    @Override
    public boolean hasNext() {
      return rocksIt.isValid();
    }

    @Override
    public Entry next() {
      if (closed) {
        throw new IllegalStateException("Attempt to read from a closed RocksDbEntryIterator.");
      }
      try {
        rocksIt.status();
      } catch (final RocksDBException e) {
        LOG.error("RocksDbEntryIterator encountered a problem while iterating.", e);
      }
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final Entry entry =
          Entry.create(BytesValue.wrap(rocksIt.key()), BytesValue.wrap(rocksIt.value()));
      rocksIt.next();
      return entry;
    }

    public Stream<Entry> toStream() {
      final Spliterator<Entry> split =
          Spliterators.spliteratorUnknownSize(
              this, Spliterator.IMMUTABLE | Spliterator.DISTINCT | Spliterator.NONNULL);

      return StreamSupport.stream(split, false).onClose(this::close);
    }

    @Override
    public void close() {
      rocksIt.close();
      closed = true;
    }
  }

  private static class RocksDbTransaction extends AbstractTransaction {
    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    protected void doPut(final BytesValue key, final BytesValue value) {
      try {
        innerTx.put(key.extractArray(), value.extractArray());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doRemove(final BytesValue key) {
      try {
        innerTx.delete(key.extractArray());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doCommit() throws StorageException {
      try {
        innerTx.commit();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    @Override
    protected void doRollback() {
      try {
        innerTx.rollback();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    private void close() {
      innerTx.close();
      options.close();
    }
  }
}
