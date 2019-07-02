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
package tech.pegasys.pantheon.cli.options;

import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class RocksDBOptions implements CLIOptions<RocksDbConfiguration.Builder> {
  private static final String MAX_OPEN_FILES_FLAG = "--Xrocksdb-max-open-files";
  private static final String CACHE_CAPACITY_FLAG = "--Xrocksdb-cache-capacity";
  private static final String MAX_BACKGROUND_COMPACTIONS_FLAG =
      "--Xrocksdb-max-background-compactions";
  private static final String BACKGROUND_THREAD_COUNT_FLAG = "--Xrocksdb-background-thread-count";

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
      defaultValue = "8388608",
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

  private RocksDBOptions() {}

  public static RocksDBOptions create() {
    return new RocksDBOptions();
  }

  public static RocksDBOptions fromConfig(final RocksDbConfiguration config) {
    final RocksDBOptions options = create();
    options.maxOpenFiles = config.getMaxOpenFiles();
    options.cacheCapacity = config.getCacheCapacity();
    options.maxBackgroundCompactions = config.getMaxBackgroundCompactions();
    options.backgroundThreadCount = config.getBackgroundThreadCount();
    return options;
  }

  @Override
  public RocksDbConfiguration.Builder toDomainObject() {
    return RocksDbConfiguration.builder()
        .maxOpenFiles(maxOpenFiles)
        .cacheCapacity(cacheCapacity)
        .maxBackgroundCompactions(maxBackgroundCompactions)
        .backgroundThreadCount(backgroundThreadCount);
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        MAX_OPEN_FILES_FLAG,
        OptionParser.format(maxOpenFiles),
        CACHE_CAPACITY_FLAG,
        OptionParser.format(cacheCapacity),
        MAX_BACKGROUND_COMPACTIONS_FLAG,
        OptionParser.format(maxBackgroundCompactions),
        BACKGROUND_THREAD_COUNT_FLAG,
        OptionParser.format(backgroundThreadCount));
  }
}
