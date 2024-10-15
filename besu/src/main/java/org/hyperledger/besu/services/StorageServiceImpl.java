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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

/** The Storage service implementation. */
public class StorageServiceImpl implements StorageService {

  private final List<SegmentIdentifier> segments;
  private final Map<String, KeyValueStorageFactory> factories;

  /** Instantiates a new Storage service. */
  @Inject
  public StorageServiceImpl() {
    this.segments = List.of(KeyValueSegmentIdentifier.values());
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

  @Override
  public Optional<KeyValueStorageFactory> getByName(final String name) {
    return Optional.ofNullable(factories.get(name));
  }
}
