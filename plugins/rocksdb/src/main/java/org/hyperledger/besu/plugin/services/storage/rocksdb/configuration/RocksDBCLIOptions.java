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

public class RocksDBCLIOptions {

  public static final int DEFAULT_MAX_OPEN_FILES = 1024;
  public static final long DEFAULT_CACHE_CAPACITY = 134217728;
  public static final int DEFAULT_MAX_BACKGROUND_COMPACTIONS = 4;
  public static final int DEFAULT_BACKGROUND_THREAD_COUNT = 4;

  private static final String MAX_OPEN_FILES_FLAG = "--Xplugin-rocksdb-max-open-files";
  private static final String CACHE_CAPACITY_FLAG = "--Xplugin-rocksdb-cache-capacity";
  private static final String MAX_BACKGROUND_COMPACTIONS_FLAG =
      "--Xplugin-rocksdb-max-background-compactions";
  private static final String BACKGROUND_THREAD_COUNT_FLAG =
      "--Xplugin-rocksdb-background-thread-count";

  @CommandLine.Option(
      names = {MAX_OPEN_FILES_FLAG},
      hidden = true,
      defaultValue = "1024",
      paramLabel = "<INTEGER>",
      description = "Max number of files RocksDB will open (default: ${DEFAULT-VALUE})")
  int maxOpenFiles;

  @CommandLine.Option(
      names = {CACHE_CAPACITY_FLAG},
      hidden = true,
      defaultValue = "134217728",
      paramLabel = "<LONG>",
      description = "Cache capacity of RocksDB (default: ${DEFAULT-VALUE})")
  long cacheCapacity;

  @CommandLine.Option(
      names = {MAX_BACKGROUND_COMPACTIONS_FLAG},
      hidden = true,
      defaultValue = "4",
      paramLabel = "<INTEGER>",
      description = "Maximum number of RocksDB background compactions (default: ${DEFAULT-VALUE})")
  int maxBackgroundCompactions;

  @CommandLine.Option(
      names = {BACKGROUND_THREAD_COUNT_FLAG},
      hidden = true,
      defaultValue = "4",
      paramLabel = "<INTEGER>",
      description = "Number of RocksDB background threads (default: ${DEFAULT-VALUE})")
  int backgroundThreadCount;

  private RocksDBCLIOptions() {}

  public static RocksDBCLIOptions create() {
    return new RocksDBCLIOptions();
  }

  public static RocksDBCLIOptions fromConfig(final RocksDBConfiguration config) {
    final RocksDBCLIOptions options = create();
    options.maxOpenFiles = config.getMaxOpenFiles();
    options.cacheCapacity = config.getCacheCapacity();
    options.maxBackgroundCompactions = config.getMaxBackgroundCompactions();
    options.backgroundThreadCount = config.getBackgroundThreadCount();
    return options;
  }

  public RocksDBFactoryConfiguration toDomainObject() {
    return new RocksDBFactoryConfiguration(
        maxOpenFiles, maxBackgroundCompactions, backgroundThreadCount, cacheCapacity);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("maxOpenFiles", maxOpenFiles)
        .add("cacheCapacity", cacheCapacity)
        .add("maxBackgroundCompactions", maxBackgroundCompactions)
        .add("backgroundThreadCount", backgroundThreadCount)
        .toString();
  }
}
