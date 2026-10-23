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
package org.hyperledger.besu.ethereum.eth.sync.snap2.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.sync.common.WrongChainException;
import org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.PeerTaskRequest;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Priority;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads and stores the headers between a trusted header and the floor of the chain (genesis or
 * a checkpoint). Segments are fetched by number in parallel, then checked top down: each segment
 * must end where the segment above starts. A segment from another chain is fetched again by hash,
 * which can only return the right one.
 */
public final class HeaderBackfill {

  private static final Logger LOG = LoggerFactory.getLogger(HeaderBackfill.class);

  private final RequestSender sender;
  private final ProtocolSchedule protocolSchedule;
  private final Consumer<List<BlockHeader>> store;
  private final HeaderFetcher fetcher;
  private final BlockHeader floor;
  private final BlockHeader top;
  private final int headersPerRequest;
  private final int parallelism;
  private final MetricsSystem metricsSystem;
  private volatile Hash expected;
  private volatile long lowestStored;
  private volatile boolean stopped;
  private volatile Instant started = Instant.now();
  // guarded by this
  private Pipeline<Segment> pipeline;

  /**
   * Creates a backfill.
   *
   * @param store writes verified headers to the blockchain
   * @param floor the highest header below which nothing is needed, already stored and trusted
   * @param top the lowest trusted header to download down from, already stored
   */
  public HeaderBackfill(
      final RequestSender sender,
      final ProtocolSchedule protocolSchedule,
      final Consumer<List<BlockHeader>> store,
      final HeaderFetcher fetcher,
      final BlockHeader floor,
      final BlockHeader top,
      final int headersPerRequest,
      final int parallelism,
      final MetricsSystem metricsSystem) {
    this.sender = sender;
    this.protocolSchedule = protocolSchedule;
    this.store = store;
    this.fetcher = fetcher;
    this.floor = floor;
    this.top = top;
    this.headersPerRequest = headersPerRequest;
    this.parallelism = parallelism;
    this.metricsSystem = metricsSystem;
    this.expected = top.getParentHash();
    this.lowestStored = top.getNumber();
  }

  /** Returns the lowest header stored so far. */
  public long lowestStored() {
    return lowestStored;
  }

  /** Returns the number of headers stored so far. */
  public long done() {
    return top.getNumber() - lowestStored;
  }

  /** Returns the time since the download started. */
  public Duration elapsed() {
    return Duration.between(started, Instant.now());
  }

  /** Returns the number of headers to store, from below the top down to above the floor. */
  public long total() {
    return Math.max(0, top.getNumber() - floor.getNumber() - 1);
  }

  /** Downloads every header in between and checks the chain reaches the floor. */
  public CompletableFuture<Void> run(final ExecutorService executor) {
    started = Instant.now();
    final CompletableFuture<Void> done = start(executor);
    return done.thenRun(
        () -> {
          if (!expected.equals(floor.getHash())) {
            throw new WrongChainException(
                "the chain of the trusted head does not lead to block "
                    + floor.getNumber()
                    + " "
                    + floor.getHash()
                    + ", the consensus client follows another chain");
          }
          LOG.info(
              "SNAP2 headers complete in {} | down to {}",
              ProgressFormat.duration(Duration.between(started, Instant.now())),
              ProgressFormat.block(floor.getNumber() + 1));
        });
  }

  // under the lock, so that a stop comes first or finds the pipeline started
  private synchronized CompletableFuture<Void> start(final ExecutorService executor) {
    if (stopped) {
      return CompletableFuture.failedFuture(new CancellationException("header download stopped"));
    }
    if (top.getNumber() <= floor.getNumber() + 1) {
      return CompletableFuture.completedFuture(null);
    }
    LOG.info(
        "SNAP2 headers started | {} headers, {} down to {}",
        ProgressFormat.compact(total()),
        ProgressFormat.block(top.getNumber() - 1),
        ProgressFormat.block(floor.getNumber() + 1));
    pipeline =
        PipelineBuilder.createPipelineFrom(
                "headerSegments",
                new Segments(top.getNumber() - 1, floor.getNumber() + 1, headersPerRequest),
                parallelism,
                metricsSystem.createLabelledCounter(
                    BesuMetricCategory.SYNCHRONIZER,
                    "snap2_header_pipeline_processed_total",
                    "Header segments processed by the SNAP2 sync",
                    "step",
                    "action"),
                false,
                "snap2-headers")
            .thenProcessAsyncOrdered("downloadHeaders", this::download, parallelism)
            .andFinishWith("storeHeaders", this::verifyAndStore);
    return pipeline.start(executor);
  }

  /** Stops the download; no header is stored afterwards. */
  public synchronized void stop() {
    stopped = true;
    if (pipeline != null) {
      pipeline.abort();
    }
  }

  // best effort: headers of the segment by number, highest first, possibly partial
  private CompletableFuture<Fetched> download(final Segment segment) {
    return downloadFrom(segment, segment.high(), new ArrayList<>())
        .exceptionally(error -> new Fetched(segment, List.of()));
  }

  private CompletableFuture<Fetched> downloadFrom(
      final Segment segment, final long high, final List<BlockHeader> collected) {
    final int count = (int) (high - segment.low() + 1);
    return sender
        .submitWithRetries(
            new PeerTaskRequest<>(
                () ->
                    new GetHeadersFromPeerTask(
                        high, count, 0, Direction.REVERSE, 0, protocolSchedule),
                Priority.HEADERS,
                "headers"))
        .thenCompose(
            response -> {
              final List<BlockHeader> headers = response.value();
              if (!collected.isEmpty()
                  && !collected.getLast().getParentHash().equals(headers.getFirst().getHash())) {
                // parts from different chains, keep the first one and let the check sort it out
                return CompletableFuture.completedFuture(new Fetched(segment, collected));
              }
              for (final BlockHeader header : headers) {
                if (header.getNumber() < segment.low()) {
                  break;
                }
                collected.add(header);
              }
              final long next = collected.getLast().getNumber() - 1;
              return next < segment.low()
                  ? CompletableFuture.completedFuture(new Fetched(segment, collected))
                  : downloadFrom(segment, next, collected);
            });
  }

  // refetching runs outside the lock, which only keeps a stopped download from storing
  private void verifyAndStore(final Fetched fetched) {
    throwIfStopped();
    final long low = fetched.segment().low();
    List<BlockHeader> headers = fetched.headers();
    if (headers.isEmpty() || !headers.getFirst().getHash().equals(expected)) {
      LOG.debug(
          "Header segment ending at block {} is missing or on another chain, refetching it by hash",
          fetched.segment().high());
      headers = byHash(expected, low);
    } else if (headers.getLast().getNumber() > low) {
      final List<BlockHeader> complete = new ArrayList<>(headers);
      complete.addAll(byHash(headers.getLast().getParentHash(), low));
      headers = complete;
    }
    final List<BlockHeader> verified = headers;
    synchronized (this) {
      throwIfStopped();
      store.accept(verified);
    }
    expected = verified.getLast().getParentHash();
    lowestStored = verified.getLast().getNumber();
  }

  private void throwIfStopped() {
    if (stopped) {
      throw new IllegalStateException("header download stopped");
    }
  }

  // the trusted chain from the header with this hash down to low, highest first
  private List<BlockHeader> byHash(final Hash hash, final long low) {
    return fetcher
        .byHash(hash, Priority.HEADERS)
        .thenCompose(header -> fetcher.chainDown(header, low, Priority.HEADERS))
        .join()
        .reversed();
  }

  /**
   * Headers fetched for a segment.
   *
   * @param segment the segment
   * @param headers the headers, highest first
   */
  private record Fetched(Segment segment, List<BlockHeader> headers) {}

  /**
   * A segment of block numbers, both included.
   *
   * @param high the highest block
   * @param low the lowest block
   */
  private record Segment(long high, long low) {}

  private static final class Segments implements Iterator<Segment> {
    private final long low;
    private final int size;
    private long next;

    private Segments(final long high, final long low, final int size) {
      this.next = high;
      this.low = low;
      this.size = size;
    }

    @Override
    public boolean hasNext() {
      return next >= low;
    }

    @Override
    public Segment next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final Segment segment = new Segment(next, Math.max(low, next - size + 1));
      next = segment.low() - 1;
      return segment;
    }
  }
}
