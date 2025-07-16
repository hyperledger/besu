/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.FullImportBlockStep;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class Era1ImportPrepipelineFactory implements FileImportPipelineFactory {

  private final MetricsSystem metricsSystem;
  private final URI era1DataUri;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final SyncTerminationCondition fullSyncTerminationCondition;

  public Era1ImportPrepipelineFactory(
      final MetricsSystem metricsSystem,
      final URI era1DataUri,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncTerminationCondition syncTerminationCondition) {
    this.metricsSystem = metricsSystem;
    this.era1DataUri = era1DataUri;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.fullSyncTerminationCondition = syncTerminationCondition;
  }

  @Override
  public Pipeline<URI> createFileImportPipelineForCurrentBlockNumber(
      final long currentHeadBlockNumber) {
    URI dataUri =
        era1DataUri.getScheme() == null
            ? URI.create("file://" + era1DataUri.getPath())
            : era1DataUri;

    final String inputSourceName = "ERA1 File Source";
    final Iterator<URI> era1UriSource =
        switch (dataUri.getScheme()) {
          case "file" -> new Era1FileSource(dataUri, currentHeadBlockNumber);
          case "http", "https" -> new Era1HttpFileSource(dataUri, currentHeadBlockNumber);
          default ->
              throw new IllegalStateException("Unexpected URI scheme: " + era1DataUri.getScheme());
        };
    final int bufferSize = Runtime.getRuntime().availableProcessors();
    final LabelledMetric<Counter> processedTotalMetric =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "era1_file_import_prepipeline_processed_total",
            "Number of entries process by each pipeline stage",
            "step",
            "action");
    final boolean tracingEnabled = true;
    final String pipelineName = "ERA1 File Import Prepipeline";

    final Function<URI, CompletableFuture<List<Block>>> era1FileReader =
        new Era1FileReader(ScheduleBasedBlockHeaderFunctions.create(protocolSchedule));
    final Function<List<Block>, Stream<Block>> flatMapBlockListsFunction =
        (blockList) -> blockList.stream().filter((b) -> b.getHeader().getNumber() != 0);
    final Consumer<Block> importBlockFunction =
        new FullImportBlockStep(
            protocolSchedule, protocolContext, ethContext, fullSyncTerminationCondition);

    return PipelineBuilder.createPipelineFrom(
            inputSourceName,
            era1UriSource,
            bufferSize,
            processedTotalMetric,
            tracingEnabled,
            pipelineName)
        .thenProcessAsyncOrdered("ERA1 File Reader", era1FileReader, bufferSize)
        .thenFlatMap(
            "Flat Map Block Lists and Filter Block 0", flatMapBlockListsFunction, bufferSize)
        .andFinishWith("Import ERA1 Block", importBlockFunction);
  }
}
