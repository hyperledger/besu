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

import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.block;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.compact;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.duration;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.eta;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.mibPerSecond;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.ProgressFormat.percent;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerSelector;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncBlockBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.snap2.chain.BlockRanges.Gap;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.SimpleNoCopyRlpEncoder;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongUnaryOperator;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads the bodies and receipts below a stored block, highest first, each request to a
 * different peer, and records the lowest block stored with every one above it. The sync runs it on
 * the window below its pivot; after the sync it runs in the background, paused while the node is
 * behind the chain head.
 */
public final class BodyBackfill {

  private static final Logger LOG = LoggerFactory.getLogger(BodyBackfill.class);
  private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
  private static final long RECORD_BYTES = 64L * 1024 * 1024;
  // peers cap a body or receipt response around 2 MiB
  private static final double RESPONSE_BYTES = 2 * 1024 * 1024;
  private static final int MIN_RANGE_BLOCKS = 8;
  private static final int MAX_RANGE_BLOCKS = 128;
  private static final double INITIAL_BLOCK_BYTES = 64 * 1024;

  /** What the back-fill is doing. */
  private enum State {
    RUNNING,
    PAUSED,
    DONE
  }

  /**
   * Tuning of the back-fill.
   *
   * @param label what the settings are for, in the logs
   * @param requests requests in flight at most, each to a different peer
   * @param recordBytes bytes stored between two records of the lowest block
   * @param pollInterval how long a paused back-fill, or one waiting for its last ranges, waits
   * @param retryDelay how long a range whose download failed waits before it is asked again
   * @param maxRangeBlocks most blocks per request
   * @param indexTransactions whether the transactions of the stored blocks are indexed
   * @param background whether it runs after the sync, logging its progress and with metrics
   */
  public record Settings(
      String label,
      int requests,
      long recordBytes,
      Duration pollInterval,
      Duration retryDelay,
      int maxRangeBlocks,
      boolean indexTransactions,
      boolean background) {

    /** Returns the settings of the back-fill that runs once the node follows the chain head. */
    public static Settings background(final boolean indexTransactions) {
      return new Settings(
          "back-fill",
          8,
          RECORD_BYTES,
          POLL_INTERVAL,
          RETRY_DELAY,
          MAX_RANGE_BLOCKS,
          indexTransactions,
          true);
    }

    /** Returns the settings of the window the sync downloads before it sets the chain head. */
    public static Settings window(final boolean indexTransactions) {
      return new Settings(
          "window",
          4,
          RECORD_BYTES,
          POLL_INTERVAL,
          RETRY_DELAY,
          MAX_RANGE_BLOCKS,
          indexTransactions,
          false);
    }
  }

  private final MutableBlockchain blockchain;
  private final ProtocolSchedule protocolSchedule;
  private final PeerSelector peers;
  private final PeerTaskExecutor peerTaskExecutor;
  private final BooleanSupplier inSync;
  // the lowest block whose body is needed, given the chain head
  private final LongUnaryOperator lowestNeeded;
  private final Settings settings;
  private final SyncTransactionReceiptEncoder receiptEncoder =
      new SyncTransactionReceiptEncoder(new SimpleNoCopyRlpEncoder());
  private final CompletableFuture<Void> done = new CompletableFuture<>();
  private final AtomicLong blocksStored = new AtomicLong();
  private final AtomicLong bytesStored = new AtomicLong();
  private final AtomicLong lastLogNanos = new AtomicLong();
  // the lowest block at the start, which the share done counts from
  private final long initialLowest;
  // guarded by itself: the peers with a request in flight
  private final Set<EthPeer> busyPeers = new HashSet<>();
  private final Object sizes = new Object();

  // guarded by this
  private final BlockRanges stored = new BlockRanges();
  private final BlockRanges fetching = new BlockRanges();
  // the lowest block stored with every block above it, as last recorded
  private long persisted;
  private long bytesSinceRecord;
  private ExecutorService workers;
  // guarded by sizes: the averages of the stored blocks, without and with their receipts
  private double bodyBytes = INITIAL_BLOCK_BYTES;
  private double blockBytes = INITIAL_BLOCK_BYTES;

  private volatile long lowest;
  private volatile boolean stopped;
  private volatile State state = State.PAUSED;
  private volatile String pauseReason = "";
  private volatile Instant started = Instant.now();
  private volatile long loggedBytes;

  /**
   * Creates the back-fill.
   *
   * @param inSync whether the node follows the chain head, the back-fill pausing otherwise
   * @param lowest the lowest block whose body and receipts are stored already, the download
   *     starting below it
   * @param lowestNeeded the lowest block whose body is needed for a chain head, above the floor of
   *     the sync and, on a pruned chain, {@link
   *     org.hyperledger.besu.ethereum.chain.ChainDataPruner#lowestToBackfill}
   */
  public BodyBackfill(
      final MutableBlockchain blockchain,
      final ProtocolSchedule protocolSchedule,
      final PeerSelector peers,
      final PeerTaskExecutor peerTaskExecutor,
      final BooleanSupplier inSync,
      final long lowest,
      final LongUnaryOperator lowestNeeded,
      final Settings settings,
      final MetricsSystem metricsSystem) {
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
    this.peers = peers;
    this.peerTaskExecutor = peerTaskExecutor;
    this.inSync = inSync;
    this.lowest = lowest;
    this.initialLowest = lowest;
    this.persisted = lowest;
    this.lowestNeeded = lowestNeeded;
    this.settings = settings;
    // the window shows in the progress of the sync
    if (settings.background()) {
      registerMetrics(metricsSystem);
    }
  }

  /**
   * Starts downloading below the lowest stored block, unless every needed body is stored.
   *
   * @param workers runs one request at a time on each of its threads, shut down once the back-fill
   *     ends
   */
  public void start(final ExecutorService workers) {
    synchronized (this) {
      this.workers = workers;
    }
    final long floor = lowestNeeded.applyAsLong(blockchain.getChainHeadBlockNumber());
    if (lowest <= floor) {
      LOG.info(
          "SNAP2 {}: every block from {} has its body and receipts",
          settings.label(),
          block(lowest));
      finish();
      return;
    }
    started = Instant.now();
    LOG.info(
        "SNAP2 {} started | {} blocks, {} down to {}",
        settings.label(),
        compact(lowest - floor),
        block(lowest - 1),
        block(floor));
    lastLogNanos.set(System.nanoTime());
    for (int i = 0; i < settings.requests(); i++) {
      workers.execute(this::run);
    }
  }

  /** Stops the back-fill, recording what it stored; nothing is written afterwards. */
  public void stop() {
    final ExecutorService running;
    synchronized (this) {
      if (stopped) {
        return;
      }
      stopped = true;
      running = workers;
    }
    if (running != null) {
      running.shutdownNow();
    }
    synchronized (this) {
      try {
        persist();
      } catch (final RuntimeException e) {
        LOG.debug("Could not record the back-filled blocks on stop: {}", e.getMessage());
      }
    }
    done.completeExceptionally(new CancellationException("body download stopped"));
  }

  /** Completes once every needed body is stored and recorded, or fails once stopped. */
  public CompletableFuture<Void> done() {
    return done;
  }

  /**
   * Returns the lowest block whose body and receipts are stored, with those of every block above.
   */
  public long lowestStored() {
    return lowest;
  }

  /** Returns the number of blocks whose body and receipts are still to store. */
  public long blocksRemaining() {
    return Math.max(0, lowest - lowestNeeded.applyAsLong(blockchain.getChainHeadBlockNumber()));
  }

  /** Returns the number of blocks whose body and receipts were stored. */
  public long blocksStored() {
    return blocksStored.get();
  }

  private void run() {
    while (!stopped) {
      try {
        step();
      } catch (final InterruptedException e) {
        return;
      } catch (final RuntimeException e) {
        LOG.debug("Body back-fill step failed, retrying: {}", e.getMessage());
        try {
          Thread.sleep(settings.retryDelay().toMillis());
        } catch (final InterruptedException interrupted) {
          return;
        }
      }
    }
  }

  private void step() throws InterruptedException {
    if (settings.background()) {
      maybeLog();
    }
    if (!inSync.getAsBoolean()) {
      pauseReason = "node behind the head";
      state = State.PAUSED;
      Thread.sleep(settings.pollInterval().toMillis());
      return;
    }
    final Optional<Gap> range = nextRange();
    if (range.isEmpty()) {
      if (idle()) {
        finish();
      } else {
        Thread.sleep(settings.pollInterval().toMillis());
      }
      return;
    }
    state = State.RUNNING;
    List<SyncBlockWithReceipts> blocks = null;
    try {
      blocks = download(range.get());
    } finally {
      if (blocks == null) {
        release(range.get());
      }
    }
    if (blocks == null) {
      Thread.sleep(settings.retryDelay().toMillis());
      return;
    }
    recordSizes(blocks);
    store(range.get(), blocks);
  }

  // the highest run of blocks below the lowest stored one that is neither stored nor in flight
  private synchronized Optional<Gap> nextRange() {
    if (stopped) {
      return Optional.empty();
    }
    final long floor = lowestNeeded.applyAsLong(blockchain.getChainHeadBlockNumber());
    final Optional<Gap> range =
        BlockRanges.highestGap(stored, fetching, floor, lowest - 1, rangeBlocks());
    range.ifPresent(gap -> fetching.add(gap.from(), gap.to()));
    return range;
  }

  private synchronized boolean idle() {
    return fetching.isEmpty();
  }

  private synchronized void release(final Gap range) {
    fetching.remove(range.from(), range.to());
  }

  // records the average sizes of the downloaded blocks, which size the next requests
  private void recordSizes(final List<SyncBlockWithReceipts> blocks) {
    final long bodies =
        blocks.stream().mapToLong(block -> block.getBlock().getBody().getRlp().size()).sum();
    final long total = bytes(blocks);
    synchronized (sizes) {
      bodyBytes = 0.8 * bodyBytes + 0.2 * bodies / blocks.size();
      blockBytes = 0.8 * blockBytes + 0.2 * total / blocks.size();
    }
  }

  private static long bytes(final List<SyncBlockWithReceipts> blocks) {
    long bytes = 0;
    for (final SyncBlockWithReceipts block : blocks) {
      bytes += block.getBlock().getBody().getRlp().size();
      for (final SyncTransactionReceipt receipt : block.getReceipts()) {
        bytes += receipt.getRlpBytes() == null ? 0 : receipt.getRlpBytes().size();
      }
    }
    return bytes;
  }

  // blocks whose bodies fill about one response, so many when they are small
  private int rangeBlocks() {
    final int max = settings.maxRangeBlocks();
    synchronized (sizes) {
      return (int)
          Math.max(Math.min(MIN_RANGE_BLOCKS, max), Math.min(max, RESPONSE_BYTES / bodyBytes));
    }
  }

  // blocks whose receipts fill about one response
  private int receiptBlocks() {
    final int max = settings.maxRangeBlocks();
    synchronized (sizes) {
      final double receiptBytes = blockBytes - bodyBytes;
      return (int)
          Math.max(1, receiptBytes > 0 ? Math.min(max, RESPONSE_BYTES / receiptBytes) : max);
    }
  }

  // a range whose headers a reorg replaced, or that a pruner would remove, is dropped
  private synchronized void store(final Gap range, final List<SyncBlockWithReceipts> blocks) {
    fetching.remove(range.from(), range.to());
    if (stopped) {
      return;
    }
    final long floor = lowestNeeded.applyAsLong(blockchain.getChainHeadBlockNumber());
    final List<SyncBlockWithReceipts> kept =
        blocks.stream().filter(block -> block.getNumber() >= floor).toList();
    if (kept.isEmpty() || !canonical(kept)) {
      return;
    }
    blockchain.unsafeStoreSyncBodiesAndReceipts(kept, settings.indexTransactions());
    stored.add(kept.getFirst().getNumber(), range.to());
    final long bytes = bytes(kept);
    blocksStored.addAndGet(kept.size());
    bytesStored.addAndGet(bytes);
    bytesSinceRecord += bytes;
    // every block from the start of the stored run below the lowest block is stored
    final long from = stored.startOf(lowest - 1);
    if (from >= 0) {
      stored.remove(from, lowest - 1);
      lowest = from;
    }
    if (bytesSinceRecord >= settings.recordBytes()) {
      persist();
    }
  }

  // records the lowest block stored with every block above it; holders of the lock only
  private void persist() {
    if (lowest < persisted) {
      blockchain.unsafeSetBodiesFrom(lowest);
      persisted = lowest;
    }
    bytesSinceRecord = 0;
  }

  private void finish() {
    final ExecutorService running;
    synchronized (this) {
      if (stopped) {
        return;
      }
      stopped = true;
      persist();
      running = workers;
    }
    state = State.DONE;
    if (blocksStored.get() > 0) {
      LOG.info(
          "SNAP2 {} complete in {} | {} blocks, down to {}",
          settings.label(),
          duration(Duration.between(started, Instant.now())),
          compact(blocksStored.get()),
          block(lowest));
    }
    running.shutdown();
    done.complete(null);
  }

  private List<SyncBlockWithReceipts> download(final Gap range) {
    final List<BlockHeader> headers = headers(range.from(), range.to());
    if (headers == null) {
      return null;
    }
    final List<SyncBlock> bodies = bodies(headers);
    return bodies == null ? null : receipts(bodies);
  }

  // null when a header is missing, which the sync never leaves behind above its floor
  private List<BlockHeader> headers(final long from, final long to) {
    final List<BlockHeader> headers = new ArrayList<>((int) (to - from + 1));
    for (long number = from; number <= to; number++) {
      final Optional<BlockHeader> header = blockchain.getBlockHeader(number);
      if (header.isEmpty()) {
        LOG.warn("No header stored for block {}, its body cannot be back-filled", number);
        return null;
      }
      headers.add(header.get());
    }
    return headers;
  }

  // null when peers do not answer, the range being asked again later
  private List<SyncBlock> bodies(final List<BlockHeader> headers) {
    final List<SyncBlock> blocks = new ArrayList<>(headers.size());
    while (blocks.size() < headers.size()) {
      if (stopped) {
        return null;
      }
      final List<BlockHeader> remaining =
          List.copyOf(headers.subList(blocks.size(), headers.size()));
      final PeerTaskExecutorResult<List<SyncBlock>> result =
          request(
              new GetSyncBlockBodiesFromPeerTask(remaining, protocolSchedule, 0) {
                @Override
                public int getRetriesWithSamePeer() {
                  return 0;
                }
              });
      if (result.responseCode() != PeerTaskExecutorResponseCode.SUCCESS) {
        return null;
      }
      blocks.addAll(result.result().orElseThrow());
    }
    return blocks;
  }

  private List<SyncBlockWithReceipts> receipts(final List<SyncBlock> blocks) {
    final Map<Hash, List<SyncTransactionReceipt>> receipts = new HashMap<>();
    final List<SyncBlock> needed = new ArrayList<>();
    for (final SyncBlock block : blocks) {
      if (block.getHeader().getReceiptsRoot().equals(Hash.EMPTY_TRIE_HASH)) {
        receipts.put(block.getHash(), List.of());
      } else {
        needed.add(block);
      }
    }
    // about one response per group, a block cut by the peer completed with the next request
    for (final List<SyncBlock> group : Lists.partition(needed, receiptBlocks())) {
      List<SyncBlock> remaining = group;
      List<SyncTransactionReceipt> partial = List.of();
      while (!remaining.isEmpty()) {
        if (stopped) {
          return null;
        }
        final PeerTaskExecutorResult<GetSyncReceiptsFromPeerTask.Response> result =
            request(
                new GetSyncReceiptsFromPeerTask(
                    new GetSyncReceiptsFromPeerTask.Request(List.copyOf(remaining), partial),
                    protocolSchedule,
                    receiptEncoder) {
                  @Override
                  public int getRetriesWithSamePeer() {
                    return 0;
                  }
                });
        if (result.responseCode() != PeerTaskExecutorResponseCode.SUCCESS) {
          return null;
        }
        final GetSyncReceiptsFromPeerTask.Response response = result.result().orElseThrow();
        response
            .completeReceiptsByBlock()
            .forEach((block, blockReceipts) -> receipts.put(block.getHash(), blockReceipts));
        remaining =
            remaining.stream().filter(block -> !receipts.containsKey(block.getHash())).toList();
        partial = response.lastBlockPartialReceipts();
      }
    }
    return blocks.stream()
        .map(block -> new SyncBlockWithReceipts(block, receipts.get(block.getHash())))
        .toList();
  }

  // one request at a time per peer, so that the back-fill takes a small share of each
  private <T> PeerTaskExecutorResult<T> request(final PeerTask<T> task) {
    final Optional<EthPeer> peer;
    synchronized (busyPeers) {
      peer =
          peers.getPeer(
              task.getPeerRequirementFilter()
                  .and(candidate -> !busyPeers.contains(candidate.ethPeer())));
      peer.ifPresent(busyPeers::add);
    }
    if (peer.isEmpty()) {
      return new PeerTaskExecutorResult<>(
          Optional.empty(), PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE, List.of());
    }
    try {
      return peerTaskExecutor.executeAgainstPeer(task, peer.get());
    } finally {
      synchronized (busyPeers) {
        busyPeers.remove(peer.get());
      }
    }
  }

  // linked end to end and still on the canonical chain
  private boolean canonical(final List<SyncBlockWithReceipts> blocks) {
    for (int i = 1; i < blocks.size(); i++) {
      if (!blocks.get(i).getHeader().getParentHash().equals(blocks.get(i - 1).getHash())) {
        return false;
      }
    }
    return canonical(blocks.getFirst().getHeader()) && canonical(blocks.getLast().getHeader());
  }

  private boolean canonical(final BlockHeader header) {
    return blockchain
        .getBlockHashByNumber(header.getNumber())
        .map(header.getHash()::equals)
        .orElse(false);
  }

  private void maybeLog() {
    final long now = System.nanoTime();
    final long last = lastLogNanos.get();
    if (now - last < LOG_INTERVAL.toNanos() || !lastLogNanos.compareAndSet(last, now)) {
      return;
    }
    final double seconds = (now - last) / 1e9;
    final long bytes = bytesStored.get();
    final long floor = lowestNeeded.applyAsLong(blockchain.getChainHeadBlockNumber());
    final long downloaded = initialLowest - lowest;
    final StringBuilder line =
        new StringBuilder("SNAP2 bodies ").append(percent(downloaded, initialLowest - floor));
    eta((double) downloaded, (double) blocksRemaining(), Duration.between(started, Instant.now()))
        .ifPresent(left -> line.append(" | ETA ").append(left));
    line.append(" | ")
        .append(settings.label())
        .append(' ')
        .append(mibPerSecond((bytes - loggedBytes) / seconds))
        .append(" | ")
        .append(
            state == State.PAUSED
                ? "paused: " + pauseReason
                : state.name().toLowerCase(Locale.ROOT));
    LOG.info("{}", line);
    loggedBytes = bytes;
  }

  private void registerMetrics(final MetricsSystem metricsSystem) {
    final BesuMetricCategory category = BesuMetricCategory.SYNCHRONIZER;
    metricsSystem.createLongGauge(
        category,
        "snap2_backfill_lowest_block",
        "Lowest block whose body and receipts the SNAP2 back-fill stored, with every block above it",
        this::lowestStored);
    metricsSystem.createLongGauge(
        category,
        "snap2_backfill_blocks_remaining",
        "Blocks whose body and receipts the SNAP2 back-fill still has to store",
        this::blocksRemaining);
    metricsSystem.createCounter(
        category,
        "snap2_backfill_bytes_total",
        "Bytes of bodies and receipts the SNAP2 back-fill stored",
        bytesStored::get);
    final LabelledSuppliedMetric states =
        metricsSystem.createLabelledSuppliedGauge(
            category,
            "snap2_backfill_state",
            "State of the SNAP2 back-fill, 1 for the current one",
            "state");
    for (final State value : State.values()) {
      states.labels(() -> state == value ? 1 : 0, value.name().toLowerCase(Locale.ROOT));
    }
  }
}
