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
package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_DELETE_OBSOLETE_FILES_PERIOD;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_IS_HIGH_SPEC;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_KEEP_LOG_FILE_NUM;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_LOG_FILE_TIME_TO_ROLL;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_TOTAL_WAL_SIZE;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_RECYCLE_LOG_FILE_NUM;

import java.nio.file.Path;

/** The RocksDb configuration builder. */
public class RocksDBConfigurationBuilder {

  private Path databaseDir;
  private String label = "blockchain";
  private int maxOpenFiles = DEFAULT_MAX_OPEN_FILES;
  private long cacheCapacity = DEFAULT_CACHE_CAPACITY;
  private int backgroundThreadCount = DEFAULT_BACKGROUND_THREAD_COUNT;
  private boolean isHighSpec = DEFAULT_IS_HIGH_SPEC;
  private int recycleLogFileNum = DEFAULT_RECYCLE_LOG_FILE_NUM;
  private int keepLogFileNum = DEFAULT_KEEP_LOG_FILE_NUM;
  private long logFileTimeToRoll = DEFAULT_LOG_FILE_TIME_TO_ROLL;
  private long maxTotalWalSize = DEFAULT_MAX_TOTAL_WAL_SIZE;
  private long deleteObsoleteFilesPeriod = DEFAULT_DELETE_OBSOLETE_FILES_PERIOD;

  /** Instantiates a new Rocks db configuration builder. */
  public RocksDBConfigurationBuilder() {}

  /**
   * Database dir.
   *
   * @param databaseDir the database dir
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder databaseDir(final Path databaseDir) {
    this.databaseDir = databaseDir;
    return this;
  }

  /**
   * Max open files.
   *
   * @param maxOpenFiles the max open files
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder maxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
    return this;
  }

  /**
   * Label.
   *
   * @param label the label
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder label(final String label) {
    this.label = label;
    return this;
  }

  /**
   * Cache capacity.
   *
   * @param cacheCapacity the cache capacity
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder cacheCapacity(final long cacheCapacity) {
    this.cacheCapacity = cacheCapacity;
    return this;
  }

  /**
   * Background thread count.
   *
   * @param backgroundThreadCount the background thread count
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder backgroundThreadCount(final int backgroundThreadCount) {
    this.backgroundThreadCount = backgroundThreadCount;
    return this;
  }

  /**
   * Is high spec.
   *
   * @param isHighSpec the is high spec
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder isHighSpec(final boolean isHighSpec) {
    this.isHighSpec = isHighSpec;
    return this;
  }

  /**
   * Sets the number of log files to recycle.
   *
   * @param recycleLogFileNum the number of log files to recycle
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder recycleLogFileNum(final int recycleLogFileNum) {
    this.recycleLogFileNum = recycleLogFileNum;
    return this;
  }

  /**
   * Sets the number of log files to keep.
   *
   * @param keepLogFileNum the number of log files to keep
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder keepLogFileNum(final int keepLogFileNum) {
    this.keepLogFileNum = keepLogFileNum;
    return this;
  }

  /**
   * Sets the time interval to roll log files.
   *
   * @param logFileTimeToRoll the time to roll log files in seconds
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder logFileTimeToRoll(final long logFileTimeToRoll) {
    this.logFileTimeToRoll = logFileTimeToRoll;
    return this;
  }

  /**
   * Sets the maximum total size of WAL files.
   *
   * @param maxTotalWalSize the maximum total WAL size in bytes
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder maxTotalWalSize(final long maxTotalWalSize) {
    this.maxTotalWalSize = maxTotalWalSize;
    return this;
  }

  /**
   * Sets the period for deleting obsolete files.
   *
   * @param deleteObsoleteFilesPeriod the period in microseconds
   * @return the rocks db configuration builder
   */
  public RocksDBConfigurationBuilder deleteObsoleteFilesPeriod(
      final long deleteObsoleteFilesPeriod) {
    this.deleteObsoleteFilesPeriod = deleteObsoleteFilesPeriod;
    return this;
  }

  public static RocksDBConfigurationBuilder from(final RocksDBFactoryConfiguration configuration) {
    return new RocksDBConfigurationBuilder()
        .backgroundThreadCount(configuration.getBackgroundThreadCount())
        .cacheCapacity(configuration.getCacheCapacity())
        .maxOpenFiles(configuration.getMaxOpenFiles())
        .isHighSpec(configuration.isHighSpec())
        .recycleLogFileNum(configuration.getRecycleLogFileNum())
        .keepLogFileNum(configuration.getKeepLogFileNum())
        .logFileTimeToRoll(configuration.getLogFileTimeToRoll())
        .maxTotalWalSize(configuration.getMaxTotalWalSize())
        .deleteObsoleteFilesPeriod(configuration.getDeleteObsoleteFilesPeriod());
  }

  public RocksDBConfiguration build() {
    return new RocksDBConfiguration(
        databaseDir,
        maxOpenFiles,
        backgroundThreadCount,
        cacheCapacity,
        label,
        isHighSpec,
        recycleLogFileNum,
        keepLogFileNum,
        logFileTimeToRoll,
        maxTotalWalSize,
        deleteObsoleteFilesPeriod);
  }
}
