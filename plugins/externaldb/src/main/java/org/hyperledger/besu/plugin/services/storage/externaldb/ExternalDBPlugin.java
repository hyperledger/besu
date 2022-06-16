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
package org.hyperledger.besu.plugin.services.storage.externaldb;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.externaldb.client.ExternalDBClientKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.externaldb.configuration.ExternalDBCLIOptions;
import org.hyperledger.besu.plugin.services.storage.externaldb.configuration.ExternalDbConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalDBPlugin implements BesuPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalDBPlugin.class);
  private static final String NAME = "externaldb";

  private final ExternalDBCLIOptions options;
  private BesuContext context;

  public ExternalDBPlugin() {
    this.options = ExternalDBCLIOptions.create();
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
  }

  @Override
  public void stop() {
    LOG.debug("Stopping plugin.");
  }

  private void createAndRegister(final StorageService service) {
    // TODO are the segments needed?
    final List<SegmentIdentifier> segments = service.getAllSegmentIdentifiers();
    final Supplier<ExternalDbConfiguration> configuration =
        Suppliers.memoize(options::toDomainObject);

    // For server, expose additional API? Is this part of the external db plugin or the rocks db
    // plugin?
    // The rocksdb plugin is needed for storage.
    // Expose get as an endpoint accessing the rocksdb storage?
    // Do we want this specific to rocks db though?

    final ExternalDBClientKeyValueStorageFactory factory =
        new ExternalDBClientKeyValueStorageFactory(configuration, segments);
    service.registerKeyValueStorage(factory);
  }

  private void createFactoriesAndRegisterWithStorageService() {
    context
        .getService(StorageService.class)
        .ifPresentOrElse(
            this::createAndRegister,
            () -> LOG.error("Failed to register KeyValueFactory due to missing StorageService."));
  }
}
