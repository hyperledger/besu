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

import java.nio.file.Path;
import java.util.Optional;

/** The Rocks db configuration. */
public class RocksDBConfiguration {

  private final Path databaseDir;
  private final int maxOpenFiles;
  private final String label;
  private final int backgroundThreadCount;
  private final long cacheCapacity;
  private final boolean isHighSpec;
  private final boolean isBlockchainGarbageCollectionEnabled;
  private final Optional<Double> blobGarbageCollectionAgeCutoff;
  private final Optional<Double> blobGarbageCollectionForceThreshold;

  /**
   * Instantiates a new RocksDb configuration.
   *
   * @param databaseDir the database dir
   * @param maxOpenFiles the max open files
   * @param backgroundThreadCount the background thread count
   * @param cacheCapacity the cache capacity
   * @param label the label
   * @param isHighSpec the is high spec
   * @param isBlockchainGarbageCollectionEnabled the garbage collection enabled for the BLOCKCHAIN
   *     column family
   * @param blobGarbageCollectionAgeCutoff the blob garbage collection age cutoff
   * @param blobGarbageCollectionForceThreshold the blob garbage collection force threshold
   */
  public RocksDBConfiguration(
      final Path databaseDir,
      final int maxOpenFiles,
      final int backgroundThreadCount,
      final long cacheCapacity,
      final String label,
      final boolean isHighSpec,
      final boolean isBlockchainGarbageCollectionEnabled,
      final Optional<Double> blobGarbageCollectionAgeCutoff,
      final Optional<Double> blobGarbageCollectionForceThreshold) {
    this.backgroundThreadCount = backgroundThreadCount;
    this.databaseDir = databaseDir;
    this.maxOpenFiles = maxOpenFiles;
    this.cacheCapacity = cacheCapacity;
    this.label = label;
    this.isHighSpec = isHighSpec;
    this.isBlockchainGarbageCollectionEnabled = isBlockchainGarbageCollectionEnabled;
    this.blobGarbageCollectionAgeCutoff = blobGarbageCollectionAgeCutoff;
    this.blobGarbageCollectionForceThreshold = blobGarbageCollectionForceThreshold;
  }

  /**
   * Gets database dir.
   *
   * @return the database dir
   */
  public Path getDatabaseDir() {
    return databaseDir;
  }

  /**
   * Gets max open files.
   *
   * @return the max open files
   */
  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  /**
   * Gets background thread count.
   *
   * @return the background thread count
   */
  public int getBackgroundThreadCount() {
    return backgroundThreadCount;
  }

  /**
   * Gets cache capacity.
   *
   * @return the cache capacity
   */
  public long getCacheCapacity() {
    return cacheCapacity;
  }

  /**
   * Gets label.
   *
   * @return the label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Is high spec.
   *
   * @return the boolean
   */
  public boolean isHighSpec() {
    return isHighSpec;
  }

  /**
   * Is blockchain garbage collection enabled.
   *
   * @return the boolean
   */
  public boolean isBlockchainGarbageCollectionEnabled() {
    return isBlockchainGarbageCollectionEnabled;
  }

  /**
   * Gets blob garbage collection age cutoff.
   *
   * @return the blob garbage collection age cutoff
   */
  public Optional<Double> getBlobGarbageCollectionAgeCutoff() {
    return blobGarbageCollectionAgeCutoff;
  }

  /**
   * Gets blob garbage collection force threshold.
   *
   * @return the blob garbage collection force threshold
   */
  public Optional<Double> getBlobGarbageCollectionForceThreshold() {
    return blobGarbageCollectionForceThreshold;
  }
}
