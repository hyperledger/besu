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
package org.hyperledger.besu.plugin.services.storage.leveldb;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbUtils.getColumnKey;
import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbUtils.getKeyAfterColumn;
import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbUtils.removeKeyPrefix;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeveldbSegmentedKeyValueStorage
    implements SegmentedKeyValueStorage<SegmentIdentifier> {
  private static final Logger LOG = LoggerFactory.getLogger(LeveldbSegmentedKeyValueStorage.class);

  public static final int MAX_OPEN_FILES = 128;

  private final Set<LevelDbTransaction> openTransactions = new HashSet<>();
  private final Set<DBIterator> openIterators = new HashSet<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final DB db;

  public LeveldbSegmentedKeyValueStorage(final DB db) {
    this.db = db;
  }

  public static SegmentedKeyValueStorage<?> create(
      final Path storagePath, final List<SegmentIdentifier> segments) {
    checkArgument(
        segments.stream().map(SegmentIdentifier::getId).map(Bytes::wrap).distinct().count()
            == segments.size(),
        "Segment IDs are not distinct");
    final Options options = new Options().createIfMissing(true).maxOpenFiles(MAX_OPEN_FILES);

    try {
      final DB db = JniDBFactory.factory.open(storagePath.toFile(), options);
      return new LeveldbSegmentedKeyValueStorage(db);
    } catch (final IOException e) {
      throw new StorageException("Failed to open database", e);
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    List<DBIterator> openIteratorsSnapshot;
    List<LevelDbTransaction> openTransactionsSnapshot;
    synchronized (this) {
      openIteratorsSnapshot = new ArrayList<>(openIterators);
      openTransactionsSnapshot = new ArrayList<>(openTransactions);
    }
    openIteratorsSnapshot.forEach(this::closeIterator);
    openTransactionsSnapshot.forEach(LevelDbTransaction::rollback);
    db.close();
  }

  void assertOpen() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed LevelDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  @Override
  public SegmentIdentifier getSegmentIdentifierByName(final SegmentIdentifier segment) {
    return segment;
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    assertOpen();
    return Optional.ofNullable(db.get(getColumnKey(segment, key)));
  }

  @Override
  public Transaction<SegmentIdentifier> startTransaction() throws StorageException {
    assertOpen();
    return new LevelDbTransaction(this, db, db.createWriteBatch());
  }

  synchronized void onTransactionClosed(final LevelDbTransaction transaction) {
    openTransactions.remove(transaction);
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segmentHandle) {
    return streamRawKeys(segmentHandle).map(key -> removeKeyPrefix(segmentHandle, key));
  }

  private Stream<byte[]> streamRawKeys(final SegmentIdentifier segmentHandle) {
    assertOpen();
    // Note that the "to" key is actually after the end of the column and iteration is inclusive.
    // Fortunately, we know that the "to" key can't exist because it is just the column ID with an
    // empty item key and empty item keys are not allowed.
    final byte[] fromBytes = segmentHandle.getId();
    final byte[] toBytes = getKeyAfterColumn(segmentHandle);
    assertOpen();
    final DBIterator iterator = createIterator();
    iterator.seek(fromBytes);
    return new LevelDbRawKeyIterator(this, iterator, segmentHandle, toBytes)
        .toStream()
        .onClose(() -> closeIterator(iterator));
  }

  private DBIterator createIterator() {
    final DBIterator iterator = db.iterator(new ReadOptions().fillCache(false));
    openIterators.add(iterator);
    return iterator;
  }

  private synchronized void closeIterator(final DBIterator iterator) {
    if (!openIterators.remove(iterator)) {
      return;
    }
    try {
      iterator.close();
    } catch (final IOException e) {
      LOG.error("Failed to close leveldb iterator", e);
    }
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segmentHandle, final byte[] key)
      throws StorageException {
    assertOpen();
    db.delete(getColumnKey(segmentHandle, key));
    return true;
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final SegmentIdentifier segmentHandle, final Predicate<byte[]> returnCondition) {
    return streamKeys(segmentHandle).filter(returnCondition).collect(toUnmodifiableSet());
  }

  @Override
  public void clear(final SegmentIdentifier segmentHandle) {
    assertOpen();
    final WriteBatch writeBatch = db.createWriteBatch();
    streamRawKeys(segmentHandle).forEach(writeBatch::delete);
    db.write(writeBatch);
  }
}
