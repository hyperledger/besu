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
package org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;

import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RocksDb transaction. */
public class RocksDBTransaction implements KeyValueStorageTransaction {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDBTransaction.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
  private static final int MAX_BUSY_RETRIES = 5;
  private static final int BUSY_RETRY_MILLISECONDS_INTERVAL = 100;
  private final RocksDBMetrics metrics;
  private final Transaction innerTx;
  private final WriteOptions options;

  /**
   * Instantiates a new RocksDb transaction.
   *
   * @param innerTx the inner tx
   * @param options the options
   * @param metrics the metrics
   */
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
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  @Override
  public void remove(final byte[] key) {
    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      innerTx.delete(key);
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw new StorageException(e);
    }
  }

  @Override
  public void commit() throws StorageException {
    try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
      innerTx.commit();
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      if (e.getStatus().getCode() == Status.Code.Busy) {
        LOG.error(e.getMessage());
        commitRetry(MAX_BUSY_RETRIES);
      }
      throw new StorageException(e);
    } finally {
      close();
    }
  }

  private void commitRetry(final int retries) {
    if (retries == 0) {
      LOG.error("RocksDB Busy - Already retried {} times", MAX_BUSY_RETRIES);
    } else {
      try {
        LOG.debug("Retries left: {}", retries - 1);
        innerTx.rollback();
        metrics.getRollbackCount().inc();
        Thread.sleep(BUSY_RETRY_MILLISECONDS_INTERVAL);
        innerTx.commit();
      } catch (final RocksDBException e) {
        if (e.getStatus().getCode() == Status.Code.Busy) {
          commitRetry(retries - 1);
        }
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void rollback() {
    try {
      innerTx.rollback();
      metrics.getRollbackCount().inc();
    } catch (final RocksDBException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
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
