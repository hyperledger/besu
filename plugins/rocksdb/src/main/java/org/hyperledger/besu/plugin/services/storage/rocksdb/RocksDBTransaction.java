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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBTransaction.RetryableRocksDBAction.maybeRetryRocksDBAction;

import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.function.Function;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RocksDb transaction. */
public class RocksDBTransaction implements SegmentedKeyValueStorageTransaction {
  private static final Logger logger = LoggerFactory.getLogger(RocksDBTransaction.class);
  private static final String ERR_NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
  private static final String ERR_BUSY = "Busy";
  private static final String ERR_LOCK_TIMED_OUT = "TimedOut(LockTimeout)";
  private static final int DEFAULT_MAX_RETRIES = 3;

  private final RocksDBMetrics metrics;
  private final Transaction innerTx;
  private final WriteOptions options;
  private final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper;

  /**
   * Instantiates a new RocksDb transaction.
   *
   * @param columnFamilyMapper mapper from segment identifier to column family handle
   * @param innerTx the inner tx
   * @param options the options
   * @param metrics the metrics
   */
  public RocksDBTransaction(
      final Function<SegmentIdentifier, ColumnFamilyHandle> columnFamilyMapper,
      final Transaction innerTx,
      final WriteOptions options,
      final RocksDBMetrics metrics) {
    this.columnFamilyMapper = columnFamilyMapper;
    this.innerTx = innerTx;
    this.options = options;
    this.metrics = metrics;
  }

  @Override
  public void put(final SegmentIdentifier segmentId, final byte[] key, final byte[] value) {
    try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
      innerTx.put(columnFamilyMapper.apply(segmentId), key, value);
    } catch (final RocksDBException e) {
      maybeRetryRocksDBAction(
          e,
          0,
          DEFAULT_MAX_RETRIES,
          () -> innerTx.put(columnFamilyMapper.apply(segmentId), key, value));
    }
  }

  @Override
  public void remove(final SegmentIdentifier segmentId, final byte[] key) {
    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      innerTx.delete(columnFamilyMapper.apply(segmentId), key);
    } catch (final RocksDBException e) {
      maybeRetryRocksDBAction(
          e,
          0,
          DEFAULT_MAX_RETRIES,
          () -> innerTx.delete(columnFamilyMapper.apply(segmentId), key));
    }
  }

  @Override
  public void commit() throws StorageException {
    try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
      innerTx.commit();
    } catch (final RocksDBException e) {
      maybeRetryRocksDBAction(e, 0, DEFAULT_MAX_RETRIES, innerTx::commit);
    } finally {
      close();
    }
  }

  @Override
  public void rollback() {
    try {
      innerTx.rollback();
    } catch (final RocksDBException e) {
      maybeRetryRocksDBAction(e, 0, DEFAULT_MAX_RETRIES, innerTx::rollback);
    } finally {
      metrics.getRollbackCount().inc();
      close();
    }
  }

  private void close() {
    innerTx.close();
    options.close();
  }

  @FunctionalInterface
  interface RetryableRocksDBAction {
    void retry() throws RocksDBException;

    static void maybeRetryRocksDBAction(
        final RocksDBException ex,
        final int attemptNumber,
        final int retryLimit,
        final RetryableRocksDBAction retryAction) {

      if (ex.getMessage().contains(ERR_NO_SPACE_LEFT_ON_DEVICE)) {
        logger.error(ex.getMessage());
        System.exit(0);
      }
      if (attemptNumber <= retryLimit) {
        if (ex.getMessage().contains(ERR_BUSY) || ex.getMessage().contains(ERR_LOCK_TIMED_OUT)) {
          logger.warn(
              "RocksDB Transient exception caught on attempt {} of {}, retrying.\n"
                  + "\tmessage: {}",
              attemptNumber,
              retryLimit,
              ex.getMessage());
          try {
            retryAction.retry();
          } catch (RocksDBException ex2) {
            maybeRetryRocksDBAction(ex2, attemptNumber + 1, retryLimit, retryAction);
          }
        }
      } else {
        throw new StorageException(ex);
      }
    }
  }
}
