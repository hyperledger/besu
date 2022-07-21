/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.plugin.services.storage.externaldb.client;

import org.hyperledger.besu.ethereum.api.grpc.ExternalDbGrpc;
import org.hyperledger.besu.ethereum.api.grpc.ExternalDbGrpc.ExternalDbBlockingStub;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.externaldb.configuration.ExternalDbConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ExternalDBClientKeyValueStorageFactory implements KeyValueStorageFactory {

  private static final int DEFAULT_VERSION = 1;
  private static final String NAME = "externaldb";

  private final int defaultVersion;

  private final Supplier<ExternalDbConfiguration> configuration;
  private final AtomicReference<ExternalDbBlockingStub> externalDbBlockingStub =
      new AtomicReference<>();

  ExternalDBClientKeyValueStorageFactory(
      final Supplier<ExternalDbConfiguration> configuration,
      final List<SegmentIdentifier> segments,
      final int defaultVersion) {
    this.configuration = configuration;
    this.defaultVersion = defaultVersion;
  }

  public ExternalDBClientKeyValueStorageFactory(
      final Supplier<ExternalDbConfiguration> configuration,
      final List<SegmentIdentifier> segments) {
    this(configuration, segments, DEFAULT_VERSION);
  }

  int getDefaultVersion() {
    return defaultVersion;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public KeyValueStorage create(
      final SegmentIdentifier segment,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    if (externalDbBlockingStub.get() == null) {
      final String target = configuration.get().getEndpoint();
      final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
      final ExternalDbBlockingStub externalDbBlockingStub = ExternalDbGrpc.newBlockingStub(channel);
      this.externalDbBlockingStub.set(externalDbBlockingStub);
    }

    final ExternalDBClientKeyValueStorage segmentedStorage =
        new ExternalDBClientKeyValueStorage(
            externalDbBlockingStub.get(), configuration.get(), metricsSystem);
    return new SegmentedKeyValueStorageAdapter<>(segment, segmentedStorage);
  }

  @Override
  public void close() {}

  @Override
  public boolean isSegmentIsolationSupported() {
    return true;
  }
}
