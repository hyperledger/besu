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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_BACKGROUND_COMPACTIONS;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import org.junit.Test;
import picocli.CommandLine;

public class RocksDBCLIOptionsTest {

  private static final String MAX_OPEN_FILES_FLAG = "--Xplugin-rocksdb-max-open-files";
  private static final String CACHE_CAPACITY_FLAG = "--Xplugin-rocksdb-cache-capacity";
  private static final String MAX_BACKGROUND_COMPACTIONS_FLAG =
      "--Xplugin-rocksdb-max-background-compactions";
  private static final String BACKGROUND_THREAD_COUNT_FLAG =
      "--Xplugin-rocksdb-background-thread-count";

  @Test
  public void defaultValues() {
    final RocksDBCLIOptions options = RocksDBCLIOptions.create();

    new CommandLine(options).parse();

    final RocksDBFactoryConfiguration configuration = options.toDomainObject();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getBackgroundThreadCount()).isEqualTo(DEFAULT_BACKGROUND_THREAD_COUNT);
    assertThat(configuration.getCacheCapacity()).isEqualTo(DEFAULT_CACHE_CAPACITY);
    assertThat(configuration.getMaxBackgroundCompactions())
        .isEqualTo(DEFAULT_MAX_BACKGROUND_COMPACTIONS);
    assertThat(configuration.getMaxOpenFiles()).isEqualTo(DEFAULT_MAX_OPEN_FILES);
  }

  @Test
  public void customBackgroundThreadCount() {
    final RocksDBCLIOptions options = RocksDBCLIOptions.create();
    final int expectedBackgroundThreadCount = 99;

    new CommandLine(options)
        .parse(BACKGROUND_THREAD_COUNT_FLAG, "" + expectedBackgroundThreadCount);

    final RocksDBFactoryConfiguration configuration = options.toDomainObject();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getBackgroundThreadCount()).isEqualTo(expectedBackgroundThreadCount);
    assertThat(configuration.getCacheCapacity()).isEqualTo(DEFAULT_CACHE_CAPACITY);
    assertThat(configuration.getMaxBackgroundCompactions())
        .isEqualTo(DEFAULT_MAX_BACKGROUND_COMPACTIONS);
    assertThat(configuration.getMaxOpenFiles()).isEqualTo(DEFAULT_MAX_OPEN_FILES);
  }

  @Test
  public void customCacheCapacity() {
    final RocksDBCLIOptions options = RocksDBCLIOptions.create();
    final long expectedCacheCapacity = 400050006000L;

    new CommandLine(options).parse(CACHE_CAPACITY_FLAG, "" + expectedCacheCapacity);

    final RocksDBFactoryConfiguration configuration = options.toDomainObject();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getBackgroundThreadCount()).isEqualTo(DEFAULT_BACKGROUND_THREAD_COUNT);
    assertThat(configuration.getCacheCapacity()).isEqualTo(expectedCacheCapacity);
    assertThat(configuration.getMaxBackgroundCompactions())
        .isEqualTo(DEFAULT_MAX_BACKGROUND_COMPACTIONS);
    assertThat(configuration.getMaxOpenFiles()).isEqualTo(DEFAULT_MAX_OPEN_FILES);
  }

  @Test
  public void customMaxBackgroundCompactions() {
    final RocksDBCLIOptions options = RocksDBCLIOptions.create();
    final int expectedMaxBackgroundCompactions = 223344;

    new CommandLine(options)
        .parse(MAX_BACKGROUND_COMPACTIONS_FLAG, "" + expectedMaxBackgroundCompactions);

    final RocksDBFactoryConfiguration configuration = options.toDomainObject();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getBackgroundThreadCount()).isEqualTo(DEFAULT_BACKGROUND_THREAD_COUNT);
    assertThat(configuration.getCacheCapacity()).isEqualTo(DEFAULT_CACHE_CAPACITY);
    assertThat(configuration.getMaxBackgroundCompactions())
        .isEqualTo(expectedMaxBackgroundCompactions);
    assertThat(configuration.getMaxOpenFiles()).isEqualTo(DEFAULT_MAX_OPEN_FILES);
  }

  @Test
  public void customMaxOpenFiles() {
    final RocksDBCLIOptions options = RocksDBCLIOptions.create();
    final int expectedMaxOpenFiles = 65;

    new CommandLine(options).parse(MAX_OPEN_FILES_FLAG, "" + expectedMaxOpenFiles);

    final RocksDBFactoryConfiguration configuration = options.toDomainObject();
    assertThat(configuration).isNotNull();
    assertThat(configuration.getBackgroundThreadCount()).isEqualTo(DEFAULT_BACKGROUND_THREAD_COUNT);
    assertThat(configuration.getCacheCapacity()).isEqualTo(DEFAULT_CACHE_CAPACITY);
    assertThat(configuration.getMaxBackgroundCompactions())
        .isEqualTo(DEFAULT_MAX_BACKGROUND_COMPACTIONS);
    assertThat(configuration.getMaxOpenFiles()).isEqualTo(expectedMaxOpenFiles);
  }
}
