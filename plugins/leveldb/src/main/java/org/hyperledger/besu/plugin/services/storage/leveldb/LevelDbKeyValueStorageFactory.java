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
package org.hyperledger.besu.plugin.services.storage.leveldb;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

public class LevelDbKeyValueStorageFactory implements KeyValueStorageFactory {
  private static final String NAME = "leveldb";
  private final List<SegmentIdentifier> segments;
  private SegmentedKeyValueStorage<?> storage;

  public LevelDbKeyValueStorageFactory(final List<SegmentIdentifier> segments) {
    this.segments = segments;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public KeyValueStorage create(
      final SegmentIdentifier segment,
      final BesuConfiguration configuration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    final Path storagePath = configuration.getStoragePath();
    return create(segment, storagePath);
  }

  @VisibleForTesting
  SegmentedKeyValueStorageAdapter<?> create(
      final SegmentIdentifier segment, final Path storagePath) {
    if (storage == null) {
      final File storageFile = storagePath.toFile();
      if (!storageFile.mkdirs() && !storageFile.isDirectory()) {
        throw new StorageException(
            "Unable to create storage directory: " + storageFile.getAbsolutePath());
      }
      storage = LeveldbSegmentedKeyValueStorage.create(storagePath, segments);
    }
    return new SegmentedKeyValueStorageAdapter<>(segment, storage);
  }

  @Override
  public boolean isSegmentIsolationSupported() {
    return true;
  }

  @Override
  public void close() throws IOException {
    if (storage != null) {
      storage.close();
    }
  }
}
