/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for opening RocksDB instances with a warning mechanism.
 *
 * <p>This class provides methods to open RocksDB databases ({@link OptimisticTransactionDB} and
 * {@link TransactionDB}) while monitoring the operation's duration. If the database takes longer
 * than a predefined delay (default: 60 seconds) to open, a warning message is logged. This warning
 * helps identify potential delays caused by factors such as RocksDB compaction.
 */
public class RocksDBOpener {

  /**
   * Default delay (in seconds) after which a warning is logged if the database opening operation
   * has not completed.
   */
  public static final int DEFAULT_DELAY = 60;

  /**
   * Warning message logged when the database opening operation takes longer than the predefined
   * delay.
   */
  public static final String WARN_MESSAGE =
      "Opening RocksDB database is taking longer than 60 seconds... "
          + "This may be due to prolonged RocksDB compaction. Please wait until the end of the compaction. "
          + "No action is needed from the user.";

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBOpener.class);

  /**
   * Default constructor.
   *
   * <p>This is a utility class and is not meant to be instantiated directly.
   */
  private RocksDBOpener() {
    // Default constructor for RocksDBOpener
  }

  /**
   * Opens an {@link OptimisticTransactionDB} instance with a warning mechanism.
   *
   * <p>If the database opening operation takes longer than {@link #DEFAULT_DELAY} seconds, a
   * warning message is logged indicating a possible delay caused by compaction.
   *
   * @param options the {@link DBOptions} instance used to configure the database.
   * @param dbPath the file path to the RocksDB database directory.
   * @param columnDescriptors a list of {@link ColumnFamilyDescriptor} objects for the column
   *     families to open.
   * @param columnHandles a list of {@link ColumnFamilyHandle} objects to be populated with the
   *     opened column families.
   * @return an instance of {@link OptimisticTransactionDB}.
   * @throws RocksDBException if the database cannot be opened.
   */
  public static OptimisticTransactionDB openOptimisticTransactionDBWithWarning(
      final DBOptions options,
      final String dbPath,
      final List<ColumnFamilyDescriptor> columnDescriptors,
      final List<ColumnFamilyHandle> columnHandles)
      throws RocksDBException {
    return openDBWithWarning(
        () -> OptimisticTransactionDB.open(options, dbPath, columnDescriptors, columnHandles));
  }

  /**
   * Opens a {@link TransactionDB} instance with a warning mechanism.
   *
   * <p>If the database opening operation takes longer than {@link #DEFAULT_DELAY} seconds, a
   * warning message is logged indicating a possible delay caused by compaction.
   *
   * @param options the {@link DBOptions} instance used to configure the database.
   * @param transactionDBOptions the {@link TransactionDBOptions} for transaction-specific
   *     configuration.
   * @param dbPath the file path to the RocksDB database directory.
   * @param columnDescriptors a list of {@link ColumnFamilyDescriptor} objects for the column
   *     families to open.
   * @param columnHandles a list of {@link ColumnFamilyHandle} objects to be populated with the
   *     opened column families.
   * @return an instance of {@link TransactionDB}.
   * @throws RocksDBException if the database cannot be opened.
   */
  public static TransactionDB openTransactionDBWithWarning(
      final DBOptions options,
      final TransactionDBOptions transactionDBOptions,
      final String dbPath,
      final List<ColumnFamilyDescriptor> columnDescriptors,
      final List<ColumnFamilyHandle> columnHandles)
      throws RocksDBException {
    return openDBWithWarning(
        () ->
            TransactionDB.open(
                options, transactionDBOptions, dbPath, columnDescriptors, columnHandles));
  }

  /**
   * A generic method to open a RocksDB database with a warning mechanism.
   *
   * <p>If the operation takes longer than {@link #DEFAULT_DELAY} seconds, a warning message is
   * logged.
   *
   * @param dbOperation a lambda or method reference representing the database opening logic.
   * @param <T> the type of the database being opened (e.g., {@link OptimisticTransactionDB} or
   *     {@link TransactionDB}).
   * @return an instance of the database being opened.
   * @throws RocksDBException if the database cannot be opened.
   */
  private static <T> T openDBWithWarning(final DBOperation<T> dbOperation) throws RocksDBException {
    AtomicBoolean operationCompleted = new AtomicBoolean(false);
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(
        () -> {
          if (!operationCompleted.get()) {
            LOG.warn(WARN_MESSAGE);
          }
        },
        DEFAULT_DELAY,
        TimeUnit.SECONDS);

    try {
      T db = dbOperation.open();
      operationCompleted.set(true);
      return db;
    } finally {
      scheduler.shutdown();
    }
  }

  /**
   * Functional interface representing a database opening operation.
   *
   * @param <T> the type of the database being opened.
   */
  @FunctionalInterface
  private interface DBOperation<T> {
    /**
     * Opens the database.
     *
     * @return the opened database instance.
     * @throws RocksDBException if an error occurs while opening the database.
     */
    T open() throws RocksDBException;
  }
}
