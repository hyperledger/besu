/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.services.kvstore;

import tech.pegasys.pantheon.services.util.RocksDbUtil;

import java.nio.file.Path;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.LRUCache;
import picocli.CommandLine;

public class RocksDbConfiguration {

  private final Path databaseDir;
  private final int maxOpenFiles;
  private final BlockBasedTableConfig blockBasedTableConfig;
  private final String label;

  public RocksDbConfiguration(
      final Path databaseDir, final int maxOpenFiles, final LRUCache cache, final String label) {
    RocksDbUtil.loadNativeLibrary();
    this.databaseDir = databaseDir;
    this.maxOpenFiles = maxOpenFiles;
    this.blockBasedTableConfig = new BlockBasedTableConfig().setBlockCache(cache);
    this.label = label;
  }

  public Path getDatabaseDir() {
    return databaseDir;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public BlockBasedTableConfig getBlockBasedTableConfig() {
    return blockBasedTableConfig;
  }

  public String getLabel() {
    return label;
  }

  public static class Builder {

    Path databaseDir;
    LRUCache cache = null;
    String label = "blockchain";

    @CommandLine.Option(
        names = {"--Xrocksdb-max-open-files"},
        hidden = true,
        defaultValue = "1024",
        paramLabel = "<INTEGER>",
        description = "Max number of files RocksDB will open (default: ${DEFAULT-VALUE})")
    int maxOpenFiles;

    @CommandLine.Option(
        names = {"--Xrocksdb-cache-capacity"},
        hidden = true,
        defaultValue = "8388608",
        paramLabel = "<LONG>",
        description = "Cache capacity of RocksDB (default: ${DEFAULT-VALUE})")
    long cacheCapacity;

    public Builder databaseDir(final Path databaseDir) {
      this.databaseDir = databaseDir;
      return this;
    }

    public Builder maxOpenFiles(final int maxOpenFiles) {
      this.maxOpenFiles = maxOpenFiles;
      return this;
    }

    public Builder label(final String label) {
      this.label = label;
      return this;
    }

    public Builder cacheCapacity(final long cacheCapacity) {
      this.cacheCapacity = cacheCapacity;
      return this;
    }

    private LRUCache createCache(final long cacheCapacity) {
      RocksDbUtil.loadNativeLibrary();
      return new LRUCache(cacheCapacity);
    }

    public RocksDbConfiguration build() {
      if (cache == null) {
        cache = createCache(cacheCapacity);
      }
      return new RocksDbConfiguration(databaseDir, maxOpenFiles, cache, label);
    }
  }
}
