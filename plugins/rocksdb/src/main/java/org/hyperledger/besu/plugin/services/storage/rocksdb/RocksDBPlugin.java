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

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBPlugin implements BesuPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBPlugin.class);
  private static final String NAME = "rocksdb";

  private final RocksDBCLIOptions options;
  private BesuContext context;
  private RocksDBKeyValueStorageFactory factory;
  private RocksDBKeyValuePrivacyStorageFactory privacyFactory;

  public RocksDBPlugin() {
    this.options = RocksDBCLIOptions.create();
  }

  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    this.context = context;

    final Optional<PicoCLIOptions> cmdlineOptions = context.getService(PicoCLIOptions.class);

    if (cmdlineOptions.isEmpty()) {
      throw new IllegalStateException(
          "Expecting a PicoCLIO options to register CLI options with, but none found.");
    }

    cmdlineOptions.get().addPicoCLIOptions(NAME, options);
    createFactoriesAndRegisterWithStorageService();

    LOG.debug("Plugin registered.");
  }

  @Override
  public void start() {
    LOG.debug("Starting plugin.");
    if (factory == null) {
      LOG.trace("Applied configuration: {}", options.toString());
      createFactoriesAndRegisterWithStorageService();
    }
  }

  @Override
  public void stop() {
    LOG.debug("Stopping plugin.");

    try {
      if (factory != null) {
        factory.close();
        factory = null;
      }
    } catch (final IOException e) {
      LOG.error("Failed to stop plugin: {}", e.getMessage(), e);
    }

    try {
      if (privacyFactory != null) {
        privacyFactory.close();
        privacyFactory = null;
      }
    } catch (final IOException e) {
      LOG.error("Failed to stop plugin: {}", e.getMessage(), e);
    }
  }

  private void createAndRegister(final StorageService service) {
    final List<SegmentIdentifier> segments = service.getAllSegmentIdentifiers();

    final Supplier<RocksDBFactoryConfiguration> configuration =
        Suppliers.memoize(options::toDomainObject);
    factory =
        new RocksDBKeyValueStorageFactory(
            configuration, segments, RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
    privacyFactory = new RocksDBKeyValuePrivacyStorageFactory(factory);

    service.registerKeyValueStorage(factory);
    service.registerKeyValueStorage(privacyFactory);
  }

  private void createFactoriesAndRegisterWithStorageService() {
    context
        .getService(StorageService.class)
        .ifPresentOrElse(
            this::createAndRegister,
            () -> LOG.error("Failed to register KeyValueFactory due to missing StorageService."));
  }
}
