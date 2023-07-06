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

/** The RocksDb factory configuration. */
public class RocksDBFactoryConfiguration {

  private final int maxOpenFiles;
  private final int backgroundThreadCount;
  private final long cacheCapacity;
  private final boolean isHighSpec;

  /**
   * Instantiates a new RocksDb factory configuration.
   *
   * @param maxOpenFiles the max open files
   * @param backgroundThreadCount the background thread count
   * @param cacheCapacity the cache capacity
   * @param isHighSpec the is high spec
   */
  public RocksDBFactoryConfiguration(
      final int maxOpenFiles,
      final int backgroundThreadCount,
      final long cacheCapacity,
      final boolean isHighSpec) {
    this.backgroundThreadCount = backgroundThreadCount;
    this.maxOpenFiles = maxOpenFiles;
    this.cacheCapacity = cacheCapacity;
    this.isHighSpec = isHighSpec;
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
   * Is high spec.
   *
   * @return the boolean
   */
  public boolean isHighSpec() {
    return isHighSpec;
  }
}
