/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-column configuration for RocksDB.
 *
 * <p>Allows fine-grained configuration of individual columns when using separate database per
 * column mode.
 */
public class PerColumnConfiguration {

  private final Map<String, ColumnConfig> columnConfigs = new HashMap<>();
  private final ColumnConfig defaultConfig;

  /**
   * Creates a per-column configuration with a default configuration.
   *
   * @param defaultConfig the default configuration to use for columns without specific config
   */
  public PerColumnConfiguration(final ColumnConfig defaultConfig) {
    this.defaultConfig = defaultConfig;
  }

  /**
   * Sets configuration for a specific column.
   *
   * @param segmentName the segment name
   * @param config the configuration for this column
   * @return this builder
   */
  public PerColumnConfiguration setColumnConfig(
      final String segmentName, final ColumnConfig config) {
    columnConfigs.put(segmentName, config);
    return this;
  }

  /**
   * Gets configuration for a specific segment.
   *
   * @param segment the segment identifier
   * @return the configuration for this segment
   */
  public ColumnConfig getConfigForSegment(final SegmentIdentifier segment) {
    return columnConfigs.getOrDefault(segment.getName(), defaultConfig);
  }

  /** Configuration for a single column. */
  public static class ColumnConfig {
    private final long cacheCapacity;
    private final int maxOpenFiles;
    private final int backgroundThreadCount;
    private final boolean enableCompression;
    private final Optional<Integer> targetFileSizeBase;
    private final Optional<Integer> maxBytesForLevelBase;
    private final Optional<Integer> writeBufferSize;
    private final Optional<Integer> maxWriteBufferNumber;

    // Cache configuration
    private final Optional<Long> rowCacheSize;
    private final boolean useCompressedCache;
    private final Optional<Long> compressedCacheSize;

    private ColumnConfig(
        final long cacheCapacity,
        final int maxOpenFiles,
        final int backgroundThreadCount,
        final boolean enableCompression,
        final Optional<Integer> targetFileSizeBase,
        final Optional<Integer> maxBytesForLevelBase,
        final Optional<Integer> writeBufferSize,
        final Optional<Integer> maxWriteBufferNumber,
        final Optional<Long> rowCacheSize,
        final boolean useCompressedCache,
        final Optional<Long> compressedCacheSize) {
      this.cacheCapacity = cacheCapacity;
      this.maxOpenFiles = maxOpenFiles;
      this.backgroundThreadCount = backgroundThreadCount;
      this.enableCompression = enableCompression;
      this.targetFileSizeBase = targetFileSizeBase;
      this.maxBytesForLevelBase = maxBytesForLevelBase;
      this.writeBufferSize = writeBufferSize;
      this.maxWriteBufferNumber = maxWriteBufferNumber;
      this.rowCacheSize = rowCacheSize;
      this.useCompressedCache = useCompressedCache;
      this.compressedCacheSize = compressedCacheSize;
    }

    public long getCacheCapacity() {
      return cacheCapacity;
    }

    public int getMaxOpenFiles() {
      return maxOpenFiles;
    }

    public int getBackgroundThreadCount() {
      return backgroundThreadCount;
    }

    public boolean isCompressionEnabled() {
      return enableCompression;
    }

    public Optional<Integer> getTargetFileSizeBase() {
      return targetFileSizeBase;
    }

    public Optional<Integer> getMaxBytesForLevelBase() {
      return maxBytesForLevelBase;
    }

    public Optional<Integer> getWriteBufferSize() {
      return writeBufferSize;
    }

    public Optional<Integer> getMaxWriteBufferNumber() {
      return maxWriteBufferNumber;
    }

    public Optional<Long> getRowCacheSize() {
      return rowCacheSize;
    }

    public boolean useCompressedCache() {
      return useCompressedCache;
    }

    public Optional<Long> getCompressedCacheSize() {
      return compressedCacheSize;
    }

    public Optional<Integer> getLevelCompactionDynamicLevelBytes() {
      return Optional.empty();
    }

    /** Builder for ColumnConfig. */
    public static class Builder {
      private long cacheCapacity = 1_073_741_824L; // 1 GB default
      private int maxOpenFiles = 1024;
      private int backgroundThreadCount = 4;
      private boolean enableCompression = true;
      private Optional<Integer> targetFileSizeBase = Optional.empty();
      private Optional<Integer> maxBytesForLevelBase = Optional.empty();
      private Optional<Integer> writeBufferSize = Optional.empty();
      private Optional<Integer> maxWriteBufferNumber = Optional.empty();

      // Cache configuration
      private Optional<Long> rowCacheSize = Optional.empty();
      private boolean useCompressedCache = false;
      private Optional<Long> compressedCacheSize = Optional.empty();

      public Builder cacheCapacity(final long cacheCapacity) {
        this.cacheCapacity = cacheCapacity;
        return this;
      }

      public Builder maxOpenFiles(final int maxOpenFiles) {
        this.maxOpenFiles = maxOpenFiles;
        return this;
      }

      public Builder backgroundThreadCount(final int backgroundThreadCount) {
        this.backgroundThreadCount = backgroundThreadCount;
        return this;
      }

      public Builder enableCompression(final boolean enableCompression) {
        this.enableCompression = enableCompression;
        return this;
      }

      public Builder targetFileSizeBase(final int targetFileSizeBase) {
        this.targetFileSizeBase = Optional.of(targetFileSizeBase);
        return this;
      }

      public Builder maxBytesForLevelBase(final int maxBytesForLevelBase) {
        this.maxBytesForLevelBase = Optional.of(maxBytesForLevelBase);
        return this;
      }

      public Builder writeBufferSize(final int writeBufferSize) {
        this.writeBufferSize = Optional.of(writeBufferSize);
        return this;
      }

      public Builder maxWriteBufferNumber(final int maxWriteBufferNumber) {
        this.maxWriteBufferNumber = Optional.of(maxWriteBufferNumber);
        return this;
      }

      public Builder rowCacheSize(final long rowCacheSize) {
        this.rowCacheSize = Optional.of(rowCacheSize);
        return this;
      }

      public Builder useCompressedCache(final boolean useCompressedCache) {
        this.useCompressedCache = useCompressedCache;
        return this;
      }

      public Builder compressedCacheSize(final long compressedCacheSize) {
        this.compressedCacheSize = Optional.of(compressedCacheSize);
        return this;
      }

      public ColumnConfig build() {
        return new ColumnConfig(
            cacheCapacity,
            maxOpenFiles,
            backgroundThreadCount,
            enableCompression,
            targetFileSizeBase,
            maxBytesForLevelBase,
            writeBufferSize,
            maxWriteBufferNumber,
            rowCacheSize,
            useCompressedCache,
            compressedCacheSize);
      }
    }
  }

  /** Creates optimized configurations based on column characteristics. */
  public static class OptimizedConfigs {

    /**
     * Configuration for write-heavy columns (e.g., BLOCKCHAIN, TRIE_LOG_STORAGE).
     *
     * <p>Optimized for high write throughput with: - Larger write buffers - More background threads
     * - Aggressive compaction
     */
    public static ColumnConfig forWriteHeavyColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(512 * 1024 * 1024L) // 512 MB cache (réduit de 2 GB)
          .maxOpenFiles(1024)
          .backgroundThreadCount(6)
          .enableCompression(true)
          .writeBufferSize(128 * 1024 * 1024) // 128 MB write buffer (réduit de 256 MB)
          .maxWriteBufferNumber(4) // 4 write buffers
          .targetFileSizeBase(64 * 1024 * 1024) // 64 MB target file size
          .maxBytesForLevelBase(256 * 1024 * 1024) // 256 MB level base
          .build();
    }

    /**
     * Configuration for read-heavy columns (e.g., WORLD_STATE, CODE_STORAGE).
     *
     * <p>Optimized for low latency reads with: - Larger block cache - Row cache for frequently
     * accessed rows - More open files for better caching - Smaller write buffers (less memory)
     */
    public static ColumnConfig forReadHeavyColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(1_073_741_824L) // 1 GB block cache (réduit de 4 GB)
          .rowCacheSize(256 * 1024 * 1024L) // 256 MB row cache (réduit de 1 GB)
          .maxOpenFiles(2048) // More files for better read cache
          .backgroundThreadCount(4)
          .enableCompression(true)
          .writeBufferSize(64 * 1024 * 1024) // 64 MB write buffer (smaller)
          .maxWriteBufferNumber(2) // 2 write buffers
          .targetFileSizeBase(64 * 1024 * 1024) // 64 MB target file size
          .build();
    }

    /**
     * Configuration for ultra-fast read columns (e.g., ACCOUNT_INFO_STATE, TRIE_BRANCH_STORAGE).
     *
     * <p>Optimized for maximum read performance (both cold and hot reads): - Moderate block cache
     * (1 GB) - Small row cache for hot keys (256 MB) - Cache index/filter blocks in memory
     * (critical for cold reads) - Bloom filters (skip unnecessary disk reads) - More open files for
     * OS page cache
     */
    public static ColumnConfig forUltraFastReadColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(1_073_741_824L) // 1 GB block cache (réduit de 2 GB)
          .rowCacheSize(256 * 1024 * 1024L) // 256 MB row cache (hot keys seulement)
          .maxOpenFiles(4096) // Plus de fichiers ouverts = OS page cache
          .backgroundThreadCount(6) // Plus de threads pour compaction
          .enableCompression(true)
          .writeBufferSize(32 * 1024 * 1024) // 32 MB write buffer (minimal)
          .maxWriteBufferNumber(2)
          .targetFileSizeBase(64 * 1024 * 1024) // 64 MB target file size
          .build();
    }

    /**
     * Configuration for balanced columns (e.g., ACCOUNT_INFO_STATE).
     *
     * <p>Balanced between reads and writes.
     */
    public static ColumnConfig forBalancedColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(256 * 1024 * 1024L) // 256 MB cache (réduit de 1 GB)
          .maxOpenFiles(512)
          .backgroundThreadCount(4)
          .enableCompression(true)
          .writeBufferSize(64 * 1024 * 1024) // 64 MB write buffer
          .maxWriteBufferNumber(2) // 2 write buffers
          .targetFileSizeBase(64 * 1024 * 1024) // 64 MB target file size
          .build();
    }

    /**
     * Configuration for small/infrequent columns (e.g., VARIABLES, CHAIN_PRUNER_STATE).
     *
     * <p>Minimal resources allocated.
     */
    public static ColumnConfig forSmallColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(64 * 1024 * 1024L) // 64 MB cache (réduit de 128 MB)
          .maxOpenFiles(128)
          .backgroundThreadCount(2)
          .enableCompression(true)
          .writeBufferSize(16 * 1024 * 1024) // 16 MB write buffer
          .maxWriteBufferNumber(2)
          .build();
    }

    /**
     * Configuration for archive/static columns (e.g., historical data).
     *
     * <p>Optimized for storage efficiency with aggressive compression.
     */
    public static ColumnConfig forArchiveColumn() {
      return new ColumnConfig.Builder()
          .cacheCapacity(128 * 1024 * 1024L) // 128 MB cache (réduit de 512 MB)
          .maxOpenFiles(256)
          .backgroundThreadCount(2)
          .enableCompression(true) // Aggressive compression
          .writeBufferSize(32 * 1024 * 1024) // 32 MB write buffer
          .maxWriteBufferNumber(2)
          .targetFileSizeBase(128 * 1024 * 1024) // Larger files for better compression
          .build();
    }

    /**
     * Creates recommended configuration for all Besu columns.
     *
     * @return optimized per-column configuration
     */
    public static PerColumnConfiguration createRecommendedConfig() {
      PerColumnConfiguration config = new PerColumnConfiguration(forBalancedColumn());

      // Write-heavy columns (sync, new blocks, transactions)
      config.setColumnConfig("BLOCKCHAIN", forWriteHeavyColumn());
      config.setColumnConfig("TRIE_LOG_STORAGE", forWriteHeavyColumn());
      config.setColumnConfig("BACKWARD_SYNC_BLOCKS", forWriteHeavyColumn());

      // Read-heavy columns (state queries, RPC calls)
      config.setColumnConfig("WORLD_STATE", forReadHeavyColumn());
      config.setColumnConfig("CODE_STORAGE", forReadHeavyColumn());

      // ULTRA-FAST READ columns (hot path critiques pour performance)
      config.setColumnConfig("ACCOUNT_INFO_STATE", forUltraFastReadColumn());
      config.setColumnConfig("ACCOUNT_STORAGE_STORAGE", forUltraFastReadColumn());
      config.setColumnConfig("TRIE_BRANCH_STORAGE", forUltraFastReadColumn());

      // Small/metadata columns
      config.setColumnConfig("VARIABLES", forSmallColumn());
      config.setColumnConfig("CHAIN_PRUNER_STATE", forSmallColumn());
      config.setColumnConfig("DEFAULT", forSmallColumn());

      // Backward sync (temporary, can be smaller)
      config.setColumnConfig("BACKWARD_SYNC_HEADERS", forBalancedColumn());
      config.setColumnConfig("BACKWARD_SYNC_CHAIN", forBalancedColumn());

      // Snap sync (temporary during sync only)
      config.setColumnConfig("SNAPSYNC_MISSING_ACCOUNT_RANGE", forSmallColumn());
      config.setColumnConfig("SNAPSYNC_ACCOUNT_TO_FIX", forSmallColumn());

      return config;
    }
  }
}
