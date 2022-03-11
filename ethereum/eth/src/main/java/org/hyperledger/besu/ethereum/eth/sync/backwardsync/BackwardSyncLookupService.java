package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;

@ThreadSafe
public class BackwardSyncLookupService {
  public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final Logger LOG = getLogger(BackwardSyncLookupService.class);
  private static final int MAX_RETRIES = 100;

  @GuardedBy("this")
  private final Queue<Hash> hashes = new ArrayDeque<>();

  @GuardedBy("this")
  boolean running = false;

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private List<Block> results = new ArrayList<>();

  public BackwardSyncLookupService(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<List<Block>> lookup(final Hash newBlockhash) {
    synchronized (this) {
      hashes.add(newBlockhash);
      if (running) { //
        LOG.info(
            "some other future is already running and will process our hash {} when time comes...",
            newBlockhash.toHexString());
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      running = true;
    }
    return findBlocks();
  }

  private CompletableFuture<List<Block>> findBlocks() {

    CompletableFuture<List<Block>> f = tryToFindBlocks();
    for (int i = 0; i < MAX_RETRIES; i++) {
      f =
          f.thenApply(CompletableFuture::completedFuture)
              .exceptionally(
                  ex -> {
                    synchronized (this) {
                      if (!results.isEmpty()) {
                        List<Block> copy = new ArrayList<>(results);
                        results = new ArrayList<>();
                        return CompletableFuture.completedFuture(copy);
                      }
                    }
                    LOG.error("Failed to fetch blocks because {}. Waiting for few seconds ...", ex.getMessage());
                    wait(5000);
                    return tryToFindBlocks();
                  })
              .thenCompose(Function.identity());
    }
    return f.thenApply(this::rememberResults).thenCompose(this::possibleNextHash);
  }

  private CompletableFuture<List<Block>> possibleNextHash(final List<Block> blocks) {
    synchronized (this) {
      hashes.poll();
      if (hashes.isEmpty()) {
        results = new ArrayList<>();
        running = false;
        return CompletableFuture.completedFuture(blocks);
      }
    }
    return findBlocks();
  }

  private List<Block> rememberResults(final List<Block> blocks) {
    this.results.addAll(blocks);
    return results;
  }

  private synchronized Hash getNextHash() {
    return hashes.peek();
  }

  private CompletableFuture<List<Block>> tryToFindBlocks() {
    return CompletableFuture.supplyAsync(
            () ->
                GetHeadersFromPeerByHashTask.forSingleHash(
                        protocolSchedule, ethContext, getNextHash(), 0L, metricsSystem)
                    .run())
        .thenCompose(f -> f)
        .thenCompose(
            headers ->
                GetBodiesFromPeerTask.forHeaders(
                        protocolSchedule, ethContext, headers.getResult(), metricsSystem)
                    .run())
        .thenApply(AbstractPeerTask.PeerTaskResult::getResult);
  }

  private void wait(final int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
