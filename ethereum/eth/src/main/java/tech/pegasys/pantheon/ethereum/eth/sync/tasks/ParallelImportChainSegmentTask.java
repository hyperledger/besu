/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractEthTask;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockHandler;
import tech.pegasys.pantheon.ethereum.eth.sync.ValidationPolicy;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelImportChainSegmentTask<C, B> extends AbstractEthTask<List<Hash>> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;

  private final ArrayBlockingQueue<BlockHeader> checkpointHeaders;
  private final int maxActiveChunks;
  private final long firstHeaderNumber;
  private final long lastHeaderNumber;

  private final BlockHandler<B> blockHandler;
  private final ValidationPolicy validationPolicy;
  private final MetricsSystem metricsSystem;

  private ParallelImportChainSegmentTask(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final int maxActiveChunks,
      final List<BlockHeader> checkpointHeaders,
      final BlockHandler<B> blockHandler,
      final ValidationPolicy validationPolicy,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.ethContext = ethContext;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.maxActiveChunks = maxActiveChunks;
    this.metricsSystem = metricsSystem;

    if (checkpointHeaders.size() > 1) {
      this.firstHeaderNumber = checkpointHeaders.get(0).getNumber();
      this.lastHeaderNumber = checkpointHeaders.get(checkpointHeaders.size() - 1).getNumber();
    } else {
      this.firstHeaderNumber = -1;
      this.lastHeaderNumber = -1;
    }
    this.checkpointHeaders =
        new ArrayBlockingQueue<>(checkpointHeaders.size(), false, checkpointHeaders);
    this.blockHandler = blockHandler;
    this.validationPolicy = validationPolicy;
  }

  public static <C, B> ParallelImportChainSegmentTask<C, B> forCheckpoints(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final int maxActiveChunks,
      final BlockHandler<B> blockHandler,
      final ValidationPolicy validationPolicy,
      final List<BlockHeader> checkpointHeaders,
      final MetricsSystem metricsSystem) {
    return new ParallelImportChainSegmentTask<>(
        protocolSchedule,
        protocolContext,
        ethContext,
        maxActiveChunks,
        checkpointHeaders,
        blockHandler,
        validationPolicy,
        metricsSystem);
  }

  @Override
  protected void executeTask() {
    if (firstHeaderNumber >= 0) {
      LOG.debug("Importing chain segment from {} to {}.", firstHeaderNumber, lastHeaderNumber);

      // build pipeline
      final ParallelDownloadHeadersTask<C> downloadHeadersTask =
          new ParallelDownloadHeadersTask<>(
              checkpointHeaders,
              maxActiveChunks,
              protocolSchedule,
              protocolContext,
              ethContext,
              validationPolicy,
              metricsSystem);
      final ParallelValidateHeadersTask<C> validateHeadersTask =
          new ParallelValidateHeadersTask<>(
              validationPolicy,
              downloadHeadersTask.getOutboundQueue(),
              maxActiveChunks,
              protocolSchedule,
              protocolContext,
              metricsSystem);
      final ParallelDownloadBodiesTask<B> downloadBodiesTask =
          new ParallelDownloadBodiesTask<>(
              blockHandler, validateHeadersTask.getOutboundQueue(), maxActiveChunks, metricsSystem);
      final ParallelExtractTxSignaturesTask<B> extractTxSignaturesTask =
          new ParallelExtractTxSignaturesTask<>(
              blockHandler, downloadBodiesTask.getOutboundQueue(), maxActiveChunks, metricsSystem);
      final ParallelValidateAndImportBodiesTask<B> validateAndImportBodiesTask =
          new ParallelValidateAndImportBodiesTask<>(
              blockHandler,
              extractTxSignaturesTask.getOutboundQueue(),
              Integer.MAX_VALUE,
              metricsSystem);

      // Start the pipeline.
      final EthScheduler scheduler = ethContext.getScheduler();
      final CompletableFuture<?> downloadHeaderFuture =
          scheduler.scheduleServiceTask(downloadHeadersTask);
      registerSubTask(downloadHeaderFuture);
      final CompletableFuture<?> validateHeaderFuture =
          scheduler.scheduleServiceTask(validateHeadersTask);
      registerSubTask(validateHeaderFuture);
      final CompletableFuture<?> downloadBodiesFuture =
          scheduler.scheduleServiceTask(downloadBodiesTask);
      registerSubTask(downloadBodiesFuture);
      final CompletableFuture<?> extractTxSignaturesFuture =
          scheduler.scheduleServiceTask(extractTxSignaturesTask);
      registerSubTask(extractTxSignaturesFuture);
      final CompletableFuture<List<List<Hash>>> validateBodiesFuture =
          scheduler.scheduleServiceTask(validateAndImportBodiesTask);
      registerSubTask(validateBodiesFuture);

      // Hook in pipeline completion signaling.
      downloadHeadersTask.shutdown();
      downloadHeaderFuture.thenRun(validateHeadersTask::shutdown);
      validateHeaderFuture.thenRun(downloadBodiesTask::shutdown);
      downloadBodiesFuture.thenRun(extractTxSignaturesTask::shutdown);
      extractTxSignaturesFuture.thenRun(validateAndImportBodiesTask::shutdown);

      final BiConsumer<? super Object, ? super Throwable> cancelOnException =
          (s, e) -> {
            if (e != null && !(e instanceof CancellationException)) {
              downloadHeadersTask.cancel();
              validateHeadersTask.cancel();
              downloadBodiesTask.cancel();
              extractTxSignaturesTask.cancel();
              validateAndImportBodiesTask.cancel();
              result.get().completeExceptionally(e);
            }
          };

      downloadHeaderFuture.whenComplete(cancelOnException);
      validateHeaderFuture.whenComplete(cancelOnException);
      downloadBodiesFuture.whenComplete(cancelOnException);
      extractTxSignaturesFuture.whenComplete(cancelOnException);
      validateBodiesFuture.whenComplete(
          (r, e) -> {
            if (e != null) {
              cancelOnException.accept(null, e);
            } else if (r != null) {
              try {
                final List<Hash> importedBlocks =
                    validateBodiesFuture.get().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                result.get().complete(importedBlocks);
              } catch (final InterruptedException | ExecutionException ex) {
                result.get().completeExceptionally(ex);
              }
            }
          });

    } else {
      LOG.warn("Import task requested with no checkpoint headers.");
    }
  }
}
