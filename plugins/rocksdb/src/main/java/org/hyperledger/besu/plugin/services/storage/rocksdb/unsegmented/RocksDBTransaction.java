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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

public class RocksDBTransaction implements KeyValueStorageTransaction {
  private static final Logger logger = LogManager.getLogger();
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";

  private final RocksDBMetrics metrics;
  private final Transaction innerTx;
  private final WriteOptions options;
  private final Tracer tracer;

  RocksDBTransaction(
      final Transaction innerTx,
      final WriteOptions options,
      final RocksDBMetrics metrics,
      final Tracer tracer) {
    this.innerTx = innerTx;
    this.options = options;
    this.metrics = metrics;
    this.tracer = tracer;
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    Span span = tracer.spanBuilder("put").setSpanKind(Span.Kind.INTERNAL).startSpan();
    try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
      innerTx.put(key, value);
    } catch (final RocksDBException e) {
      span.recordException(e);
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        logger.error(e.getMessage());
        span.end();
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      span.end();
    }
  }

  @Override
  public void remove(final byte[] key) {
    Span span = tracer.spanBuilder("remove").setSpanKind(Span.Kind.INTERNAL).startSpan();
    try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
      innerTx.delete(key);
    } catch (final RocksDBException e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        logger.error(e.getMessage());
        span.end();
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      span.end();
    }
  }

  @Override
  public void commit() throws StorageException {
    Span span = tracer.spanBuilder("commit").setSpanKind(Span.Kind.INTERNAL).startSpan();
    try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
      innerTx.commit();
    } catch (final RocksDBException e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        logger.error(e.getMessage());
        span.end();
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      close();
      span.end();
    }
  }

  @Override
  public void rollback() {
    Span span = tracer.spanBuilder("commit").setSpanKind(Span.Kind.INTERNAL).startSpan();
    try {
      innerTx.rollback();
      metrics.getRollbackCount().inc();
    } catch (final RocksDBException e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        logger.error(e.getMessage());
        span.end();
        System.exit(0);
      }
      throw new StorageException(e);
    } finally {
      close();
      span.end();
    }
  }

  private void close() {
    innerTx.close();
    options.close();
  }
}
