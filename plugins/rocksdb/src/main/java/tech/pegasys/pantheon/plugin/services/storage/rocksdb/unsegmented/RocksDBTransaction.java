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
package tech.pegasys.pantheon.plugin.services.storage.rocksdb.unsegmented;

import tech.pegasys.pantheon.plugin.services.exception.StorageException;
import tech.pegasys.pantheon.plugin.services.metrics.OperationTimer;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.RocksDBMetrics;

import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

public class RocksDBTransaction implements KeyValueStorageTransaction {

  private final RocksDBMetrics metrics;
  private final Transaction innerTx;
  private final WriteOptions options;

  RocksDBTransaction(
      final Transaction innerTx, final WriteOptions options, final RocksDBMetrics metrics) {
    this.innerTx = innerTx;
    this.options = options;
    this.metrics = metrics;
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
      innerTx.put(key, value);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void remove(final byte[] key) {
    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      innerTx.delete(key);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void commit() throws StorageException {
    try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
      innerTx.commit();
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    } finally {
      close();
    }
  }

  @Override
  public void rollback() {
    try {
      innerTx.rollback();
      metrics.getRollbackCount().inc();
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
