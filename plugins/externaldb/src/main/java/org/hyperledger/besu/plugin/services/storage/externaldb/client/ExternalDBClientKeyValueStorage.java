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

import org.hyperledger.besu.ethereum.api.grpc.ExternalDbGrpc.ExternalDbBlockingStub;
import org.hyperledger.besu.ethereum.api.grpc.GetRequest;
import org.hyperledger.besu.ethereum.api.grpc.GetRequest.Segment;
import org.hyperledger.besu.ethereum.api.grpc.GetResponse;
import org.hyperledger.besu.ethereum.api.grpc.SegmentMapper;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.externaldb.configuration.ExternalDbConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.protobuf.ByteString;

public class ExternalDBClientKeyValueStorage implements SegmentedKeyValueStorage<Segment> {

  private final OperationTimer readLatency;
  private final ExternalDbBlockingStub externalDbBlockingStub;
  private final SegmentMapper segmentMapper = new SegmentMapper();

  public ExternalDBClientKeyValueStorage(
      final ExternalDbBlockingStub externalDbBlockingStub,
      final ExternalDbConfiguration configuration,
      final MetricsSystem metricsSystem) {
    this.readLatency =
        metricsSystem
            .createLabelledTimer(
                BesuMetricCategory.KVSTORE_ROCKSDB,
                "read_latency_seconds",
                "Latency for read from RocksDB.",
                "database")
            .labels("blockchain");
    this.externalDbBlockingStub = externalDbBlockingStub;
  }

  @Override
  public Segment getSegmentIdentifierByName(final SegmentIdentifier segment) {
    return segmentMapper.getGrpcSegment(segment);
  }

  @Override
  public Optional<byte[]> get(final Segment segment, final byte[] key) throws StorageException {
    try (final OperationTimer.TimingContext ignored = readLatency.startTimer()) {
      final GetRequest request =
          GetRequest.newBuilder().setKey(ByteString.copyFrom(key)).setSegment(segment).build();
      final GetResponse response = externalDbBlockingStub.getValue(request);
      if (response.hasValue()) {
        return Optional.of(response.getValue().toByteArray());
      } else {
        return Optional.empty();
      }
    }
  }

  @Override
  public Transaction<Segment> startTransaction() throws StorageException {
    return new ExternalDBClientTransaction();
  }

  @Override
  public Stream<byte[]> streamKeys(final Segment segmentHandle) {
    return Stream.empty();
  }

  @Override
  public boolean tryDelete(final Segment segmentHandle, final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final Segment segmentHandle, final Predicate<byte[]> returnCondition) {
    return Set.of();
  }

  @Override
  public void clear(final Segment segmentHandle) {}

  @Override
  public void close() throws IOException {}
}
