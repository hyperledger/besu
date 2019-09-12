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
package tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration;

import static tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_BACKGROUND_COMPACTIONS;
import static tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import java.nio.file.Path;

public class RocksDBConfigurationBuilder {

  private Path databaseDir;
  private String label = "blockchain";
  private int maxOpenFiles = DEFAULT_MAX_OPEN_FILES;
  private long cacheCapacity = DEFAULT_CACHE_CAPACITY;
  private int maxBackgroundCompactions = DEFAULT_MAX_BACKGROUND_COMPACTIONS;
  private int backgroundThreadCount = DEFAULT_BACKGROUND_THREAD_COUNT;

  public RocksDBConfigurationBuilder databaseDir(final Path databaseDir) {
    this.databaseDir = databaseDir;
    return this;
  }

  public RocksDBConfigurationBuilder maxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
    return this;
  }

  public RocksDBConfigurationBuilder label(final String label) {
    this.label = label;
    return this;
  }

  public RocksDBConfigurationBuilder cacheCapacity(final long cacheCapacity) {
    this.cacheCapacity = cacheCapacity;
    return this;
  }

  public RocksDBConfigurationBuilder maxBackgroundCompactions(final int maxBackgroundCompactions) {
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    return this;
  }

  public RocksDBConfigurationBuilder backgroundThreadCount(final int backgroundThreadCount) {
    this.backgroundThreadCount = backgroundThreadCount;
    return this;
  }

  public static RocksDBConfigurationBuilder from(final RocksDBFactoryConfiguration configuration) {
    return new RocksDBConfigurationBuilder()
        .backgroundThreadCount(configuration.getBackgroundThreadCount())
        .cacheCapacity(configuration.getCacheCapacity())
        .maxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
        .maxOpenFiles(configuration.getMaxOpenFiles());
  }

  public RocksDBConfiguration build() {
    return new RocksDBConfiguration(
        databaseDir,
        maxOpenFiles,
        maxBackgroundCompactions,
        backgroundThreadCount,
        cacheCapacity,
        label);
  }
}
