/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbUtils.getColumnKey;

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.ReentrantLock;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;

public class LevelDbTransaction implements Transaction<SegmentIdentifier> {

  private final ReentrantLock lock = new ReentrantLock();
  private boolean closed = false;
  private final LeveldbSegmentedKeyValueStorage dbInstance;
  private final DB db;
  private final WriteBatch writeBatch;

  public LevelDbTransaction(
      final LeveldbSegmentedKeyValueStorage dbInstance, final DB db, final WriteBatch writeBatch) {
    this.dbInstance = dbInstance;
    this.db = db;
    this.writeBatch = writeBatch;
  }

  @Override
  public void put(final SegmentIdentifier segment, final byte[] key, final byte[] value) {
    applyUpdate(() -> writeBatch.put(getColumnKey(segment, key), value));
  }

  @Override
  public void remove(final SegmentIdentifier segment, final byte[] key) {
    applyUpdate(() -> writeBatch.delete(getColumnKey(segment, key)));
  }

  @Override
  public void commit() {
    applyUpdate(
        () -> {
          try {
            db.write(writeBatch);
          } finally {
            close();
          }
        });
  }

  @Override
  public void rollback() {
    close();
  }

  private void close() {
    lock.lock();
    try {
      if (closed) {
        throw new IllegalStateException("Already closed transaction");
      }
      closed = true;
      writeBatch.close();
      dbInstance.onTransactionClosed(this);
    } catch (IOException e) {
      dbInstance.onTransactionClosed(this);
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  private void applyUpdate(final Runnable operation) {
    lock.lock();
    try {
      assertOpen();
      operation.run();
    } finally {
      lock.unlock();
    }
  }

  private void assertOpen() {
    if (closed) {
      throw new IllegalStateException("LevelDb transaction has been closed");
    }
    dbInstance.assertOpen();
  }
}
