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

import com.google.common.base.MoreObjects;
import picocli.CommandLine;

/** The RocksDb cli options. */
public class RocksDBCLIOptions {

  /** The constant DEFAULT_MAX_OPEN_FILES. */
  public static final int DEFAULT_MAX_OPEN_FILES = 1024;

  /** The constant DEFAULT_CACHE_CAPACITY. */
  public static final long DEFAULT_CACHE_CAPACITY = 134217728;

  /** The constant DEFAULT_BACKGROUND_THREAD_COUNT. */
  public static final int DEFAULT_BACKGROUND_THREAD_COUNT = 4;

  /** The constant DEFAULT_IS_HIGH_SPEC. */
  public static final boolean DEFAULT_IS_HIGH_SPEC = false;

  /** The constant MAX_OPEN_FILES_FLAG. */
  public static final String MAX_OPEN_FILES_FLAG = "--Xplugin-rocksdb-max-open-files";

  /** The constant CACHE_CAPACITY_FLAG. */
  public static final String CACHE_CAPACITY_FLAG = "--Xplugin-rocksdb-cache-capacity";

  /** The constant BACKGROUND_THREAD_COUNT_FLAG. */
  public static final String BACKGROUND_THREAD_COUNT_FLAG =
      "--Xplugin-rocksdb-background-thread-count";

  /** The constant IS_HIGH_SPEC. */
  public static final String IS_HIGH_SPEC = "--Xplugin-rocksdb-high-spec-enabled";

  /** The Max open files. */
  @CommandLine.Option(
      names = {MAX_OPEN_FILES_FLAG},
      hidden = true,
      defaultValue = "1024",
      paramLabel = "<INTEGER>",
      description = "Max number of files RocksDB will open (default: ${DEFAULT-VALUE})")
  int maxOpenFiles;

  /** The Cache capacity. */
  @CommandLine.Option(
      names = {CACHE_CAPACITY_FLAG},
      hidden = true,
      defaultValue = "134217728",
      paramLabel = "<LONG>",
      description = "Cache capacity of RocksDB (default: ${DEFAULT-VALUE})")
  long cacheCapacity;

  /** The Background thread count. */
  @CommandLine.Option(
      names = {BACKGROUND_THREAD_COUNT_FLAG},
      hidden = true,
      defaultValue = "4",
      paramLabel = "<INTEGER>",
      description = "Number of RocksDB background threads (default: ${DEFAULT-VALUE})")
  int backgroundThreadCount;

  /** The Is high spec. */
  @CommandLine.Option(
      names = {IS_HIGH_SPEC},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description =
          "Use this flag to boost Besu performance if you have a 16 GiB RAM hardware or more (default: ${DEFAULT-VALUE})")
  boolean isHighSpec;

  private RocksDBCLIOptions() {}

  /**
   * Create RocksDb cli options.
   *
   * @return the RocksDb cli options
   */
  public static RocksDBCLIOptions create() {
    return new RocksDBCLIOptions();
  }

  /**
   * RocksDb cli options from config.
   *
   * @param config the config
   * @return the RocksDb cli options
   */
  public static RocksDBCLIOptions fromConfig(final RocksDBConfiguration config) {
    final RocksDBCLIOptions options = create();
    options.maxOpenFiles = config.getMaxOpenFiles();
    options.cacheCapacity = config.getCacheCapacity();
    options.backgroundThreadCount = config.getBackgroundThreadCount();
    options.isHighSpec = config.isHighSpec();
    return options;
  }

  /**
   * To domain object rocks db factory configuration.
   *
   * @return the rocks db factory configuration
   */
  public RocksDBFactoryConfiguration toDomainObject() {
    return new RocksDBFactoryConfiguration(
        maxOpenFiles, backgroundThreadCount, cacheCapacity, isHighSpec);
  }

  /**
   * Is high spec.
   *
   * @return the boolean
   */
  public boolean isHighSpec() {
    return isHighSpec;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("maxOpenFiles", maxOpenFiles)
        .add("cacheCapacity", cacheCapacity)
        .add("backgroundThreadCount", backgroundThreadCount)
        .add("isHighSpec", isHighSpec)
        .toString();
  }
}
