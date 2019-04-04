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
package tech.pegasys.pantheon.ethereum.eth.sync;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.DownloadHeaderSequenceTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.FutureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadHeadersStep<C>
    implements Function<CheckpointRange, CompletableFuture<CheckpointRangeHeaders>> {

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final ValidationPolicy validationPolicy;
  private final MetricsSystem metricsSystem;

  public DownloadHeadersStep(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final ValidationPolicy validationPolicy,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.validationPolicy = validationPolicy;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<CheckpointRangeHeaders> apply(final CheckpointRange checkpointRange) {
    final CompletableFuture<List<BlockHeader>> taskFuture = downloadHeaders(checkpointRange);
    final CompletableFuture<CheckpointRangeHeaders> processedFuture =
        taskFuture.thenApply(headers -> processHeaders(checkpointRange, headers));
    FutureUtils.propagateCancellation(processedFuture, taskFuture);
    return processedFuture;
  }

  private CompletableFuture<List<BlockHeader>> downloadHeaders(
      final CheckpointRange checkpointRange) {
    return DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            ethContext,
            checkpointRange.getEnd(),
            // -1 because we don't want to request the range starting header
            checkpointRange.getSegmentLength() - 1,
            validationPolicy,
            metricsSystem)
        .run();
  }

  private CheckpointRangeHeaders processHeaders(
      final CheckpointRange checkpointRange, final List<BlockHeader> headers) {
    final List<BlockHeader> headersToImport = new ArrayList<>(headers);
    headersToImport.add(checkpointRange.getEnd());
    return new CheckpointRangeHeaders(checkpointRange, headersToImport);
  }
}
