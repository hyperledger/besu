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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.services.kvstore.RocksDbKeyValueStorage;

import java.io.IOException;
import java.nio.file.Files;

public class RocksDbStorageProvider {

  public static StorageProvider create(
      final RocksDbConfiguration rocksDbConfiguration, final MetricsSystem metricsSystem)
      throws IOException {
    Files.createDirectories(rocksDbConfiguration.getDatabaseDir());
    final KeyValueStorage kv = RocksDbKeyValueStorage.create(rocksDbConfiguration, metricsSystem);
    return new KeyValueStorageProvider(kv);
  }
}
