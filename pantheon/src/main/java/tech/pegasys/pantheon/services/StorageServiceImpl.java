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
package tech.pegasys.pantheon.services;

import tech.pegasys.pantheon.plugin.services.StorageService;
import tech.pegasys.pantheon.plugin.services.exception.StorageException;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageFactory;
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StorageServiceImpl implements StorageService {

  private final List<SegmentIdentifier> segments;
  private final Map<String, KeyValueStorageFactory> factories;

  public StorageServiceImpl() {
    this.segments = List.of(Segment.values());
    this.factories = new ConcurrentHashMap<>();
  }

  @Override
  public void registerKeyValueStorage(final KeyValueStorageFactory factory) {
    factories.put(factory.getName(), factory);
  }

  @Override
  public List<SegmentIdentifier> getAllSegmentIdentifiers() {
    return segments;
  }

  private enum Segment implements SegmentIdentifier {
    BLOCKCHAIN,
    WORLD_STATE,
    PRIVATE_TRANSACTIONS,
    PRIVATE_STATE,
    PRUNING_STATE;

    @Override
    public String getName() {
      return name();
    }
  }

  public KeyValueStorageFactory getByName(final String name) {
    return Optional.ofNullable(factories.get(name))
        .orElseThrow(
            () -> new StorageException("No KeyValueStorageFactory found for key: " + name));
  }
}
