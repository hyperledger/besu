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
package org.hyperledger.besu.services.kvstore;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryStoragePlugin implements BesuPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryStoragePlugin.class);
  private BesuContext context;
  private MemoryKeyValueStorageFactory factory;
  private MemoryKeyValueStorageFactory privacyFactory;

  @Override
  public void register(final BesuContext context) {
    LOG.debug("Registering plugin");
    this.context = context;

    createFactoriesAndRegisterWithStorageService();

    LOG.debug("Plugin registered.");
  }

  @Override
  public void start() {
    LOG.debug("Starting plugin.");
    if (factory == null) {
      createFactoriesAndRegisterWithStorageService();
    }
  }

  @Override
  public void stop() {
    LOG.debug("Stopping plugin.");

    if (factory != null) {
      factory.close();
      factory = null;
    }

    if (privacyFactory != null) {
      privacyFactory.close();
      privacyFactory = null;
    }
  }

  private void createAndRegister(final StorageService service) {

    factory = new MemoryKeyValueStorageFactory("memory");
    privacyFactory = new MemoryKeyValueStorageFactory("memory-privacy");

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

  public static class MemoryKeyValueStorageFactory implements KeyValueStorageFactory {

    private final String name;
    private final Map<SegmentIdentifier, InMemoryKeyValueStorage> storageMap = new HashMap<>();

    public MemoryKeyValueStorageFactory(final String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public KeyValueStorage create(
        final SegmentIdentifier segment,
        final BesuConfiguration configuration,
        final MetricsSystem metricsSystem)
        throws StorageException {
      return storageMap.computeIfAbsent(segment, __ -> new InMemoryKeyValueStorage());
    }

    @Override
    public boolean isSegmentIsolationSupported() {
      return true;
    }

    @Override
    public void close() {
      storageMap.clear();
    }
  }
}
