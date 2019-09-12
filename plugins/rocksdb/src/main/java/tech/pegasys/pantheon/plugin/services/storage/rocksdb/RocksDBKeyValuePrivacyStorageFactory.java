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
package tech.pegasys.pantheon.plugin.services.storage.rocksdb;

import tech.pegasys.pantheon.plugin.services.PantheonConfiguration;
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.nio.file.Path;
import java.util.List;

import com.google.common.base.Supplier;

@Deprecated
public class RocksDBKeyValuePrivacyStorageFactory extends RocksDBKeyValueStorageFactory {

  private static final String PRIVATE_DATABASE_PATH = "private";

  public RocksDBKeyValuePrivacyStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments) {
    super(configuration, segments);
  }

  @Override
  public String getName() {
    return "rocksdb-privacy";
  }

  @Override
  protected Path storagePath(final PantheonConfiguration commonConfiguration) {
    return super.storagePath(commonConfiguration).resolve(PRIVATE_DATABASE_PATH);
  }
}
